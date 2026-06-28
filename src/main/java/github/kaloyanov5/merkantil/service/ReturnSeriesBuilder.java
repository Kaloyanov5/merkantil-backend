package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds time-weighted daily return series from the transaction ledger and stored
 * price history. Portfolio returns weight each holding by its start-of-day market
 * value, so cash flows (buying/selling) never register as market returns.
 */
@Service
@RequiredArgsConstructor
public class ReturnSeriesBuilder {

    private final HoldingsReconstruction holdingsReconstruction;
    private final StockPriceHistoryRepository priceHistoryRepository;

    public PortfolioReturnSeries portfolioDailyReturns(Long userId, List<LocalDate> tradingDays) {
        List<DailyReturn> out = new ArrayList<>();
        TreeSet<String> excluded = new TreeSet<>();
        if (tradingDays.size() < 2) return new PortfolioReturnSeries(out, List.copyOf(excluded));

        LocalDate from = tradingDays.get(0).minusDays(7);
        LocalDate to = tradingDays.get(tradingDays.size() - 1);
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceMaps = new HashMap<>();

        for (int i = 1; i < tradingDays.size(); i++) {
            LocalDate prev = tradingDays.get(i - 1);
            LocalDate cur = tradingDays.get(i);
            Map<String, Integer> holdings = holdingsReconstruction.positionsAsOf(userId, prev);
            if (holdings.isEmpty()) {
                out.add(new DailyReturn(cur, 0.0));
                continue;
            }

            Map<String, Double> prevValue = new HashMap<>();
            Map<String, Double> symbolReturn = new HashMap<>();
            double totalPrev = 0.0;
            for (Map.Entry<String, Integer> h : holdings.entrySet()) {
                String symbol = h.getKey();
                int qty = h.getValue();
                NavigableMap<LocalDate, BigDecimal> closes =
                        priceMaps.computeIfAbsent(symbol, s -> loadCloses(s, from, to));
                Map.Entry<LocalDate, BigDecimal> ePrev = closes.floorEntry(prev);
                Map.Entry<LocalDate, BigDecimal> eCur = closes.floorEntry(cur);
                if (ePrev == null || eCur == null) {
                    excluded.add(symbol);
                    continue;
                }
                double p = ePrev.getValue().doubleValue();
                double c = eCur.getValue().doubleValue();
                if (p == 0.0) {
                    excluded.add(symbol);
                    continue;
                }
                double pv = qty * p;
                prevValue.put(symbol, pv);
                symbolReturn.put(symbol, c / p - 1.0);
                totalPrev += pv;
            }

            if (totalPrev == 0.0) {
                out.add(new DailyReturn(cur, 0.0));
                continue;
            }
            double r = 0.0;
            for (Map.Entry<String, Double> e : prevValue.entrySet()) {
                r += (e.getValue() / totalPrev) * symbolReturn.get(e.getKey());
            }
            out.add(new DailyReturn(cur, r));
        }
        return new PortfolioReturnSeries(out, List.copyOf(excluded));
    }

    public Map<String, List<DailyReturn>> perSymbolDailyReturns(Collection<String> symbols, List<LocalDate> tradingDays) {
        Map<String, List<DailyReturn>> result = new HashMap<>();
        if (tradingDays.size() < 2) return result;
        LocalDate from = tradingDays.get(0).minusDays(7);
        LocalDate to = tradingDays.get(tradingDays.size() - 1);
        for (String symbol : symbols) {
            result.put(symbol.toUpperCase(),
                    PriceSeries.dailyReturns(loadCloses(symbol.toUpperCase(), from, to), tradingDays));
        }
        return result;
    }

    private NavigableMap<LocalDate, BigDecimal> loadCloses(String symbol, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        List<StockPriceHistory> rows =
                priceHistoryRepository.findBySymbolAndDateBetweenOrderByDateAsc(symbol.toUpperCase(), from, to);
        for (StockPriceHistory h : rows) {
            map.put(h.getDate(), h.getClose());
        }
        return map;
    }
}
