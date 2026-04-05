package github.kaloyanov5.merkantil.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewsArticleResponse {
    private String id;
    private String title;
    private String author;
    private String description;
    private String articleUrl;
    private String imageUrl;
    private String publishedUtc;
    private NewsPublisherResponse publisher;
    private List<String> tickers;
    private List<String> keywords;
    private List<NewsInsightResponse> insights;
}
