package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response wrapper for /v3/reference/tickers/{ticker} (single ticker detail).
 */
@Data
public class MassiveTickerResponse {
    @JsonProperty("status")
    private String status;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("results")
    private MassiveTickerDetail results;
}

