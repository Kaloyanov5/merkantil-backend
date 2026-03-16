package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Response wrapper for /v3/reference/tickers (list all tickers).
 */
@Data
public class MassiveTickerListResponse {
    @JsonProperty("status")
    private String status;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("results")
    private List<MassiveTickerDetail> results;

    @JsonProperty("next_url")
    private String nextUrl;
}

