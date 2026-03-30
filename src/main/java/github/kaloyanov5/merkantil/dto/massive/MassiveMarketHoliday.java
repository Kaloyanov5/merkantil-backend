package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A single entry from GET /v1/marketstatus/upcoming.
 */
@Data
public class MassiveMarketHoliday {

    @JsonProperty("date")
    private String date; // "yyyy-MM-dd"

    @JsonProperty("exchange")
    private String exchange; // "NYSE", "NASDAQ", "OTC"

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private String status; // "closed" or "early-close"
}
