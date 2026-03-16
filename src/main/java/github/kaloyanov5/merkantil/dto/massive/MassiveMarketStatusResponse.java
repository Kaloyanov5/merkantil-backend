package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Response from /v1/marketstatus/now
 */
@Data
public class MassiveMarketStatusResponse {
    @JsonProperty("market")
    private String market; // "open", "closed", "extended-hours"

    @JsonProperty("earlyHours")
    private Boolean earlyHours;

    @JsonProperty("afterHours")
    private Boolean afterHours;

    @JsonProperty("exchanges")
    private Map<String, String> exchanges; // { "nasdaq": "open", "nyse": "open", "otc": "closed" }

    @JsonProperty("currencies")
    private Map<String, String> currencies;

    @JsonProperty("serverTime")
    private String serverTime;
}

