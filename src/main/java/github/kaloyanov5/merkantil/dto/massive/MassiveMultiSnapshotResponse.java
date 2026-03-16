package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Response wrapper for /v2/snapshot/locale/us/markets/stocks/tickers
 * Multiple tickers snapshot (batch). Also used for gainers/losers.
 */
@Data
public class MassiveMultiSnapshotResponse {
    @JsonProperty("status")
    private String status;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("tickers")
    private List<MassiveSnapshotTicker> tickers;
}

