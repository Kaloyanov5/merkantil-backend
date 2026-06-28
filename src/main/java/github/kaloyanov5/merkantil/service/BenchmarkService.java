package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.configuration.AnalyticsProperties;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Supplies the benchmark (default SPY) daily return series, seeding its price history
 * through the existing backfill path when coverage is missing. The benchmark lives only
 * in {@code stock_price_history}; it is never added to the tradeable stock universe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BenchmarkService {

    private final StockPriceHistoryRepository priceHistoryRepository;
    private final StockPriceScheduler stockPriceScheduler;
    private final AnalyticsProperties analyticsProperties;

    public String benchmarkSymbol() {
        return analyticsProperties.benchmarkSymbol().toUpperCase();
    }

    /** Benchmark daily returns aligned to {@code tradingDays}; empty when no data could be obtained. */
    public List<DailyReturn> dailyReturns(List<LocalDate> tradingDays) {
        if (tradingDays.size() < 2) return List.of();
        String symbol = benchmarkSymbol();
        LocalDate from = tradingDays.get(0).minusDays(7);
        LocalDate to = tradingDays.get(tradingDays.size() - 1);

        ensureCoverage(symbol, from, to);

        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (StockPriceHistory h : priceHistoryRepository.findBySymbolAndDateBetweenOrderByDateAsc(symbol, from, to)) {
            closes.put(h.getDate(), h.getClose());
        }
        if (closes.isEmpty()) {
            log.warn("No benchmark data for {} after seeding attempt", symbol);
            return List.of();
        }
        return PriceSeries.dailyReturns(closes, tradingDays);
    }

    private void ensureCoverage(String symbol, LocalDate from, LocalDate to) {
        boolean haveStart = priceHistoryRepository.findMostRecentPriceOnOrBefore(symbol, from).isPresent();
        boolean haveEnd = priceHistoryRepository.findBySymbolAndDate(symbol, to).isPresent()
                || priceHistoryRepository.findMostRecentPriceOnOrBefore(symbol, to).isPresent();
        if (haveStart && haveEnd) return;
        try {
            int seeded = stockPriceScheduler.backfillStockHistory(symbol, from, to);
            log.info("Seeded {} benchmark bars for {}", seeded, symbol);
        } catch (Exception e) {
            log.warn("Failed to seed benchmark {}: {}", symbol, e.getMessage());
        }
    }
}
