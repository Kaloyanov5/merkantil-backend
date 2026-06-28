package github.kaloyanov5.merkantil.service;

import java.time.LocalDate;

/** Supported analytics look-back windows and their start-date resolution. */
public enum AnalyticsWindow {
    ONE_MONTH("1M"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    YTD("YTD"),
    ALL("ALL");

    private final String code;

    AnalyticsWindow(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AnalyticsWindow fromCode(String code) {
        for (AnalyticsWindow w : values()) {
            if (w.code.equalsIgnoreCase(code)) return w;
        }
        throw new IllegalArgumentException("Unknown analytics window: " + code);
    }

    /** Inclusive start date relative to {@code today}; ALL uses {@code earliestActivity} (or 5y back). */
    public LocalDate startDate(LocalDate today, LocalDate earliestActivity) {
        return switch (this) {
            case ONE_MONTH -> today.minusMonths(1);
            case THREE_MONTHS -> today.minusMonths(3);
            case SIX_MONTHS -> today.minusMonths(6);
            case ONE_YEAR -> today.minusYears(1);
            case YTD -> LocalDate.of(today.getYear(), 1, 1);
            case ALL -> earliestActivity != null ? earliestActivity : today.minusYears(5);
        };
    }
}
