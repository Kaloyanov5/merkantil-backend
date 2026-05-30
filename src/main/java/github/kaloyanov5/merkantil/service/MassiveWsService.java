package github.kaloyanov5.merkantil.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.kaloyanov5.merkantil.entity.Order;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.OrderRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.util.MoneyUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Persistent WebSocket connection to Massive's stocks stream. Subscribes to the
 * per-second aggregate ("A") feed for the project's tracked symbols and applies
 * each event to the corresponding Stock row, broadcasts to the SPA on
 * /topic/prices, and triggers limit-order matching — i.e. the same things
 * {@link StockPriceScheduler} does, but event-driven instead of every-5s polled.
 *
 * <p>{@link StockPriceScheduler#updateAllStockPrices()} consults
 * {@link #isHealthy()} and skips its REST tick when the WS is alive and recent,
 * so the two paths don't double-update. When the WS is down or stale the
 * scheduler takes over again — also the path used outside market hours, when
 * Massive emits few or no A events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MassiveWsService {

    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MarketSessionService marketSessionService;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    @Value("${massive.ws.enabled:true}")
    private boolean enabled;

    @Value("${massive.ws.url}")
    private String wsUrl;

    @Value("${massive.api.key}")
    private String apiKey;

    @Value("${massive.ws.stale-threshold-seconds:60}")
    private long staleThresholdSeconds;

    @Value("${massive.ws.reconnect.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${massive.ws.reconnect.max-backoff-ms:60000}")
    private long maxBackoffMs;

    private enum State { DISCONNECTED, CONNECTING, AUTHENTICATING, SUBSCRIBED }

    private final AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private final AtomicLong lastEventAtMillis = new AtomicLong(0);
    private final AtomicLong currentBackoffMs = new AtomicLong();
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final Set<String> appliedSymbolsThisSession = ConcurrentHashMap.newKeySet();
    private final StringBuilder textBuffer = new StringBuilder();

    private HttpClient httpClient;
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("Massive WS disabled via config; REST scheduler will handle price updates");
            return;
        }
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "massive-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
        currentBackoffMs.set(initialBackoffMs);
        connect();
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        WebSocket ws = webSocket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
                // Best-effort close on shutdown.
            }
        }
    }

    /**
     * Healthy = SUBSCRIBED state AND we've received at least one event within
     * the stale-threshold window. The scheduler uses this to decide whether to
     * skip its REST tick.
     */
    public boolean isHealthy() {
        if (!enabled || state.get() != State.SUBSCRIBED) {
            return false;
        }
        long last = lastEventAtMillis.get();
        if (last == 0) {
            return false;
        }
        return System.currentTimeMillis() - last <= staleThresholdSeconds * 1000;
    }

    /**
     * Re-syncs the subscription list against the current set of active stocks
     * in the DB. Caller should invoke after admin add/remove operations.
     */
    public void refreshSubscriptions() {
        if (state.get() != State.SUBSCRIBED) {
            return; // will be picked up on next subscribe pass after reconnect
        }
        applyDesiredSubscriptions(loadDesiredSymbols());
    }

    private void connect() {
        if (!state.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
            return;
        }
        log.info("Massive WS: connecting to {}", wsUrl);
        try {
            httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new Listener())
                    .whenComplete((ws, ex) -> {
                        if (ex != null) {
                            log.warn("Massive WS connect failed: {}", ex.getMessage());
                            state.set(State.DISCONNECTED);
                            scheduleReconnect();
                        } else {
                            webSocket.set(ws);
                            // Server sends {ev:status,status:connected} first; we auth on receipt.
                        }
                    });
        } catch (Exception e) {
            log.warn("Massive WS connect threw: {}", e.getMessage());
            state.set(State.DISCONNECTED);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        long delay = currentBackoffMs.get();
        long next = Math.min(delay * 2, maxBackoffMs);
        currentBackoffMs.set(next);
        log.info("Massive WS: reconnecting in {}ms", delay);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void sendAuth(WebSocket ws) {
        state.set(State.AUTHENTICATING);
        String payload = "{\"action\":\"auth\",\"params\":\"" + apiKey + "\"}";
        ws.sendText(payload, true);
    }

    private void sendInitialSubscribe(WebSocket ws) {
        Set<String> desired = loadDesiredSymbols();
        if (desired.isEmpty()) {
            log.info("Massive WS: no active stocks to subscribe to");
            state.set(State.SUBSCRIBED);
            return;
        }
        String params = desired.stream().map(s -> "A." + s).collect(Collectors.joining(","));
        log.info("Massive WS: subscribing to {} symbols", desired.size());
        ws.sendText("{\"action\":\"subscribe\",\"params\":\"" + params + "\"}", true);
        subscribedSymbols.addAll(desired);
        state.set(State.SUBSCRIBED);
        // Reset backoff after a successful end-to-end connect.
        currentBackoffMs.set(initialBackoffMs);
    }

    private void applyDesiredSubscriptions(Set<String> desired) {
        WebSocket ws = webSocket.get();
        if (ws == null) return;

        Set<String> toAdd = new HashSet<>(desired);
        toAdd.removeAll(subscribedSymbols);
        Set<String> toRemove = new HashSet<>(subscribedSymbols);
        toRemove.removeAll(desired);

        if (!toAdd.isEmpty()) {
            String params = toAdd.stream().map(s -> "A." + s).collect(Collectors.joining(","));
            ws.sendText("{\"action\":\"subscribe\",\"params\":\"" + params + "\"}", true);
            subscribedSymbols.addAll(toAdd);
            log.info("Massive WS: subscribed to {} new symbols", toAdd.size());
        }
        if (!toRemove.isEmpty()) {
            String params = toRemove.stream().map(s -> "A." + s).collect(Collectors.joining(","));
            ws.sendText("{\"action\":\"unsubscribe\",\"params\":\"" + params + "\"}", true);
            subscribedSymbols.removeAll(toRemove);
            log.info("Massive WS: unsubscribed from {} symbols", toRemove.size());
        }
    }

    private Set<String> loadDesiredSymbols() {
        return stockRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .map(Stock::getSymbol)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void handleFrame(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            if (!root.isArray()) {
                return;
            }
            for (JsonNode event : root) {
                String ev = event.path("ev").asText("");
                switch (ev) {
                    case "status" -> handleStatus(event);
                    case "A" -> handleAggregate(event);
                    default -> { /* ignore unknown event types */ }
                }
            }
        } catch (Exception e) {
            log.warn("Massive WS: failed to parse frame: {}", e.getMessage());
        }
    }

    private void handleStatus(JsonNode event) {
        String status = event.path("status").asText("");
        String message = event.path("message").asText("");
        log.info("Massive WS status: {} ({})", status, message);
        WebSocket ws = webSocket.get();
        if (ws == null) return;
        switch (status) {
            case "connected" -> sendAuth(ws);
            case "auth_success" -> sendInitialSubscribe(ws);
            case "auth_failed" -> {
                log.error("Massive WS auth failed: {} — disabling reconnects (check API key)", message);
                // Don't reconnect on bad-key; nothing we can do without a config change.
                state.set(State.DISCONNECTED);
            }
            case "success" -> { /* subscribe/unsubscribe ack */ }
            default -> { /* ignore */ }
        }
    }

    private void handleAggregate(JsonNode event) {
        String symbol = event.path("sym").asText(null);
        if (symbol == null) return;

        Double close = nullableDouble(event, "c");
        Double high = nullableDouble(event, "h");
        Double low = nullableDouble(event, "l");
        Long accumVolume = event.has("av") && !event.get("av").isNull() ? event.get("av").asLong() : null;

        try {
            boolean applied = applyPriceUpdate(symbol, close, high, low, accumVolume);
            if (applied) {
                // Only mark "we saw a usable event" after a real DB+broadcast,
                // so isHealthy() doesn't lie when events arrive but the handler
                // silently no-ops (session=CLOSED, c<=0, stock missing, etc.) —
                // otherwise the scheduler skips its REST fallback while nothing
                // is actually being persisted.
                lastEventAtMillis.set(System.currentTimeMillis());
                if (appliedSymbolsThisSession.add(symbol)) {
                    log.info("Massive WS: first applied price for {} (c={})", symbol, close);
                }
            }
        } catch (Exception e) {
            log.warn("Massive WS: failed to apply event for {}: {}", symbol, e.getMessage());
        }
    }

    private Double nullableDouble(JsonNode event, String field) {
        JsonNode node = event.get(field);
        if (node == null || node.isNull()) return null;
        double v = node.asDouble();
        return v > 0 ? v : null;
    }

    @Transactional
    protected boolean applyPriceUpdate(String symbol, Double close, Double high, Double low, Long accumVolume) {
        Stock stock = stockRepository.findBySymbol(symbol).orElse(null);
        if (stock == null || !Boolean.TRUE.equals(stock.getIsActive())) {
            return false;
        }

        String session = marketSessionService.getCurrentSession();
        boolean updated = false;

        if ("OPEN".equals(session)) {
            if (close != null) {
                stock.setCurrentPrice(MoneyUtil.of(close));
                stock.setExtendedHoursPrice(null);
                updated = true;
            }
            if (high != null) {
                BigDecimal newHigh = MoneyUtil.of(high);
                if (stock.getDayHigh() == null || newHigh.compareTo(stock.getDayHigh()) > 0) {
                    stock.setDayHigh(newHigh);
                }
            }
            if (low != null) {
                BigDecimal newLow = MoneyUtil.of(low);
                if (stock.getDayLow() == null || newLow.compareTo(stock.getDayLow()) < 0) {
                    stock.setDayLow(newLow);
                }
            }
            if (accumVolume != null) {
                stock.setVolume(accumVolume);
            }
        } else if ("PRE_MARKET".equals(session) || "AFTER_HOURS".equals(session)) {
            if (close != null) {
                stock.setExtendedHoursPrice(MoneyUtil.of(close));
                updated = true;
            }
        } else {
            // CLOSED / HOLIDAY — ignore. Massive shouldn't be emitting anyway.
            return false;
        }

        if (!updated) return false;

        stock.setLastUpdated(LocalDateTime.now());
        stockRepository.save(stock);

        // Mirror StockPriceScheduler: evict per-symbol cache, broadcast, check limits.
        evictStockCaches(symbol);
        broadcastPriceUpdate(stock);
        checkLimitOrders(stock);
        return true;
    }

    private void evictStockCaches(String symbol) {
        var stocksCache = cacheManager.getCache("stocks");
        if (stocksCache != null) {
            stocksCache.evictIfPresent(symbol);
        }
        var snapshotsCache = cacheManager.getCache("stockSnapshots");
        if (snapshotsCache != null) {
            snapshotsCache.evictIfPresent(symbol);
        }
    }

    private void broadcastPriceUpdate(Stock stock) {
        if (stock.getCurrentPrice() == null) return;
        Map<String, Object> priceUpdate = Map.of(
                stock.getSymbol(), Map.of(
                        "price", stock.getCurrentPrice(),
                        "extendedHoursPrice", stock.getExtendedHoursPrice() != null
                                ? stock.getExtendedHoursPrice() : ""
                )
        );
        messagingTemplate.convertAndSend("/topic/prices", priceUpdate);
    }

    private void checkLimitOrders(Stock stock) {
        BigDecimal currentPrice = stock.getCurrentPrice();
        if (currentPrice == null) return;

        List<Order> openOrders = orderRepository
                .findOpenLimitOrdersForSymbols(List.of(stock.getSymbol()));
        for (Order order : openOrders) {
            try {
                if (order.getLimitPrice() == null) continue;
                boolean met = order.getType() == Side.BUY
                        ? currentPrice.compareTo(order.getLimitPrice()) <= 0
                        : currentPrice.compareTo(order.getLimitPrice()) >= 0;
                if (met) {
                    orderService.executeLimitOrder(order.getId(), currentPrice);
                }
            } catch (Exception e) {
                log.error("Error checking limit order {} from WS: {}", order.getId(), e.getMessage());
            }
        }
    }

    private final class Listener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            log.info("Massive WS: socket open");
            WebSocket.Listener.super.onOpen(ws);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String full = textBuffer.toString();
                textBuffer.setLength(0);
                handleFrame(full);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("Massive WS closed: code={} reason={}", statusCode, reason);
            webSocket.set(null);
            subscribedSymbols.clear();
            appliedSymbolsThisSession.clear();
            lastEventAtMillis.set(0);
            if (state.getAndSet(State.DISCONNECTED) != State.DISCONNECTED) {
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("Massive WS error: {}", error.getMessage());
            webSocket.set(null);
            subscribedSymbols.clear();
            appliedSymbolsThisSession.clear();
            lastEventAtMillis.set(0);
            if (state.getAndSet(State.DISCONNECTED) != State.DISCONNECTED) {
                scheduleReconnect();
            }
        }
    }
}
