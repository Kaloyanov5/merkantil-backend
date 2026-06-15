package github.kaloyanov5.merkantil.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics")
public record AnalyticsProperties(
        double riskFreeRate,
        String benchmarkSymbol,
        int tradingDaysPerYear,
        String defaultWindow,
        int minObservations
) {
    public AnalyticsProperties {
        if (benchmarkSymbol == null || benchmarkSymbol.isBlank()) benchmarkSymbol = "SPY";
        if (tradingDaysPerYear <= 0) tradingDaysPerYear = 252;
        if (defaultWindow == null || defaultWindow.isBlank()) defaultWindow = "3M";
        if (minObservations <= 0) minObservations = 20;
    }
}
