package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.configuration.AnalyticsProperties;
import github.kaloyanov5.merkantil.dto.response.BenchmarkComparison;
import github.kaloyanov5.merkantil.dto.response.DataQuality;
import github.kaloyanov5.merkantil.dto.response.DiversificationMetrics;
import github.kaloyanov5.merkantil.dto.response.HoldingAnalytics;
import github.kaloyanov5.merkantil.dto.response.PortfolioAnalyticsResponse;
import github.kaloyanov5.merkantil.dto.response.ReturnSummary;
import github.kaloyanov5.merkantil.dto.response.RiskMetrics;
import github.kaloyanov5.merkantil.entity.Portfolio;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.PortfolioRepository;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import github.kaloyanov5.merkantil.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes proprietary portfolio risk, performance, benchmark and diversification
 * metrics from stored transactions and price history. All values are computed here
 * (no Massive calls); current prices come from the scheduler-maintained DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalyticsService {

    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository priceHistoryRepository;
    private final TransactionRepository transactionRepository;
    private final ReturnSeriesBuilder returnSeriesBuilder;
    private final BenchmarkService benchmarkService;
    private final MarketCalendar marketCalendar;
    private final AnalyticsProperties props;

    @Transactional(readOnly = true)
    public PortfolioAnalyticsResponse getAnalytics(Long userId, AnalyticsWindow window) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.minusDays(1);
        LocalDate startDate = window.startDate(today, earliestActivity(userId));
        if (startDate.isAfter(endDate)) startDate = endDate;
        List<LocalDate> tradingDays = tradingDaysInRange(startDate, endDate);

        List<Portfolio> holdings = portfolioRepository.findByUserId(userId);
        List<String> symbols = holdings.stream().map(p -> p.getSymbol().toUpperCase()).toList();

        Map<String, Stock> stockBySymbol = new HashMap<>();
        if (!symbols.isEmpty()) {
            for (Stock s : stockRepository.findBySymbolIn(symbols)) {
                stockBySymbol.put(s.getSymbol().toUpperCase(), s);
            }
        }

        PortfolioReturnSeries series = returnSeriesBuilder.portfolioDailyReturns(userId, tradingDays);
        double[] portReturns = toArray(series.dailyReturns());
        int dataPoints = portReturns.length;
        boolean lowConfidence = dataPoints < props.minObservations();

        double cumulative = FinancialMath.cumulativeReturn(portReturns);
        double annualized = FinancialMath.annualizeReturn(cumulative, dataPoints, props.tradingDaysPerYear());
        double volatility = FinancialMath.annualizedVolatility(portReturns, props.tradingDaysPerYear());
        Double sharpe = FinancialMath.sharpe(portReturns, props.riskFreeRate(), props.tradingDaysPerYear());
        Double sortino = FinancialMath.sortino(portReturns, props.riskFreeRate(), props.tradingDaysPerYear());
        double maxDrawdown = FinancialMath.maxDrawdown(portReturns);

        List<DailyReturn> benchSeries = benchmarkService.dailyReturns(tradingDays);
        BenchmarkComparison benchmark = buildBenchmark(series.dailyReturns(), benchSeries, annualized);

        Map<String, BigDecimal> priceBySymbol = new HashMap<>();
        for (String sym : symbols) priceBySymbol.put(sym, currentPrice(sym, stockBySymbol.get(sym)));
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Portfolio p : holdings) {
            BigDecimal price = priceBySymbol.get(p.getSymbol().toUpperCase());
            if (price != null) totalValue = totalValue.add(MoneyUtil.multiply(price, p.getQuantity()));
        }

        Map<String, List<DailyReturn>> perSymbol = returnSeriesBuilder.perSymbolDailyReturns(symbols, tradingDays);
        List<HoldingAnalytics> holdingAnalytics = buildHoldings(holdings, priceBySymbol, totalValue, perSymbol, benchSeries);
        DiversificationMetrics diversification = buildDiversification(holdings, priceBySymbol, totalValue, stockBySymbol);

        ReturnSummary returns = new ReturnSummary(
                MoneyUtil.scaled(totalValue), cumulative, annualized, moneyWeightedReturn(userId, totalValue));
        RiskMetrics risk = new RiskMetrics(volatility, sharpe, sortino, maxDrawdown);
        DataQuality dataQuality = new DataQuality(startDate, endDate, dataPoints, lowConfidence, series.excludedSymbols());

        return new PortfolioAnalyticsResponse(
                window.code(), returns, risk, benchmark, diversification, holdingAnalytics, dataQuality);
    }

    private BenchmarkComparison buildBenchmark(List<DailyReturn> port, List<DailyReturn> bench, double portAnnualized) {
        String symbol = benchmarkService.benchmarkSymbol();
        if (port.isEmpty() || bench.isEmpty()) {
            return new BenchmarkComparison(symbol, null, null, null, null, null);
        }
        Map<LocalDate, Double> benchByDate = new HashMap<>();
        for (DailyReturn d : bench) benchByDate.put(d.date(), d.value());
        List<Double> pa = new ArrayList<>();
        List<Double> ba = new ArrayList<>();
        for (DailyReturn d : port) {
            Double b = benchByDate.get(d.date());
            if (b != null) {
                pa.add(d.value());
                ba.add(b);
            }
        }
        if (pa.size() < 2) return new BenchmarkComparison(symbol, null, null, null, null, null);
        double[] p = toDouble(pa);
        double[] b = toDouble(ba);
        Double beta = FinancialMath.beta(p, b);
        Double corr = FinancialMath.correlation(p, b);
        Double rSquared = corr == null ? null : corr * corr;
        double benchCum = FinancialMath.cumulativeReturn(b);
        double benchAnn = FinancialMath.annualizeReturn(benchCum, b.length, props.tradingDaysPerYear());
        Double alpha = beta == null ? null
                : FinancialMath.jensensAlpha(portAnnualized, benchAnn, beta, props.riskFreeRate());
        double portCumAligned = FinancialMath.cumulativeReturn(p);
        return new BenchmarkComparison(symbol, beta, alpha, rSquared, benchCum, portCumAligned - benchCum);
    }

    private List<HoldingAnalytics> buildHoldings(List<Portfolio> holdings, Map<String, BigDecimal> priceBySymbol,
                                                 BigDecimal totalValue, Map<String, List<DailyReturn>> perSymbol,
                                                 List<DailyReturn> benchSeries) {
        Map<LocalDate, Double> benchByDate = new HashMap<>();
        for (DailyReturn d : benchSeries) benchByDate.put(d.date(), d.value());
        boolean hasTotal = MoneyUtil.isPositive(totalValue);

        List<HoldingAnalytics> out = new ArrayList<>();
        for (Portfolio p : holdings) {
            String sym = p.getSymbol().toUpperCase();
            BigDecimal price = priceBySymbol.get(sym);
            BigDecimal marketValue = price != null ? MoneyUtil.multiply(price, p.getQuantity()) : null;
            double weight = (marketValue != null && hasTotal)
                    ? marketValue.doubleValue() / totalValue.doubleValue() : 0.0;

            List<DailyReturn> sr = perSymbol.getOrDefault(sym, List.of());
            double[] arr = toArray(sr);
            Double vol = arr.length >= 2 ? FinancialMath.annualizedVolatility(arr, props.tradingDaysPerYear()) : null;

            Double beta = null;
            if (arr.length >= 2 && !benchByDate.isEmpty()) {
                List<Double> sa = new ArrayList<>();
                List<Double> ba = new ArrayList<>();
                for (DailyReturn d : sr) {
                    Double b = benchByDate.get(d.date());
                    if (b != null) {
                        sa.add(d.value());
                        ba.add(b);
                    }
                }
                if (sa.size() >= 2) beta = FinancialMath.beta(toDouble(sa), toDouble(ba));
            }

            Double contribution = arr.length >= 1 ? weight * FinancialMath.cumulativeReturn(arr) : null;
            BigDecimal unrealized = marketValue != null
                    ? marketValue.subtract(MoneyUtil.multiply(p.getAverageBuyPrice(), p.getQuantity())) : null;

            out.add(new HoldingAnalytics(sym, p.getQuantity(), MoneyUtil.scaled(marketValue),
                    weight, vol, beta, contribution, MoneyUtil.scaled(unrealized)));
        }
        return out;
    }

    private DiversificationMetrics buildDiversification(List<Portfolio> holdings, Map<String, BigDecimal> priceBySymbol,
                                                        BigDecimal totalValue, Map<String, Stock> stockBySymbol) {
        Map<String, Double> sector = new HashMap<>();
        List<Double> weights = new ArrayList<>();
        if (!MoneyUtil.isPositive(totalValue)) {
            return new DiversificationMetrics(sector, 0.0, 0.0, 0.0, 0.0);
        }
        double tv = totalValue.doubleValue();
        for (Portfolio p : holdings) {
            String sym = p.getSymbol().toUpperCase();
            BigDecimal price = priceBySymbol.get(sym);
            if (price == null) continue;
            double w = MoneyUtil.multiply(price, p.getQuantity()).doubleValue() / tv;
            weights.add(w);
            Stock s = stockBySymbol.get(sym);
            String sec = (s != null && s.getSector() != null && !s.getSector().isBlank()) ? s.getSector() : "Unknown";
            sector.merge(sec, w, Double::sum);
        }
        double hhi = 0.0;
        for (double w : weights) hhi += w * w;
        double effective = hhi > 0 ? 1.0 / hhi : 0.0;
        weights.sort(Comparator.reverseOrder());
        double top = weights.isEmpty() ? 0.0 : weights.get(0);
        double top3 = weights.stream().limit(3).mapToDouble(Double::doubleValue).sum();
        return new DiversificationMetrics(sector, hhi, effective, top, top3);
    }

    private Double moneyWeightedReturn(Long userId, BigDecimal currentValue) {
        List<Transaction> txns = transactionRepository.findByUserIdOrderByTimestampDesc(userId);
        if (txns.isEmpty()) return null;
        List<Transaction> asc = new ArrayList<>(txns);
        asc.sort(Comparator.comparing(Transaction::getTimestamp));
        LocalDate first = asc.get(0).getTimestamp().toLocalDate();
        double[] amounts = new double[asc.size() + 1];
        long[] offsets = new long[asc.size() + 1];
        for (int i = 0; i < asc.size(); i++) {
            Transaction t = asc.get(i);
            double amt = t.getTotalAmount().doubleValue();
            amounts[i] = t.getType() == Side.BUY ? -amt : amt;
            offsets[i] = ChronoUnit.DAYS.between(first, t.getTimestamp().toLocalDate());
        }
        amounts[asc.size()] = currentValue.doubleValue();
        offsets[asc.size()] = ChronoUnit.DAYS.between(first, LocalDate.now());
        return FinancialMath.xirr(amounts, offsets);
    }

    private BigDecimal currentPrice(String symbol, Stock stock) {
        if (stock != null && stock.getCurrentPrice() != null) return stock.getCurrentPrice();
        return priceHistoryRepository.findRecentHistory(symbol.toUpperCase(), 1).stream()
                .findFirst().map(StockPriceHistory::getClose).orElse(null);
    }

    private LocalDate earliestActivity(Long userId) {
        List<Transaction> txns = transactionRepository.findByUserIdOrderByTimestampDesc(userId);
        if (txns.isEmpty()) return null;
        return txns.get(txns.size() - 1).getTimestamp().toLocalDate();
    }

    private List<LocalDate> tradingDaysInRange(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            if (marketCalendar.isTradingDay(cur)) days.add(cur);
            cur = cur.plusDays(1);
        }
        return days;
    }

    private static double[] toArray(List<DailyReturn> returns) {
        double[] a = new double[returns.size()];
        for (int i = 0; i < returns.size(); i++) a[i] = returns.get(i).value();
        return a;
    }

    private static double[] toDouble(List<Double> values) {
        double[] a = new double[values.size()];
        for (int i = 0; i < values.size(); i++) a[i] = values.get(i);
        return a;
    }
}
