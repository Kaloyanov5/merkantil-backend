package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MassiveNewsResponse(
        @JsonProperty("status") String status,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("count") Integer count,
        @JsonProperty("next_url") String nextUrl,
        @JsonProperty("results") List<MassiveNewsArticle> results
) {
}
