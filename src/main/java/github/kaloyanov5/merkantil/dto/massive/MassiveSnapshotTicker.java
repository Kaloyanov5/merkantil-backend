package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a single ticker's snapshot data.
 * Contains day bar, previous day bar, minute bar, last trade, last quote, and change info.
 */
@Data
public class MassiveSnapshotTicker {
    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("day")
    private MassiveBar day;

    @JsonProperty("prevDay")
    private MassiveBar prevDay;

    @JsonProperty("min")
    private MassiveBar min;

    @JsonProperty("lastTrade")
    private MassiveLastTrade lastTrade;

    @JsonProperty("lastQuote")
    private MassiveLastQuote lastQuote;

    @JsonProperty("todaysChange")
    private Double todaysChange;

    @JsonProperty("todaysChangePerc")
    private Double todaysChangePerc;

    @JsonProperty("updated")
    private Long updated;

    @JsonProperty("fmv")
    private Double fmv;
}

