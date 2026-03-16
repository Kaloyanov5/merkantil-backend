package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Represents the last quote (NBBO) data within a snapshot or from /v2/last/nbbo/{ticker}.
 */
@Data
public class MassiveLastQuote {
    @JsonProperty("P")
    private Double askPrice;

    @JsonProperty("S")
    private Integer askSize;

    @JsonProperty("p")
    private Double bidPrice;

    @JsonProperty("s")
    private Integer bidSize;

    @JsonProperty("t")
    private Long timestamp; // Nanosecond SIP timestamp
}

