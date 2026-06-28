package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import github.kaloyanov5.merkantil.repository.StockPriceHistoryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnSeriesBuilderTest {

    @Mock private HoldingsReconstruction holdingsReconstruction;
    @Mock private StockPriceHistoryRepository priceHistoryRepository;

    private ReturnSeriesBuilder builder;

    private static final LocalDate D1 = LocalDate.of(2026, 5, 4);
    private static final LocalDate D2 = LocalDate.of(2026, 5, 5);
    private static final LocalDate D3 = LocalDate.of(2026, 5, 6);

    @BeforeEach
    void setUp() {
        builder = new ReturnSeriesBuilder(holdingsReconstruction, priceHistoryRepository);
    }

    private StockPriceHistory bar(String symbol, LocalDate date, double close) {
        StockPriceHistory h = new StockPriceHistory();
        h.setSymbol(symbol);
        h.setDate(date);
        h.setClose(BigDecimal.valueOf(close));
        h.setOpen(BigDecimal.valueOf(close));
        h.setHigh(BigDecimal.valueOf(close));
        h.setLow(BigDecimal.valueOf(close));
        return h;
    }

    private void stubHistory(String symbol, StockPriceHistory... bars) {
        when(priceHistoryRepository.findBySymbolAndDateBetweenOrderByDateAsc(
                eq(symbol), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ArrayList<>(List.of(bars)));
    }

    @Test
    @DisplayName("mid-window buy does NOT create a spurious return; weighting uses start-of-day value")
    void cashFlowIsolation() {
        when(holdingsReconstruction.positionsAsOf(anyLong(), eq(D1))).thenReturn(Map.of("AAPL", 10));
        when(holdingsReconstruction.positionsAsOf(anyLong(), eq(D2))).thenReturn(Map.of("AAPL", 10, "MSFT", 5));

        stubHistory("AAPL", bar("AAPL", D1, 100), bar("AAPL", D2, 110), bar("AAPL", D3, 121));
        stubHistory("MSFT", bar("MSFT", D2, 200), bar("MSFT", D3, 210));

        PortfolioReturnSeries series = builder.portfolioDailyReturns(1L, List.of(D1, D2, D3));
        List<DailyReturn> r = series.dailyReturns();

        assertThat(r).hasSize(2);
        assertThat(r.get(0).value()).isCloseTo(0.10, within(1e-9));
        assertThat(r.get(1).value()).isCloseTo(1100.0 / 2100 * 0.10 + 1000.0 / 2100 * 0.05, within(1e-9));
        assertThat(series.excludedSymbols()).isEmpty();
    }

    @Test
    @DisplayName("a symbol with no price data is excluded and its weight dropped")
    void excludesSymbolWithoutPrices() {
        when(holdingsReconstruction.positionsAsOf(anyLong(), any(LocalDate.class)))
                .thenReturn(Map.of("AAPL", 10, "GHOST", 7));
        stubHistory("AAPL", bar("AAPL", D1, 100), bar("AAPL", D2, 110));
        stubHistory("GHOST");

        PortfolioReturnSeries series = builder.portfolioDailyReturns(1L, List.of(D1, D2));

        assertThat(series.dailyReturns()).hasSize(1);
        assertThat(series.dailyReturns().get(0).value()).isCloseTo(0.10, within(1e-9));
        assertThat(series.excludedSymbols()).contains("GHOST");
    }

    @Test
    @DisplayName("per-symbol returns are simple price returns")
    void perSymbolReturns() {
        stubHistory("AAPL", bar("AAPL", D1, 100), bar("AAPL", D2, 110), bar("AAPL", D3, 121));
        Map<String, List<DailyReturn>> r = builder.perSymbolDailyReturns(List.of("AAPL"), List.of(D1, D2, D3));
        assertThat(r.get("AAPL")).hasSize(2);
        assertThat(r.get("AAPL").get(0).value()).isCloseTo(0.10, within(1e-9));
        assertThat(r.get("AAPL").get(1).value()).isCloseTo(0.10, within(1e-9));
    }
}
