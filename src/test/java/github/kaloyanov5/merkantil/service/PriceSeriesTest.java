package github.kaloyanov5.merkantil.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PriceSeriesTest {

    private static final LocalDate D1 = LocalDate.of(2026, 5, 4);
    private static final LocalDate D2 = LocalDate.of(2026, 5, 5);
    private static final LocalDate D3 = LocalDate.of(2026, 5, 6);

    private NavigableMap<LocalDate, BigDecimal> closes(Object... pairs) {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((LocalDate) pairs[i], BigDecimal.valueOf((double) pairs[i + 1]));
        }
        return m;
    }

    @Test @DisplayName("consecutive closes produce simple daily returns")
    void simpleReturns() {
        List<DailyReturn> r = PriceSeries.dailyReturns(
                closes(D1, 100.0, D2, 110.0, D3, 121.0), List.of(D1, D2, D3));
        assertThat(r).hasSize(2);
        assertThat(r.get(0).date()).isEqualTo(D2);
        assertThat(r.get(0).value()).isCloseTo(0.10, within(1e-9));
        assertThat(r.get(1).value()).isCloseTo(0.10, within(1e-9));
    }

    @Test @DisplayName("a missing close falls back to the most recent prior close (0% that day)")
    void missingCloseFallsBack() {
        List<DailyReturn> r = PriceSeries.dailyReturns(
                closes(D1, 100.0, D3, 121.0), List.of(D1, D2, D3));
        assertThat(r).hasSize(2);
        assertThat(r.get(0).value()).isCloseTo(0.0, within(1e-9));
        assertThat(r.get(1).value()).isCloseTo(0.21, within(1e-9));
    }

    @Test @DisplayName("fewer than two trading days yields no returns")
    void tooFewDays() {
        assertThat(PriceSeries.dailyReturns(closes(D1, 100.0), List.of(D1))).isEmpty();
    }
}
