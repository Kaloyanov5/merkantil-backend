package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response wrapper for /v2/snapshot/locale/us/markets/stocks/tickers
 * Multiple tickers snapshot (batch). Also used for gainers/losers.
 */
public record MassiveMultiSnapshotResponse(
        @JsonProperty("status") String status,
        @JsonProperty("count") Integer count,
        @JsonProperty("tickers") List<MassiveSnapshotTicker> tickers
) {
}
