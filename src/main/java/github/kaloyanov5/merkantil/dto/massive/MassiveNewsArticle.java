package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class MassiveNewsArticle {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("author")
    private String author;

    @JsonProperty("description")
    private String description;

    @JsonProperty("article_url")
    private String articleUrl;

    @JsonProperty("amp_url")
    private String ampUrl;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("published_utc")
    private String publishedUtc;

    @JsonProperty("publisher")
    private MassiveNewsPublisher publisher;

    @JsonProperty("tickers")
    private List<String> tickers;

    @JsonProperty("keywords")
    private List<String> keywords;

    @JsonProperty("insights")
    private List<MassiveNewsInsight> insights;
}
