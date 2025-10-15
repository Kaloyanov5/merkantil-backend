package github.kaloyanov5.merkantil.dto.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AlpacaQuote {
    @JsonProperty("t")
    private String timestamp;

    @JsonProperty("ax")
    private String askExchange;

    @JsonProperty("ap")
    private Double askPrice;

    @JsonProperty("as")
    private Long askSize;

    @JsonProperty("bx")
    private String bidExchange;

    @JsonProperty("bp")
    private Double bidPrice;

    @JsonProperty("bs")
    private Long bidSize;

    @JsonProperty("c")
    private String[] conditions;
}