package github.kaloyanov5.merkantil.dto.response;

import java.util.List;

/** Top-level analytics payload for GET /api/portfolio/analytics. */
public record PortfolioAnalyticsResponse(
        String window,
        ReturnSummary returns,
        RiskMetrics risk,
        BenchmarkComparison benchmark,
        DiversificationMetrics diversification,
        List<HoldingAnalytics> holdings,
        DataQuality dataQuality
) {
}
