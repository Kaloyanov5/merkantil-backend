package github.kaloyanov5.merkantil.dto.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AlpacaBar {
    @JsonProperty("t")
    private String timestamp;

    @JsonProperty("o")
    private Double open;

    @JsonProperty("h")
    private Double high;

    @JsonProperty("l")
    private Double low;

    @JsonProperty("c")
    private Double close;

    @JsonProperty("v")
    private Long volume;

    @JsonProperty("n")
    private Long tradeCount;

    @JsonProperty("vw")
    private Double vwap;  // Volume-weighted average price
}