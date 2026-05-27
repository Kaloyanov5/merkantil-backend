package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveBar;
import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.entity.Order;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.OrderRepository;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;
    private final MassiveApiService massiveApiService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MarketSessionService marketSessionService;

    private static final int BATCH_SIZE = 10; // Process 10 stocks per API call

    // Circuit-breaker: when the scheduled tick fails this many consecutive times,
    // skip subsequent runs for the cooldown window so a flapping Massive upstream
    // does not pin the executor or spam the logs every 30 seconds.
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration COOLDOWN = Duration.ofMinutes(5);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.EPOCH);

    /**
     * Update all stock prices every 30 seconds using batch requests
     * With 30 stocks and batch size 10: 3 API calls per update
     */
    @Scheduled(fixedRate = 30000) // 30 seconds = 30000 milliseconds
    @CacheEvict(value = {"stocks", "stockSnapshots"}, allEntries = true)
    public void updateAllStockPrices() {
        Instant now = Instant.now();
        Instant cooldownEnd = openUntil.get();
        if (now.isBefore(cooldownEnd)) {
            log.debug("Price-update circuit open until {}; skipping tick", cooldownEnd);
            return;
        }

        try {
            String marketSession = marketSessionService.getCurrentSession();
            log.info("Starting scheduled stock price update (session: {})...", marketSession);

            if ("CLOSED".equals(marketSession) || "HOLIDAY".equals(marketSession)) {
                log.debug("Market is {}. Skipping price update.", marketSession);
                return;
            }

            // Get all active stocks
            List<Stock> allStocks = stockRepository.findAll().stream()
                    .filter(s -> s.getIsActive() != null && s.getIsActive())
                    .collect(Collectors.toList());

            if (allStocks.isEmpty()) {
                log.warn("No active stocks found in database. Import stocks first!");
                return;
            }

            log.info("Updating {} stocks in batches of {} (session: {})",
                    allStocks.size(), BATCH_SIZE, marketSession);

            // Process stocks in batches
            for (int i = 0; i < allStocks.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, allStocks.size());
                List<Stock> batch = allStocks.subList(i, end);
                updateStockBatch(batch, marketSession);
            }

            log.info("All stock price updates completed");
            consecutiveFailures.set(0);
        } catch (Exception e) {
            int failures = consecutiveFailures.incrementAndGet();
            log.error("Error in scheduled stock price update (failure {}): {}", failures, e.getMessage());
            if (failures >= FAILURE_THRESHOLD) {
                Instant until = Instant.now().plus(COOLDOWN);
                openUntil.set(until);
                consecutiveFailures.set(0);
                log.warn("Price-update circuit opened after {} consecutive failures; pausing until {}",
                        FAILURE_THRESHOLD, until);
            }
        }
    }

    /**
     * Save end-of-day snapshot to history at market close (4:00 PM EST)
     * Runs every day at 4:05 PM to capture closing prices
     */
    @Scheduled(cron = "0 30 0 * * TUE-SAT") // 12:30 AM local (after 4 PM EST market close), Tue-Sat
    @Transactional
    public void saveEndOfDayHistory() {
        log.info("Starting end-of-day history capture...");

        List<Stock> allStocks = stockRepository.findAll().stream()
                .filter(s -> s.getIsActive() != null && s.getIsActive())
                .toList();

        if (allStocks.isEmpty()) {
            log.warn("No active stocks found");
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        int saved = 0;
        int skipped = 0;

        for (Stock stock : allStocks) {
            try {
                // check if today's history already exists
                Optional<StockPriceHistory> existing = stockPriceHistoryRepository
                        .findBySymbolAndDate(stock.getSymbol(), today);

                if (existing.isPresent()) {
                    log.debug("History already exists for {} on {}", stock.getSymbol(), today);
                    skipped++;
                    continue;
                }

                // fetch today's bar from Massive
                List<MassiveBar> bars =
                        massiveApiService.getHistoricalBars(stock.getSymbol(), today, today);

                if (bars != null && !bars.isEmpty()) {
                    MassiveBar bar = bars.getFirst();

                    StockPriceHistory history = new StockPriceHistory();
                    history.setSymbol(stock.getSymbol());
                    history.setDate(today);
                    history.setOpen(MoneyUtil.of(bar.open()));
                    history.setHigh(MoneyUtil.of(bar.high()));
                    history.setLow(MoneyUtil.of(bar.low()));
                    history.setClose(MoneyUtil.of(bar.close()));
                    history.setVolume(bar.volume() != null ? bar.volume().longValue() : null);
                    history.setCreatedAt(LocalDateTime.now());

                    stockPriceHistoryRepository.save(history);
                    saved++;
                    log.debug("Saved EOD history for {}", stock.getSymbol());
                } else {
                    log.warn("No bar data available for {} on {}", stock.getSymbol(), today);
                }

                // small delay to respect rate limits
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("Error saving EOD history for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        log.info("End-of-day history capture completed: {} saved, {} skipped", saved, skipped);
    }

    /**
     * Backfill historical data for all stocks
     * Runs once per day at 5:00 AM EST (before market opens)
     * Intelligently detects gaps and fills them
     */
    @Scheduled(cron = "0 0 5 * * *") // 5:00 AM EST daily
    @Transactional
    public void backfillHistoricalData() {
        log.info("Starting historical data backfill with gap detection...");

        List<Stock> allStocks = stockRepository.findAll().stream()
                .filter(s -> s.getIsActive() != null && s.getIsActive())
                .toList();

        if (allStocks.isEmpty()) {
            log.warn("No active stocks found");
            return;
        }

        int totalFilled = 0;

        for (Stock stock : allStocks) {
            try {
                // detect and fill gaps for this stock
                int filled = detectAndFillGaps(stock.getSymbol());
                totalFilled += filled;

                // delay to respect rate limits
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("Error backfilling history for {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        log.info("Historical data backfill completed: {} records added across all stocks", totalFilled);
    }

    /**
     * Detect gaps in historical data and fill them
     * This checks for missing dates and backfills them from Massive
     */
    @Transactional
    public int detectAndFillGaps(String symbol) {
        log.info("Detecting gaps for {}", symbol);

        // get all existing historical records for this stock
        List<StockPriceHistory> existingHistory = stockPriceHistoryRepository
                .findBySymbolOrderByDateDesc(symbol.toUpperCase());

        if (existingHistory.isEmpty()) {
            // no history at all
            log.info("No history found for {}, backfilling last month", symbol);
            LocalDate endDate = LocalDate.now().minusDays(1);
            LocalDate startDate = endDate.minusDays(31);
            return backfillStockHistory(symbol, startDate, endDate);
        }

        // get the most recent date we have data for
        LocalDate mostRecentDate = existingHistory.getFirst().getDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // calculate gap in days
        long daysSinceLastUpdate = java.time.temporal.ChronoUnit.DAYS.between(mostRecentDate, yesterday);

        if (daysSinceLastUpdate <= 1) {
            log.debug("No gap detected for {} (last update: {})", symbol, mostRecentDate);
            return 0;
        }

        if (daysSinceLastUpdate > 365) {
            log.warn("Large gap detected for {} ({} days). Limiting backfill to 365 days",
                    symbol, daysSinceLastUpdate);
            // only backfill last 365 days to avoid excessive API calls
            LocalDate startDate = yesterday.minusDays(365);
            return backfillStockHistory(symbol, startDate, yesterday);
        }

        // fill the gap
        log.info("Gap detected for {}: {} days from {} to {}",
                symbol, daysSinceLastUpdate, mostRecentDate.plusDays(1), yesterday);

        return backfillStockHistory(symbol, mostRecentDate.plusDays(1), yesterday);
    }

    /**
     * One-time manual backfill for a specific date range
     * This can be called manually via admin endpoint
     */
    @Transactional
    public int backfillStockHistory(String symbol, LocalDate startDate, LocalDate endDate) {
        log.info("Backfilling history for {} from {} to {}", symbol, startDate, endDate);

        // fetch historical bars from Massive
        List<MassiveBar> bars =
                massiveApiService.getHistoricalBars(symbol.toUpperCase(), startDate, endDate);

        if (bars == null || bars.isEmpty()) {
            log.warn("No historical data available for {}", symbol);
            return 0;
        }

        int saved = 0;

        for (MassiveBar bar : bars) {
            try {
                LocalDate date = MassiveApiService.millisToLocalDate(bar.timestamp());
                if (date == null) continue;

                // check if this date already exists
                Optional<StockPriceHistory> existing = stockPriceHistoryRepository
                        .findBySymbolAndDate(symbol.toUpperCase(), date);

                if (existing.isPresent()) {
                    continue; // skip if already exists
                }

                // create new history record
                StockPriceHistory history = new StockPriceHistory();
                history.setSymbol(symbol.toUpperCase());
                history.setDate(date);
                history.setOpen(MoneyUtil.of(bar.open()));
                history.setHigh(MoneyUtil.of(bar.high()));
                history.setLow(MoneyUtil.of(bar.low()));
                history.setClose(MoneyUtil.of(bar.close()));
                history.setVolume(bar.volume() != null ? bar.volume().longValue() : null);
                history.setCreatedAt(LocalDateTime.now());

                stockPriceHistoryRepository.save(history);
                saved++;
            } catch (Exception e) {
                log.error("Error saving historical record for {}: {}", symbol, e.getMessage());
            }
        }

        log.info("Backfilled {} records for {}", saved, symbol);
        return saved;
    }

    /**
     * Backfill history for all stocks for a specific date range
     * Can be triggered manually via admin endpoint
     */
    @Transactional
    public BackfillResult backfillAllStocks(LocalDate startDate, LocalDate endDate) {
        log.info("Starting manual backfill for all stocks from {} to {}", startDate, endDate);

        List<Stock> allStocks = stockRepository.findAll().stream()
                .filter(s -> s.getIsActive() != null && s.getIsActive())
                .toList();

        if (allStocks.isEmpty()) {
            return new BackfillResult(0, 0, "No active stocks found");
        }

        int totalRecords = 0;
        int stocksProcessed = 0;

        for (Stock stock : allStocks) {
            try {
                int records = backfillStockHistory(stock.getSymbol(), startDate, endDate);
                totalRecords += records;
                stocksProcessed++;

                // delay to respect rate limits
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("Error backfilling {}: {}", stock.getSymbol(), e.getMessage());
            }
        }

        String message = String.format("Backfilled %d records for %d stocks", totalRecords, stocksProcessed);
        log.info(message);
        return new BackfillResult(stocksProcessed, totalRecords, message);
    }

    /**
     * Update a batch of stocks using multiple snapshots (efficient batch API call)
     * Updates both the database AND evicts Redis cache
     */
    private void updateStockBatch(List<Stock> stocks, String marketSession) {
        try {
            List<String> symbols = stocks.stream()
                    .map(Stock::getSymbol)
                    .collect(Collectors.toList());

            log.debug("Fetching snapshots for symbols: {}", symbols);

            // get multiple snapshots in one API call
            Map<String, MassiveSnapshotTicker> snapshots = massiveApiService.getMultipleSnapshots(symbols);

            for (Stock stock : stocks) {
                MassiveSnapshotTicker snapshot = snapshots.get(stock.getSymbol());
                if (snapshot != null) {
                    updateStockFromSnapshot(stock, snapshot, marketSession);
                } else {
                    log.warn("No snapshot data for stock: {}", stock.getSymbol());
                }
            }

            // save to database (cache already evicted by @CacheEvict on calling method)
            stockRepository.saveAll(stocks);
            log.debug("Updated batch of {} stocks in database", stocks.size());

            // Broadcast updated prices to all connected WebSocket clients
            Map<String, Object> priceUpdate = stocks.stream()
                    .filter(s -> s.getCurrentPrice() != null)
                    .collect(Collectors.toMap(
                            Stock::getSymbol,
                            s -> Map.of(
                                    "price", s.getCurrentPrice(),
                                    "extendedHoursPrice", s.getExtendedHoursPrice() != null
                                            ? s.getExtendedHoursPrice() : ""
                            )
                    ));
            messagingTemplate.convertAndSend("/topic/prices", priceUpdate);

            // Check if any open limit orders can now be filled
            checkLimitOrders(stocks);
        } catch (Exception e) {
            log.error("Error updating stock batch: {}", e.getMessage());
        }
    }

    /**
     * Check open limit orders for the updated stocks and execute any that meet their condition.
     * BUY: execute when currentPrice <= limitPrice
     * SELL: execute when currentPrice >= limitPrice
     */
    private void checkLimitOrders(List<Stock> stocks) {
        List<String> symbols = stocks.stream().map(Stock::getSymbol).toList();
        List<Order> openOrders = orderRepository.findOpenLimitOrdersForSymbols(symbols);

        if (openOrders.isEmpty()) return;

        Map<String, BigDecimal> priceMap = stocks.stream()
                .filter(s -> s.getCurrentPrice() != null)
                .collect(Collectors.toMap(Stock::getSymbol, Stock::getCurrentPrice));

        for (Order order : openOrders) {
            try {
                BigDecimal currentPrice = priceMap.get(order.getSymbol());
                if (currentPrice == null || order.getLimitPrice() == null) continue;

                boolean conditionMet = order.getType() == Side.BUY
                        ? currentPrice.compareTo(order.getLimitPrice()) <= 0
                        : currentPrice.compareTo(order.getLimitPrice()) >= 0;

                if (conditionMet) {
                    orderService.executeLimitOrder(order.getId(), currentPrice);
                }
            } catch (Exception e) {
                log.error("Error checking limit order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    /**
     * Update single stock from snapshot, respecting market session.
     *
     * OPEN:        update currentPrice with live intraday price, clear extendedHoursPrice.
     * PRE_MARKET / AFTER_HOURS: update extendedHoursPrice only; leave currentPrice as
     *              the last regular-session close so the frontend always has a clean
     *              "official" price alongside the extended-hours price.
     */
    private void updateStockFromSnapshot(Stock stock, MassiveSnapshotTicker snapshot,
                                         String marketSession) {
        if ("OPEN".equals(marketSession)) {
            // Live regular-hours price — priority: lastTrade > min.close > fmv > day.close
            if (snapshot.lastTrade() != null && snapshot.lastTrade().price() != null
                    && snapshot.lastTrade().price() > 0) {
                stock.setCurrentPrice(MoneyUtil.of(snapshot.lastTrade().price()));
            } else if (snapshot.min() != null && snapshot.min().close() != null
                    && snapshot.min().close() > 0) {
                stock.setCurrentPrice(MoneyUtil.of(snapshot.min().close()));
            } else if (snapshot.fmv() != null && snapshot.fmv() > 0) {
                stock.setCurrentPrice(MoneyUtil.of(snapshot.fmv()));
            } else if (snapshot.day() != null && snapshot.day().close() != null
                    && snapshot.day().close() > 0) {
                stock.setCurrentPrice(MoneyUtil.of(snapshot.day().close()));
            } else {
                log.debug("No valid price in snapshot for {}, currentPrice unchanged ({})",
                        stock.getSymbol(), stock.getCurrentPrice());
            }
            // Clear extended-hours price during regular session
            stock.setExtendedHoursPrice(null);

            if (snapshot.day() != null) {
                if (snapshot.day().high() != null && snapshot.day().high() > 0) {
                    stock.setDayHigh(MoneyUtil.of(snapshot.day().high()));
                }
                if (snapshot.day().low() != null && snapshot.day().low() > 0) {
                    stock.setDayLow(MoneyUtil.of(snapshot.day().low()));
                }
                if (snapshot.day().volume() != null && snapshot.day().volume() > 0) {
                    stock.setVolume(snapshot.day().volume().longValue());
                }
            }
        } else {
            // PRE_MARKET or AFTER_HOURS — update extended price only, leave currentPrice intact
            BigDecimal extPrice = null;
            if (snapshot.lastTrade() != null && snapshot.lastTrade().price() != null
                    && snapshot.lastTrade().price() > 0) {
                extPrice = MoneyUtil.of(snapshot.lastTrade().price());
            } else if (snapshot.min() != null && snapshot.min().close() != null
                    && snapshot.min().close() > 0) {
                extPrice = MoneyUtil.of(snapshot.min().close());
            } else if (snapshot.fmv() != null && snapshot.fmv() > 0) {
                extPrice = MoneyUtil.of(snapshot.fmv());
            }

            if (extPrice != null) {
                stock.setExtendedHoursPrice(extPrice);
                log.debug("Extended hours price for {}: {} (session: {})",
                        stock.getSymbol(), extPrice, marketSession);
            }

            // Sync currentPrice with the official regular-session close.
            // During AFTER_HOURS: day.close is today's official close (fixes drift
            // from the last intraday tick that may differ from the closing auction).
            // During PRE_MARKET: day.close may be 0 (no trading yet), so fall back to prevDay.close.
            if (snapshot.day() != null && snapshot.day().close() != null
                    && snapshot.day().close() > 0) {
                stock.setCurrentPrice(MoneyUtil.of(snapshot.day().close()));
            } else if (snapshot.prevDay() != null && snapshot.prevDay().close() != null
                    && snapshot.prevDay().close() > 0) {
                stock.setCurrentPrice(MoneyUtil.of(snapshot.prevDay().close()));
            }

            // Keep previousClose up to date from prevDay
            if (snapshot.prevDay() != null && snapshot.prevDay().close() != null
                    && snapshot.prevDay().close() > 0) {
                stock.setPreviousClose(MoneyUtil.of(snapshot.prevDay().close()));
            }
        }

        stock.setLastUpdated(LocalDateTime.now());
    }

    /**
     * Manual trigger for price update (useful for testing)
     * This method can be called directly without waiting for the schedule
     */
    @CacheEvict(value = {"stocks", "stockSnapshots"}, allEntries = true)
    public void updatePricesNow() {
        log.info("Manual price update triggered");

        String marketSession = marketSessionService.getCurrentSession();

        List<Stock> allStocks = stockRepository.findAll().stream()
                .filter(s -> s.getIsActive() != null && s.getIsActive())
                .collect(Collectors.toList());

        if (allStocks.isEmpty()) {
            log.warn("No active stocks found");
            return;
        }

        for (int i = 0; i < allStocks.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allStocks.size());
            List<Stock> batch = allStocks.subList(i, end);
            updateStockBatch(batch, marketSession);
        }

        log.info("Manual price update completed for {} stocks", allStocks.size());
    }

    /**
     * Result class for backfill operations
     */
    public record BackfillResult(int stocksProcessed, int recordsAdded, String message) {
    }
}
