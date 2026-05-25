package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response wrapper for single ticker snapshot endpoint.
 */
public record MassiveSnapshotResponse(
        @JsonProperty("ticker") MassiveSnapshotTicker ticker,
        @JsonProperty("status") String status,
        @JsonProperty("request_id") String requestId
) {
}
