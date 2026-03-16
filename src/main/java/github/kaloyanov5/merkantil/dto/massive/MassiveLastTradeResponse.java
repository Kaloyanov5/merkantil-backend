package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response wrapper for /v2/last/trade/{ticker}
 */
@Data
public class MassiveLastTradeResponse {
    @JsonProperty("status")
    private String status;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("results")
    private MassiveLastTrade results;
}

