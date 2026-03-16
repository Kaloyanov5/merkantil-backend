package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents a single OHLCV bar from Massive.com aggregate endpoints.
 * Used in /v2/aggs/ticker/{ticker}/range/... responses.
 */
@Data
public class MassiveBar {
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

    @JsonProperty("vw")
    private Double vwap;

    @JsonProperty("t")
    private Long timestamp; // Unix milliseconds

    @JsonProperty("n")
    private Integer tradeCount;

    @JsonProperty("T")
    private String ticker; // Present in grouped daily responses
}
