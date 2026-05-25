package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the last trade data within a snapshot or from /v2/last/trade/{ticker}.
 */
public record MassiveLastTrade(
        @JsonProperty("p") Double price,
        @JsonProperty("s") Double size,
        @JsonProperty("t") Long timestamp, // Nanosecond SIP timestamp
        @JsonProperty("x") Integer exchange,
        @JsonProperty("i") String id,
        @JsonProperty("c") int[] conditions
) {
}
