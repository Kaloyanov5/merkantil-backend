package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveBar;
import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import github.kaloyanov5.merkantil.util.MoneyUtil;
import github.kaloyanov5.merkantil.dto.response.StockHistoryResponse;
import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.dto.response.StockResponse;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;
    private final MassiveApiService massiveApiService;
    private final MarketCalendar marketCalendar;
    private final MarketSessionService marketSessionService;

    /**
     * Get all stocks with pagination
     */
    public Page<StockResponse> getAllStocks(int page, int size, String sortBy, String direction) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(sortDirection, sortBy));

        return stockRepository.findByIsActiveTrue(pageable)
                .map(this::mapToStockResponse);
    }

    /**
     * Get stock by symbol
     * TODO: replace caching with database price access
     */
    @Cacheable(value = "stocks", key = "#symbol")
    @Transactional
    public StockResponse getStockBySymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + symbol));

        // Update price from Massive if stale (older than 5 minutes)
        if (stock.getLastUpdated() == null ||
                stock.getLastUpdated().isBefore(LocalDateTime.now().minusMinutes(5))) {
            updateStockPriceFromMassive(stock);
        }

        return mapToStockResponse(stock);
    }

    /**
     * Get real-time quote from Massive
     */
    public StockQuoteResponse getQuote(String symbol) {
        MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(symbol.toUpperCase());

        if (snapshot == null) {
            throw new IllegalArgumentException("Unable to fetch quote for: " + symbol);
        }

        String marketSession = marketSessionService.getCurrentSession();

        BigDecimal currentPrice = resolveRegularPrice(snapshot, marketSession);
        if (currentPrice == null) {
            throw new IllegalArgumentException("Unable to fetch quote for: " + symbol);
        }

        String name = stockRepository.findBySymbol(symbol.toUpperCase())
                .map(Stock::getName)
                .orElse(null);

        return buildQuoteResponse(symbol.toUpperCase(), name, currentPrice, snapshot, marketSession);
    }

    /**
     * Search stocks
     */
    public Page<StockResponse> searchStocks(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        return stockRepository.findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(
                        query, query, pageable)
                .map(this::mapToStockResponse);
    }

    /**
     * Get stocks by sector
     */
    public Page<StockResponse> getStocksBySector(String sector, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "marketCap"));

        return stockRepository.findBySector(sector, pageable)
                .map(this::mapToStockResponse);
    }

    /**
     * Get all sectors
     */
    public List<String> getAllSectors() {
        return stockRepository.findAllSectors();
    }

    /**
     * Get top gainers
     */
    public List<StockResponse> getTopGainers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return stockRepository.findTopGainers(pageable).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get top losers
     */
    public List<StockResponse> getTopLosers(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return stockRepository.findTopLosers(pageable).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get most active (by volume)
     */
    public List<StockResponse> getMostActive(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return stockRepository.findTopByVolume(pageable).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get daily OHLCV history for a stock.
     *
     * <p>Daily bars are immutable once a trading day closes, so the local
     * {@code stock_price_history} table is authoritative for any range ending on
     * or before yesterday — provided it actually covers that range. We serve from
     * the DB whenever it does and only call Massive when the DB is missing data or
     * the request reaches into today (whose bar may still be forming). API results
     * for already-closed days are written through to the DB so the next load for
     * the same range needs no API call.
     */
    public List<StockHistoryResponse> getStockHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        String upper = symbol.toUpperCase();
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));

        // DB can only be authoritative for ranges that don't include today's
        // still-forming bar. When it covers the range, skip the API entirely.
        if (endDate.isBefore(today)) {
            List<StockPriceHistory> dbRows = stockPriceHistoryRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(upper, startDate, endDate);
            if (coversRange(dbRows, endDate)) {
                return dbRows.stream().map(this::mapToHistoryResponse).collect(Collectors.toList());
            }
        }

        List<MassiveBar> bars = massiveApiService.getHistoricalBars(upper, startDate, endDate);

        if (bars == null || bars.isEmpty()) {
            // Massive had nothing — serve whatever the DB holds as a last resort.
            return stockPriceHistoryRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(upper, startDate, endDate)
                    .stream()
                    .map(this::mapToHistoryResponse)
                    .collect(Collectors.toList());
        }

        writeThroughClosedBars(upper, bars, today);

        return bars.stream()
                .map(bar -> new StockHistoryResponse(
                        MassiveApiService.millisToLocalDate(bar.timestamp()),
                        MoneyUtil.of(bar.open()),
                        MoneyUtil.of(bar.high()),
                        MoneyUtil.of(bar.low()),
                        MoneyUtil.of(bar.close()),
                        bar.volume()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Whether the DB rows fully cover the requested range up to its last trading day.
     *
     * <p>Checks two things: (1) the most recent row is at least as new as the last
     * trading day on or before {@code endDate} (not stale), and (2) there is no
     * missing trading day <em>between</em> the earliest and latest rows we hold (no
     * interior gap). Missing days <em>before</em> the earliest row are treated as
     * pre-history (the stock had not started trading yet — e.g. a recent IPO like
     * KVYO) and deliberately ignored, otherwise a young stock's long-range chart
     * would re-hit the API forever.
     */
    private boolean coversRange(List<StockPriceHistory> rows, LocalDate endDate) {
        if (rows.isEmpty()) {
            return false;
        }
        LocalDate lastExpected = previousOrSameTradingDay(endDate);
        LocalDate lastHeld = rows.getLast().getDate();
        if (lastExpected != null && lastHeld.isBefore(lastExpected)) {
            return false; // behind — missing recent trading days
        }

        Set<LocalDate> present = rows.stream()
                .map(StockPriceHistory::getDate)
                .collect(Collectors.toSet());
        LocalDate firstHeld = rows.getFirst().getDate();
        for (LocalDate d = firstHeld; !d.isAfter(lastHeld); d = d.plusDays(1)) {
            if (marketCalendar.isTradingDay(d) && !present.contains(d)) {
                return false; // interior gap
            }
        }
        return true;
    }

    /** Latest trading day on or before {@code date}; null if none within a week. */
    private LocalDate previousOrSameTradingDay(LocalDate date) {
        for (int i = 0; i < 7; i++) {
            LocalDate candidate = date.minusDays(i);
            if (marketCalendar.isTradingDay(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Persist already-closed bars (date strictly before today) that we don't yet
     * hold, so a later request for the same range is served from the DB. Today's
     * unclosed bar is skipped, and any persistence failure is swallowed — it must
     * never break the chart response the caller is waiting on.
     */
    private void writeThroughClosedBars(String symbol, List<MassiveBar> bars, LocalDate today) {
        try {
            // Closed-day bars keyed by date: skips today's still-forming bar and
            // collapses any duplicate dates within the API response.
            Map<LocalDate, MassiveBar> closed = new LinkedHashMap<>();
            for (MassiveBar bar : bars) {
                LocalDate date = MassiveApiService.millisToLocalDate(bar.timestamp());
                if (date == null || !date.isBefore(today)) {
                    continue;
                }
                closed.put(date, bar);
            }
            if (closed.isEmpty()) {
                return;
            }

            // One query for the dates already stored in this span, then diff in
            // memory — instead of an existence check per bar.
            Set<LocalDate> existing = stockPriceHistoryRepository
                    .findBySymbolAndDateBetweenOrderByDateAsc(
                            symbol, Collections.min(closed.keySet()), Collections.max(closed.keySet()))
                    .stream()
                    .map(StockPriceHistory::getDate)
                    .collect(Collectors.toSet());

            List<StockPriceHistory> toSave = new ArrayList<>();
            closed.forEach((date, bar) -> {
                if (existing.contains(date)) {
                    return;
                }
                StockPriceHistory history = new StockPriceHistory();
                history.setSymbol(symbol);
                history.setDate(date);
                history.setOpen(MoneyUtil.of(bar.open()));
                history.setHigh(MoneyUtil.of(bar.high()));
                history.setLow(MoneyUtil.of(bar.low()));
                history.setClose(MoneyUtil.of(bar.close()));
                history.setVolume(bar.volume() != null ? bar.volume().longValue() : null);
                history.setCreatedAt(LocalDateTime.now());
                toSave.add(history);
            });

            if (!toSave.isEmpty()) {
                stockPriceHistoryRepository.saveAll(toSave);
                log.debug("Wrote through {} history rows for {}", toSave.size(), symbol);
            }
        } catch (Exception e) {
            log.warn("History write-through failed for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Get multiple quotes at once from Massive
     */
    public List<StockQuoteResponse> getMultipleQuotes(List<String> symbols) {
        Map<String, MassiveSnapshotTicker> snapshots = massiveApiService.getMultipleSnapshots(
                symbols.stream().map(String::toUpperCase).collect(Collectors.toList()));

        String marketSession = marketSessionService.getCurrentSession();

        return snapshots.entrySet().stream()
                .map(entry -> convertSnapshotToQuote(entry.getKey(), entry.getValue(), marketSession))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get detailed market status (OPEN, PRE_MARKET, AFTER_HOURS, CLOSED, HOLIDAY)
     */
    public Map<String, String> getMarketStatus() {
        Map<String, String> status = massiveApiService.getDetailedMarketStatus();
        if ("CLOSED".equals(status.get("status")) && marketCalendar.isHoliday(LocalDate.now(ZoneId.of("America/New_York")))) {
            Map<String, String> result = new java.util.HashMap<>(status);
            result.put("status", "HOLIDAY");
            return result;
        }
        return status;
    }

    /**
     * Update stock price from Massive (used for cache-miss refresh in getStockBySymbol).
     * Delegates to the same session-aware logic used by the scheduler.
     */
    @Transactional
    protected void updateStockPriceFromMassive(Stock stock) {
        try {
            MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(stock.getSymbol());
            if (snapshot == null) return;

            String marketSession = marketSessionService.getCurrentSession();

            if ("OPEN".equals(marketSession)) {
                BigDecimal price = resolveRegularPrice(snapshot, marketSession);
                if (price != null) stock.setCurrentPrice(price);
                stock.setExtendedHoursPrice(null);
                if (snapshot.day() != null) {
                    if (snapshot.day().high() != null && snapshot.day().high() > 0)
                        stock.setDayHigh(MoneyUtil.of(snapshot.day().high()));
                    if (snapshot.day().low() != null && snapshot.day().low() > 0)
                        stock.setDayLow(MoneyUtil.of(snapshot.day().low()));
                    if (snapshot.day().volume() != null && snapshot.day().volume() > 0)
                        stock.setVolume(snapshot.day().volume().longValue());
                }
            } else if ("PRE_MARKET".equals(marketSession) || "AFTER_HOURS".equals(marketSession)) {
                BigDecimal extPrice = resolveExtendedHoursPrice(snapshot, marketSession);
                stock.setExtendedHoursPrice(extPrice);
                // Sync currentPrice with official regular-session close
                if (snapshot.day() != null && snapshot.day().close() != null
                        && snapshot.day().close() > 0) {
                    stock.setCurrentPrice(MoneyUtil.of(snapshot.day().close()));
                } else if (snapshot.prevDay() != null && snapshot.prevDay().close() != null
                        && snapshot.prevDay().close() > 0) {
                    stock.setCurrentPrice(MoneyUtil.of(snapshot.prevDay().close()));
                }
                if (snapshot.prevDay() != null && snapshot.prevDay().close() != null
                        && snapshot.prevDay().close() > 0) {
                    stock.setPreviousClose(MoneyUtil.of(snapshot.prevDay().close()));
                }
            }

            stock.setLastUpdated(LocalDateTime.now());
            stockRepository.save(stock);
            log.info("Updated price for {} (session: {}): regular=${}, extended=${}",
                    stock.getSymbol(), marketSession, stock.getCurrentPrice(), stock.getExtendedHoursPrice());
        } catch (Exception e) {
            log.error("Error updating price for {} from Massive: {}", stock.getSymbol(), e.getMessage());
        }
    }

    private StockQuoteResponse convertSnapshotToQuote(String symbol, MassiveSnapshotTicker snapshot,
                                                       String marketSession) {
        if (snapshot == null) return null;

        BigDecimal currentPrice = resolveRegularPrice(snapshot, marketSession);
        if (currentPrice == null) return null;

        String name = stockRepository.findBySymbol(symbol).map(Stock::getName).orElse(null);
        return buildQuoteResponse(symbol, name, currentPrice, snapshot, marketSession);
    }

    /**
     * Resolves the regular-hours price from a snapshot.
     * During OPEN: min.close / lastTrade is the live price.
     * During PRE_MARKET / AFTER_HOURS / CLOSED: prevDay.close is the official regular-session price.
     */
    private BigDecimal resolveRegularPrice(MassiveSnapshotTicker snapshot, String marketSession) {
        if ("OPEN".equals(marketSession)) {
            // Live intraday price
            if (snapshot.lastTrade() != null && snapshot.lastTrade().price() != null
                    && snapshot.lastTrade().price() > 0) {
                return MoneyUtil.of(snapshot.lastTrade().price());
            }
            if (snapshot.min() != null && snapshot.min().close() != null
                    && snapshot.min().close() > 0) {
                return MoneyUtil.of(snapshot.min().close());
            }
            if (snapshot.fmv() != null && snapshot.fmv() > 0) {
                return MoneyUtil.of(snapshot.fmv());
            }
            if (snapshot.day() != null && snapshot.day().close() != null
                    && snapshot.day().close() > 0) {
                return MoneyUtil.of(snapshot.day().close());
            }
        } else {
            // Outside regular hours — return last regular-session close
            if (snapshot.prevDay() != null && snapshot.prevDay().close() != null
                    && snapshot.prevDay().close() > 0) {
                return MoneyUtil.of(snapshot.prevDay().close());
            }
            // Fallback: day.close if prevDay unavailable
            if (snapshot.day() != null && snapshot.day().close() != null
                    && snapshot.day().close() > 0) {
                return MoneyUtil.of(snapshot.day().close());
            }
        }
        return null;
    }

    /**
     * Resolves the extended-hours price (pre-market or after-hours).
     * Only meaningful when marketSession is PRE_MARKET or AFTER_HOURS.
     */
    private BigDecimal resolveExtendedHoursPrice(MassiveSnapshotTicker snapshot, String marketSession) {
        if (!"PRE_MARKET".equals(marketSession) && !"AFTER_HOURS".equals(marketSession)) {
            return null;
        }
        if (snapshot.lastTrade() != null && snapshot.lastTrade().price() != null
                && snapshot.lastTrade().price() > 0) {
            return MoneyUtil.of(snapshot.lastTrade().price());
        }
        if (snapshot.min() != null && snapshot.min().close() != null
                && snapshot.min().close() > 0) {
            return MoneyUtil.of(snapshot.min().close());
        }
        if (snapshot.fmv() != null && snapshot.fmv() > 0) {
            return MoneyUtil.of(snapshot.fmv());
        }
        return null;
    }

    /**
     * Classifies the extended-hours display state for the frontend.
     * Returns {@code null} outside PRE_MARKET/AFTER_HOURS; {@code "NO_TRADES"}
     * when in an extended session with no visible extended-hours price (feed not
     * yet caught up, or the ticker genuinely has no extended-hours trades);
     * {@code "TRADING"} otherwise.
     */
    static String resolveExtendedHoursStatus(BigDecimal extendedHoursPrice, String marketSession) {
        if (!"PRE_MARKET".equals(marketSession) && !"AFTER_HOURS".equals(marketSession)) {
            return null;
        }
        return extendedHoursPrice == null ? "NO_TRADES" : "TRADING";
    }

    private StockQuoteResponse buildQuoteResponse(String symbol, String name,
                                                   BigDecimal currentPrice, MassiveSnapshotTicker snapshot,
                                                   String marketSession) {
        BigDecimal previousClose = snapshot.prevDay() != null
                ? MoneyUtil.of(snapshot.prevDay().close()) : null;

        // Prefer Massive's pre-computed change values; fall back to manual calculation
        BigDecimal change = snapshot.todaysChange() != null
                ? MoneyUtil.of(snapshot.todaysChange())
                : (previousClose != null ? currentPrice.subtract(previousClose) : null);
        Double changePercent = snapshot.todaysChangePerc() != null
                ? snapshot.todaysChangePerc()
                : (MoneyUtil.isPositive(previousClose) ? percentOf(currentPrice.subtract(previousClose), previousClose) : null);

        // day OHLV fields are 0 during pre/after-hours — return null instead of 0
        BigDecimal dayHigh = snapshot.day() != null && snapshot.day().high() != null
                             && snapshot.day().high() > 0 ? MoneyUtil.of(snapshot.day().high()) : null;
        BigDecimal dayLow  = snapshot.day() != null && snapshot.day().low() != null
                             && snapshot.day().low() > 0 ? MoneyUtil.of(snapshot.day().low()) : null;
        BigDecimal dayOpen = snapshot.day() != null && snapshot.day().open() != null
                             && snapshot.day().open() > 0 ? MoneyUtil.of(snapshot.day().open()) : null;
        Long   dayVolume = snapshot.day() != null && snapshot.day().volume() != null
                           && snapshot.day().volume() > 0
                           ? snapshot.day().volume() : null;

        BigDecimal extendedHoursPrice = resolveExtendedHoursPrice(snapshot, marketSession);
        BigDecimal extendedHoursChange = (extendedHoursPrice != null && currentPrice != null)
                ? extendedHoursPrice.subtract(currentPrice) : null;
        Double extendedHoursChangePercent = (extendedHoursChange != null && MoneyUtil.isPositive(currentPrice))
                ? percentOf(extendedHoursChange, currentPrice) : null;

        String extendedHoursStatus = resolveExtendedHoursStatus(extendedHoursPrice, marketSession);

        return new StockQuoteResponse(symbol, name, currentPrice, change, changePercent,
                dayHigh, dayLow, dayOpen, previousClose, dayVolume,
                extendedHoursPrice, extendedHoursChange, extendedHoursChangePercent,
                extendedHoursStatus, marketSession, LocalDateTime.now());
    }

    /** Expresses {@code amount} as a percentage of {@code base}, as a display-only double. */
    private Double percentOf(BigDecimal amount, BigDecimal base) {
        return amount.divide(base, 6, MoneyUtil.ROUNDING)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private StockResponse mapToStockResponse(Stock stock) {
        BigDecimal changeAmount = null;
        Double changePercent = null;

        if (stock.getCurrentPrice() != null && stock.getPreviousClose() != null) {
            changeAmount = stock.getCurrentPrice().subtract(stock.getPreviousClose());
            if (MoneyUtil.isPositive(stock.getPreviousClose())) {
                changePercent = percentOf(changeAmount, stock.getPreviousClose());
            }
        }

        String marketSession = marketSessionService.getCurrentSession();
        String extendedHoursStatus =
                resolveExtendedHoursStatus(stock.getExtendedHoursPrice(), marketSession);

        return new StockResponse(
                stock.getId(),
                stock.getSymbol(),
                stock.getName(),
                stock.getExchange(),
                stock.getCurrency(),
                stock.getSector(),
                stock.getIndustry(),
                stock.getCurrentPrice(),
                stock.getPreviousClose(),
                stock.getDayHigh(),
                stock.getDayLow(),
                stock.getVolume(),
                stock.getMarketCap(),
                changeAmount,
                changePercent,
                stock.getExtendedHoursPrice(),
                extendedHoursStatus,
                stock.getIsActive(),
                stock.getLastUpdated()
        );
    }

    private StockHistoryResponse mapToHistoryResponse(StockPriceHistory history) {
        return new StockHistoryResponse(
                history.getDate(),
                history.getOpen(),
                history.getHigh(),
                history.getLow(),
                history.getClose(),
                history.getVolume()
        );
    }
}
