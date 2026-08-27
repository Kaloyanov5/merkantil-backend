package github.kaloyanov5.merkantil.marketdata.controller.dto.response;

public record NewsInsightResponse(
        String ticker,
        String sentiment,
        String sentimentReasoning
) {
}
