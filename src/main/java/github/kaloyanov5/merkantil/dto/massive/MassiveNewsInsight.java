package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MassiveNewsInsight(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("sentiment") String sentiment,
        @JsonProperty("sentiment_reasoning") String sentimentReasoning
) {
}
