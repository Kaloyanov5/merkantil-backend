package github.kaloyanov5.merkantil.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Test @DisplayName("fromCode is case-insensitive")
    void fromCode() {
        assertThat(AnalyticsWindow.fromCode("3m")).isEqualTo(AnalyticsWindow.THREE_MONTHS);
        assertThat(AnalyticsWindow.fromCode("YTD")).isEqualTo(AnalyticsWindow.YTD);
    }

    @Test @DisplayName("fromCode throws on unknown code")
    void fromCodeUnknown() {
        assertThatThrownBy(() -> AnalyticsWindow.fromCode("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("relative windows subtract from today")
    void relativeWindows() {
        assertThat(AnalyticsWindow.THREE_MONTHS.startDate(TODAY, null)).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(AnalyticsWindow.ONE_YEAR.startDate(TODAY, null)).isEqualTo(LocalDate.of(2025, 6, 15));
    }

    @Test @DisplayName("YTD starts on Jan 1 of the current year")
    void ytd() {
        assertThat(AnalyticsWindow.YTD.startDate(TODAY, null)).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test @DisplayName("ALL uses earliest activity, or 5y back when none")
    void all() {
        LocalDate earliest = LocalDate.of(2024, 2, 1);
        assertThat(AnalyticsWindow.ALL.startDate(TODAY, earliest)).isEqualTo(earliest);
        assertThat(AnalyticsWindow.ALL.startDate(TODAY, null)).isEqualTo(LocalDate.of(2021, 6, 15));
    }
}
