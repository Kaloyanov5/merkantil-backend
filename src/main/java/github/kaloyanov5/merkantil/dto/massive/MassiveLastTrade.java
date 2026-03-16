package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents the last trade data within a snapshot or from /v2/last/trade/{ticker}.
 */
@Data
public class MassiveLastTrade {
    @JsonProperty("p")
    private Double price;

    @JsonProperty("s")
    private Double size;

    @JsonProperty("t")
    private Long timestamp; // Nanosecond SIP timestamp

    @JsonProperty("x")
    private Integer exchange;

    @JsonProperty("i")
    private String id;

    @JsonProperty("c")
    private int[] conditions;
}

