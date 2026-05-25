package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response wrapper for /v3/reference/tickers/{ticker} (single ticker detail).
 */
public record MassiveTickerResponse(
        @JsonProperty("status") String status,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("results") MassiveTickerDetail results
) {
}
