package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MassiveNewsResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("next_url")
    private String nextUrl;

    @JsonProperty("results")
    private List<MassiveNewsArticle> results;
}
