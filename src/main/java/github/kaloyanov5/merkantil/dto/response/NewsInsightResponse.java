package github.kaloyanov5.merkantil.dto.response;

public record NewsInsightResponse(
        String ticker,
        String sentiment,
        String sentimentReasoning
) {
}
