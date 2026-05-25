package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response from /v1/marketstatus/now
 */
public record MassiveMarketStatusResponse(
        @JsonProperty("market") String market, // "open", "closed", "extended-hours"
        @JsonProperty("earlyHours") Boolean earlyHours,
        @JsonProperty("afterHours") Boolean afterHours,
        @JsonProperty("exchanges") Map<String, String> exchanges, // { "nasdaq": "open", "nyse": "open", "otc": "closed" }
        @JsonProperty("currencies") Map<String, String> currencies,
        @JsonProperty("serverTime") String serverTime
) {
}
