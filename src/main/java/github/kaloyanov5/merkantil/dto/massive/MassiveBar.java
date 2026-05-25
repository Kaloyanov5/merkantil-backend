package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single OHLCV bar from Massive.com aggregate endpoints.
 * Used in /v2/aggs/ticker/{ticker}/range/... responses.
 */
public record MassiveBar(
        @JsonProperty("o") Double open,
        @JsonProperty("h") Double high,
        @JsonProperty("l") Double low,
        @JsonProperty("c") Double close,
        @JsonProperty("v") Long volume,
        @JsonProperty("vw") Double vwap,
        @JsonProperty("t") Long timestamp, // Unix milliseconds
        @JsonProperty("n") Integer tradeCount,
        @JsonProperty("T") String ticker // Present in grouped daily responses
) {
}
