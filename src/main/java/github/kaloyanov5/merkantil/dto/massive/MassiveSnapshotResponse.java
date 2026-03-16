package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response wrapper for single ticker snapshot endpoint.
 */
@Data
public class MassiveSnapshotResponse {
    @JsonProperty("ticker")
    private MassiveSnapshotTicker ticker;

    @JsonProperty("status")
    private String status;

    @JsonProperty("request_id")
    private String requestId;
}

