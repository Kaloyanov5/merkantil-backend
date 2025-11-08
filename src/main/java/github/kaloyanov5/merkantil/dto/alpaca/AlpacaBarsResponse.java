package github.kaloyanov5.merkantil.dto.alpaca;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AlpacaBarsResponse {
    @JsonProperty("bars")
    private List<AlpacaBar> bars;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("next_page_token")
    private String nextPageToken;
}