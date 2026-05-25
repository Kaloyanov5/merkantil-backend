package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response wrapper for /v3/reference/tickers (list all tickers).
 */
public record MassiveTickerListResponse(
        @JsonProperty("status") String status,
        @JsonProperty("count") Integer count,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("results") List<MassiveTickerDetail> results,
        @JsonProperty("next_url") String nextUrl
) {
}
