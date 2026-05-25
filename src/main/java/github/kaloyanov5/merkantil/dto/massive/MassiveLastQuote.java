package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the last quote (NBBO) data within a snapshot or from /v2/last/nbbo/{ticker}.
 */
public record MassiveLastQuote(
        @JsonProperty("P") Double askPrice,
        @JsonProperty("S") Integer askSize,
        @JsonProperty("p") Double bidPrice,
        @JsonProperty("s") Integer bidSize,
        @JsonProperty("t") Long timestamp // Nanosecond SIP timestamp
) {
}
