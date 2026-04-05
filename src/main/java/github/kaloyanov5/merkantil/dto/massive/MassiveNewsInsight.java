package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MassiveNewsInsight {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("sentiment")
    private String sentiment;

    @JsonProperty("sentiment_reasoning")
    private String sentimentReasoning;
}
