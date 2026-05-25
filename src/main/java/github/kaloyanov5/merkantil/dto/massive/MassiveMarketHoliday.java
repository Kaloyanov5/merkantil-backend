package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single entry from GET /v1/marketstatus/upcoming.
 */
public record MassiveMarketHoliday(
        @JsonProperty("date") String date, // "yyyy-MM-dd"
        @JsonProperty("exchange") String exchange, // "NYSE", "NASDAQ", "OTC"
        @JsonProperty("name") String name,
        @JsonProperty("status") String status // "closed" or "early-close"
) {
}
