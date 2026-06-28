package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.configuration.AnalyticsProperties;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkServiceTest {

    @Mock private StockPriceHistoryRepository priceHistoryRepository;
    @Mock private StockPriceScheduler stockPriceScheduler;

    private BenchmarkService service;

    private static final LocalDate D1 = LocalDate.of(2026, 5, 4);
    private static final LocalDate D2 = LocalDate.of(2026, 5, 5);
    private static final LocalDate D3 = LocalDate.of(2026, 5, 6);

    @BeforeEach
    void setUp() {
        AnalyticsProperties props = new AnalyticsProperties(0.04, "SPY", 252, "3M", 20);
        service = new BenchmarkService(priceHistoryRepository, stockPriceScheduler, props);
    }

    private StockPriceHistory bar(LocalDate date, double close) {
        StockPriceHistory h = new StockPriceHistory();
        h.setSymbol("SPY");
        h.setDate(date);
        h.setClose(BigDecimal.valueOf(close));
        h.setOpen(BigDecimal.valueOf(close));
        h.setHigh(BigDecimal.valueOf(close));
        h.setLow(BigDecimal.valueOf(close));
        return h;
    }

    @Test
    @DisplayName("when coverage exists, no backfill and returns are computed")
    void coverageExists_noBackfill() {
        when(priceHistoryRepository.findMostRecentPriceOnOrBefore(eq("SPY"), any(LocalDate.class)))
                .thenReturn(Optional.of(bar(D1, 400)));
        when(priceHistoryRepository.findBySymbolAndDate(eq("SPY"), any(LocalDate.class)))
                .thenReturn(Optional.of(bar(D3, 420)));
        when(priceHistoryRepository.findBySymbolAndDateBetweenOrderByDateAsc(eq("SPY"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(bar(D1, 400), bar(D2, 404), bar(D3, 420)));

        List<DailyReturn> r = service.dailyReturns(List.of(D1, D2, D3));

        assertThat(r).hasSize(2);
        assertThat(r.get(0).value()).isCloseTo(404.0 / 400 - 1, within(1e-9));
        verify(stockPriceScheduler, never()).backfillStockHistory(anyString(), any(), any());
    }

    @Test
    @DisplayName("when coverage is missing, backfill is triggered")
    void coverageMissing_seeds() {
        when(priceHistoryRepository.findMostRecentPriceOnOrBefore(eq("SPY"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(priceHistoryRepository.findBySymbolAndDate(eq("SPY"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(stockPriceScheduler.backfillStockHistory(eq("SPY"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);
        when(priceHistoryRepository.findBySymbolAndDateBetweenOrderByDateAsc(eq("SPY"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        List<DailyReturn> r = service.dailyReturns(List.of(D1, D2, D3));

        assertThat(r).isEmpty();
        verify(stockPriceScheduler, times(1)).backfillStockHistory(eq("SPY"), any(LocalDate.class), any(LocalDate.class));
    }
}
