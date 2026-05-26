package github.kaloyanov5.merkantil.dto.response;

import java.util.List;

public record NewsArticleResponse(
        String id,
        String title,
        String author,
        String description,
        String articleUrl,
        String imageUrl,
        String publishedUtc,
        NewsPublisherResponse publisher,
        List<String> tickers,
        List<String> keywords,
        List<NewsInsightResponse> insights
) {
}
