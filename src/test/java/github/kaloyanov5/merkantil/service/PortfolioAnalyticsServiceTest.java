package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.configuration.AnalyticsProperties;
import github.kaloyanov5.merkantil.dto.response.PortfolioAnalyticsResponse;
import github.kaloyanov5.merkantil.entity.Portfolio;
import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.entity.Transaction;
import github.kaloyanov5.merkantil.repository.PortfolioRepository;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.repository.TransactionRepository;
import github.kaloyanov5.merkantil.util.MarketCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioAnalyticsServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockPriceHistoryRepository priceHistoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ReturnSeriesBuilder returnSeriesBuilder;
    @Mock private BenchmarkService benchmarkService;
    @Mock private MarketCalendar marketCalendar;

    private PortfolioAnalyticsService service;

    private static final LocalDate D2 = LocalDate.of(2026, 5, 5);
    private static final LocalDate D3 = LocalDate.of(2026, 5, 6);

    @BeforeEach
    void setUp() {
        AnalyticsProperties props = new AnalyticsProperties(0.04, "SPY", 252, "3M", 2);
        service = new PortfolioAnalyticsService(portfolioRepository, stockRepository, priceHistoryRepository,
                transactionRepository, returnSeriesBuilder, benchmarkService, marketCalendar, props);
        when(marketCalendar.isTradingDay(any(LocalDate.class))).thenReturn(true);
        when(benchmarkService.benchmarkSymbol()).thenReturn("SPY");
    }

    private Portfolio holding(String symbol, int qty, String avg) {
        Portfolio p = new Portfolio();
        p.setSymbol(symbol);
        p.setQuantity(qty);
        p.setAverageBuyPrice(new BigDecimal(avg));
        return p;
    }

    private Stock stock(String symbol, String sector, String price) {
        Stock s = new Stock();
        s.setSymbol(symbol);
        s.setSector(sector);
        s.setCurrentPrice(new BigDecimal(price));
        return s;
    }

    private Transaction buy(String symbol, int qty, String total, LocalDate date) {
        Transaction t = new Transaction();
        t.setStockSymbol(symbol);
        t.setType(Side.BUY);
        t.setQuantity(qty);
        t.setPrice(new BigDecimal("100.00"));
        t.setTotalAmount(new BigDecimal(total));
        t.setTimestamp(date.atTime(LocalTime.NOON));
        return t;
    }

    @Test
    @DisplayName("empty portfolio: 200 with zeroed metrics and dataPoints 0")
    void emptyPortfolio() {
        when(portfolioRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(transactionRepository.findByUserIdOrderByTimestampDesc(anyLong())).thenReturn(List.of());
        when(returnSeriesBuilder.portfolioDailyReturns(anyLong(), anyList()))
                .thenReturn(new PortfolioReturnSeries(List.of(), List.of()));
        when(returnSeriesBuilder.perSymbolDailyReturns(any(), anyList())).thenReturn(Map.of());
        when(benchmarkService.dailyReturns(anyList())).thenReturn(List.of());

        PortfolioAnalyticsResponse r = service.getAnalytics(1L, AnalyticsWindow.THREE_MONTHS);

        assertThat(r.window()).isEqualTo("3M");
        assertThat(r.dataQuality().dataPoints()).isZero();
        assertThat(r.dataQuality().lowConfidence()).isTrue();
        assertThat(r.returns().totalValue()).isEqualByComparingTo("0");
        assertThat(r.returns().moneyWeightedReturn()).isNull();
        assertThat(r.holdings()).isEmpty();
        assertThat(r.benchmark().benchmarkSymbol()).isEqualTo("SPY");
        assertThat(r.benchmark().beta()).isNull();
        assertThat(r.diversification().herfindahlIndex()).isZero();
    }

    @Test
    @DisplayName("single holding: value, weight, sector, benchmark beta wired through")
    void singleHolding() {
        when(portfolioRepository.findByUserId(anyLong())).thenReturn(List.of(holding("AAPL", 10, "100")));
        when(stockRepository.findBySymbolIn(anyList())).thenReturn(List.of(stock("AAPL", "Technology", "130")));
        when(transactionRepository.findByUserIdOrderByTimestampDesc(anyLong()))
                .thenReturn(List.of(buy("AAPL", 10, "1000", LocalDate.of(2026, 4, 1))));
        when(returnSeriesBuilder.portfolioDailyReturns(anyLong(), anyList()))
                .thenReturn(new PortfolioReturnSeries(List.of(new DailyReturn(D2, 0.10), new DailyReturn(D3, 0.05)), List.of()));
        when(returnSeriesBuilder.perSymbolDailyReturns(any(), anyList()))
                .thenReturn(Map.of("AAPL", List.of(new DailyReturn(D2, 0.10), new DailyReturn(D3, 0.05))));
        when(benchmarkService.dailyReturns(anyList()))
                .thenReturn(List.of(new DailyReturn(D2, 0.08), new DailyReturn(D3, 0.04)));

        PortfolioAnalyticsResponse r = service.getAnalytics(1L, AnalyticsWindow.THREE_MONTHS);

        assertThat(r.dataQuality().dataPoints()).isEqualTo(2);
        assertThat(r.dataQuality().lowConfidence()).isFalse();
        assertThat(r.returns().totalValue()).isEqualByComparingTo("1300");
        assertThat(r.holdings()).hasSize(1);
        assertThat(r.holdings().get(0).symbol()).isEqualTo("AAPL");
        assertThat(r.holdings().get(0).weight()).isEqualTo(1.0);
        assertThat(r.holdings().get(0).unrealizedGain()).isEqualByComparingTo("300");
        assertThat(r.diversification().sectorAllocation()).containsKey("Technology");
        assertThat(r.benchmark().beta()).isNotNull();
    }
}
