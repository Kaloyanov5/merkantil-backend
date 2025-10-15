package github.kaloyanov5.merkantil.dto.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AlpacaTrade {
    @JsonProperty("t")
    private String timestamp;

    @JsonProperty("x")
    private String exchange;

    @JsonProperty("p")
    private Double price;

    @JsonProperty("s")
    private Long size;

    @JsonProperty("c")
    private String[] conditions;

    @JsonProperty("i")
    private Long id;

    @JsonProperty("z")
    private String tape;
}