package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response wrapper for /v2/last/nbbo/{ticker}
 */
public record MassiveLastQuoteResponse(
        @JsonProperty("status") String status,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("results") MassiveLastQuote results
) {
}
