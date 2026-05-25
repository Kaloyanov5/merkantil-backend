package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single ticker's snapshot data.
 * Contains day bar, previous day bar, minute bar, last trade, last quote, and change info.
 */
public record MassiveSnapshotTicker(
        @JsonProperty("ticker") String ticker,
        @JsonProperty("day") MassiveBar day,
        @JsonProperty("prevDay") MassiveBar prevDay,
        @JsonProperty("min") MassiveBar min,
        @JsonProperty("lastTrade") MassiveLastTrade lastTrade,
        @JsonProperty("lastQuote") MassiveLastQuote lastQuote,
        @JsonProperty("todaysChange") Double todaysChange,
        @JsonProperty("todaysChangePerc") Double todaysChangePerc,
        @JsonProperty("updated") Long updated,
        @JsonProperty("fmv") Double fmv
) {
}
