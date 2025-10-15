package github.kaloyanov5.merkantil.dto.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AlpacaSnapshot {
    @JsonProperty("latestTrade")
    private AlpacaTrade latestTrade;

    @JsonProperty("latestQuote")
    private AlpacaQuote latestQuote;

    @JsonProperty("minuteBar")
    private AlpacaBar minuteBar;

    @JsonProperty("dailyBar")
    private AlpacaBar dailyBar;

    @JsonProperty("prevDailyBar")
    private AlpacaBar prevDailyBar;
}