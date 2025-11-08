package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.alpaca.AlpacaBar;
import github.kaloyanov5.merkantil.dto.alpaca.AlpacaSnapshot;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;
    private final AlpacaApiService alpacaApiService;

    private static final int BATCH_SIZE = 10; // Process 10 stocks per API call

    /**
     * Update all stock prices every 30 seconds using batch requests
     * With 30 stocks and batch size 10: 3 API calls per update
     * 3 calls × 2 updates/min = 6 API calls/min (way under 200 limit!)
     */
    @Scheduled(fixedRate = 30000) // 30 seconds = 30000 milliseconds
    @Transactional
    @CacheEvict(value = {"stocks", "alpacaSnapshots"}, allEntries = true) // Clear all caches
    public void updateAllStockPrices() {
        // Only update during market hours (9:30 AM - 4:00 PM EST)
        if (!isMarketHours()) {
            log.debug("Market is closed, skipping price update");
            return;
        }

        try {
            log.info("Starting scheduled stock price update for all stocks...");

            // Get all active stocks
            List<Stock> allStocks = stockRepository.findAll().stream()
                    .filter(s -> s.getIsActive() != null && s.getIsActive())
                    .collect(Collectors.toList());

            if (allStocks.isEmpty()) {
                log.warn("No active stocks found in database. Import stocks first!");
                return;
            }

            log.info("Updating {} stocks in batches of {}", allStocks.size(), BATCH_SIZE);

            // Process stocks in batches
            for (int i = 0; i < allStocks.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, allStocks.size());
                List<Stock> batch = allStocks.subList(i, end);
                updateStockBatch(batch);

                // Small delay between batches to avoid rate limiting
                if (i + BATCH_SIZE < allStocks.size()) {
                    Thread.sleep(100);
                }
            }

            log.info("All stock price updates completed");
        } catch (Exception e) {
            log.error("Error in scheduled stock price update: {}", e.getMessage());
        }
    }

    /**
     * Save end-of-day snapshot to history at market close (4:00 PM EST)
     * Runs every day at 4:05 PM to capture closing prices
     */
    @Scheduled(cron = "0 5 16 * * MON-FRI") // 4:05 PM EST, Monday-Friday
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

        LocalDate today = LocalDate.now();
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

                // fetch today's bar from Alpaca
                List<AlpacaBar> bars =
                        alpacaApiService.getHistoricalBars(stock.getSymbol(), today, today);

                if (bars != null && !bars.isEmpty()) {
                    AlpacaBar bar = bars.getFirst();

                    StockPriceHistory history = new StockPriceHistory();
                    history.setSymbol(stock.getSymbol());
                    history.setDate(today);
                    history.setOpen(bar.getOpen());
                    history.setHigh(bar.getHigh());
                    history.setLow(bar.getLow());
                    history.setClose(bar.getClose());
                    history.setVolume(bar.getVolume());
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
        log.info("Starting intelligent historical data backfill with gap detection...");

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
     * This checks for missing dates and backfills them from Alpaca
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

        // fetch historical bars from Alpaca (now returns List<AlpacaBar> directly)
        List<AlpacaBar> bars =
                alpacaApiService.getHistoricalBars(symbol.toUpperCase(), startDate, endDate);

        if (bars == null || bars.isEmpty()) {
            log.warn("No historical data available for {}", symbol);
            return 0;
        }

        int saved = 0;

        for (AlpacaBar bar : bars) {
            try {
                LocalDate date = LocalDate.parse(bar.getTimestamp().substring(0, 10));

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
                history.setOpen(bar.getOpen());
                history.setHigh(bar.getHigh());
                history.setLow(bar.getLow());
                history.setClose(bar.getClose());
                history.setVolume(bar.getVolume());
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
    private void updateStockBatch(List<Stock> stocks) {
        try {
            List<String> symbols = stocks.stream()
                    .map(Stock::getSymbol)
                    .collect(Collectors.toList());

            log.debug("Fetching snapshots for symbols: {}", symbols);

            // get multiple snapshots in one API call
            Map<String, AlpacaSnapshot> snapshots = alpacaApiService.getMultipleSnapshots(symbols);

            for (Stock stock : stocks) {
                AlpacaSnapshot snapshot = snapshots.get(stock.getSymbol());
                if (snapshot != null && snapshot.getLatestTrade() != null) {
                    updateStockFromSnapshot(stock, snapshot);
                } else {
                    log.warn("No snapshot data for stock: {}", stock.getSymbol());
                }
            }

            // save to database (cache already evicted by @CacheEvict on calling method)
            stockRepository.saveAll(stocks);
            log.debug("Updated batch of {} stocks in database", stocks.size());
        } catch (Exception e) {
            log.error("Error updating stock batch: {}", e.getMessage());
        }
    }

    /**
     * Update single stock from snapshot
     */
    private void updateStockFromSnapshot(Stock stock, AlpacaSnapshot snapshot) {
        stock.setCurrentPrice(snapshot.getLatestTrade().getPrice());

        if (snapshot.getPrevDailyBar() != null) {
            stock.setPreviousClose(snapshot.getPrevDailyBar().getClose());
        }

        if (snapshot.getDailyBar() != null) {
            stock.setDayHigh(snapshot.getDailyBar().getHigh());
            stock.setDayLow(snapshot.getDailyBar().getLow());
            stock.setVolume(snapshot.getDailyBar().getVolume());
        }

        stock.setLastUpdated(LocalDateTime.now());
    }

    /**
     * Check if market is open (9:30 AM - 4:00 PM EST)
     */
    private boolean isMarketHours() {
        LocalTime now = LocalTime.now();
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);

        return now.isAfter(marketOpen) && now.isBefore(marketClose);
    }

    /**
     * Manual trigger for price update (useful for testing)
     * This method can be called directly without waiting for the schedule
     */
    @Transactional
    @CacheEvict(value = {"stocks", "alpacaSnapshots"}, allEntries = true) // Clear all caches
    public void updatePricesNow() {
        log.info("Manual price update triggered");

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
            updateStockBatch(batch);
        }

        log.info("Manual price update completed for {} stocks", allStocks.size());
    }

    /**
     * Result class for backfill operations
     */
    public record BackfillResult(int stocksProcessed, int recordsAdded, String message) {
    }
}