package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.alpaca.AlpacaSnapshot;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final AlpacaApiService alpacaApiService;

    private static final int BATCH_SIZE = 10; // Process 10 stocks per API call

    /**
     * Update all stock prices every 30 seconds using batch requests
     * With 30 stocks and batch size 10: 3 API calls per update
     * 3 calls × 2 updates/min = 6 API calls/min
     */
    @Scheduled(fixedRate = 30000) // 30 seconds = 30000 milliseconds
    @Transactional
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
     * Update a batch of stocks using multiple snapshots (efficient batch API call)
     * Updates both the database AND Redis cache
     */
    private void updateStockBatch(List<Stock> stocks) {
        try {
            List<String> symbols = stocks.stream()
                    .map(Stock::getSymbol)
                    .collect(Collectors.toList());

            log.debug("Fetching snapshots for symbols: {}", symbols);

            // Get multiple snapshots in one API call (much more efficient)
            Map<String, AlpacaSnapshot> snapshots = alpacaApiService.getMultipleSnapshots(symbols);

            for (Stock stock : stocks) {
                AlpacaSnapshot snapshot = snapshots.get(stock.getSymbol());
                if (snapshot != null && snapshot.getLatestTrade() != null) {
                    updateStockFromSnapshot(stock, snapshot);
                } else {
                    log.warn("No snapshot data for stock: {}", stock.getSymbol());
                }
            }

            // Save to database (this also updates Redis cache due to @Cacheable)
            stockRepository.saveAll(stocks);
            log.debug("Updated batch of {} stocks in database and cache", stocks.size());
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

        // For testing purposes, market hours are disabled
        // return now.isAfter(marketOpen) && now.isBefore(marketClose);
        return true;
    }

    /**
     * Manual trigger for price update (useful for testing)
     * This method can be called directly without waiting for the schedule
     */
    @Transactional
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
}