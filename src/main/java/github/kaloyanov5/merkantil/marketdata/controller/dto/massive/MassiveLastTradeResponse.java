package github.kaloyanov5.merkantil.marketdata.controller.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response wrapper for /v2/last/trade/{ticker}
 */
public record MassiveLastTradeResponse(
        @JsonProperty("status") String status,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("results") MassiveLastTrade results
) {
}
