package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response wrapper for /v2/aggs/ticker/{ticker}/range/... endpoints.
 * Contains historical OHLCV bars.
 */
public record MassiveAggregatesResponse(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("adjusted") Boolean adjusted,
        @JsonProperty("queryCount") Integer queryCount,
        @JsonProperty("resultsCount") Integer resultsCount,
        @JsonProperty("status") String status,
        @JsonProperty("results") List<MassiveBar> results,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("next_url") String nextUrl
) {
}
