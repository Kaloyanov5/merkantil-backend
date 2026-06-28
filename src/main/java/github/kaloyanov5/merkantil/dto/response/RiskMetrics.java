package github.kaloyanov5.merkantil.dto.response;

/** Risk statistics. Ratios are null when undefined (e.g. zero volatility). */
public record RiskMetrics(
        double annualizedVolatility,
        Double sharpeRatio,
        Double sortinoRatio,
        double maxDrawdown
) {
}
