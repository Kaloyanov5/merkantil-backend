package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Response wrapper for /v2/aggs/ticker/{ticker}/range/... endpoints.
 * Contains historical OHLCV bars.
 */
@Data
public class MassiveAggregatesResponse {
    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("adjusted")
    private Boolean adjusted;

    @JsonProperty("queryCount")
    private Integer queryCount;

    @JsonProperty("resultsCount")
    private Integer resultsCount;

    @JsonProperty("status")
    private String status;

    @JsonProperty("results")
    private List<MassiveBar> results;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("next_url")
    private String nextUrl;
}

