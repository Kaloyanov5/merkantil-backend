package github.kaloyanov5.merkantil.dto.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AlpacaAsset {
    @JsonProperty("id")
    private String id;

    @JsonProperty("class")
    private String assetClass;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("name")
    private String name;

    @JsonProperty("status")
    private String status;

    @JsonProperty("tradable")
    private Boolean tradable;

    @JsonProperty("marginable")
    private Boolean marginable;

    @JsonProperty("shortable")
    private Boolean shortable;

    @JsonProperty("easy_to_borrow")
    private Boolean easyToBorrow;

    @JsonProperty("fractionable")
    private Boolean fractionable;
}