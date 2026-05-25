package github.kaloyanov5.merkantil.dto.massive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MassiveNewsArticle(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("author") String author,
        @JsonProperty("description") String description,
        @JsonProperty("article_url") String articleUrl,
        @JsonProperty("amp_url") String ampUrl,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("published_utc") String publishedUtc,
        @JsonProperty("publisher") MassiveNewsPublisher publisher,
        @JsonProperty("tickers") List<String> tickers,
        @JsonProperty("keywords") List<String> keywords,
        @JsonProperty("insights") List<MassiveNewsInsight> insights
) {
}
