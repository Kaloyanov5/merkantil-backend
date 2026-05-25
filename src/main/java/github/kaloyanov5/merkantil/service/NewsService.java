package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveNewsArticle;
import github.kaloyanov5.merkantil.dto.massive.MassiveNewsInsight;
import github.kaloyanov5.merkantil.dto.massive.MassiveNewsPublisher;
import github.kaloyanov5.merkantil.dto.massive.MassiveNewsResponse;
import github.kaloyanov5.merkantil.dto.response.NewsArticleResponse;
import github.kaloyanov5.merkantil.dto.response.NewsInsightResponse;
import github.kaloyanov5.merkantil.dto.response.NewsPublisherResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final MassiveApiService massiveApiService;

    @Cacheable(value = "news", key = "(#ticker ?: 'all') + '-' + #limit + '-' + #order + '-' + #sort")
    public List<NewsArticleResponse> getNews(String ticker, int limit, String order, String sort) {
        log.info("Fetching news from Massive API: ticker={}, limit={}", ticker, limit);

        MassiveNewsResponse response = massiveApiService.getNews(ticker, limit, order, sort);

        if (response == null || response.results() == null) {
            return Collections.emptyList();
        }

        return response.results().stream()
                .map(this::mapToArticleResponse)
                .toList();
    }

    private NewsArticleResponse mapToArticleResponse(MassiveNewsArticle article) {
        return new NewsArticleResponse(
                article.id(),
                article.title(),
                article.author(),
                article.description(),
                article.articleUrl(),
                article.imageUrl(),
                article.publishedUtc(),
                mapToPublisherResponse(article.publisher()),
                article.tickers(),
                article.keywords(),
                mapToInsightResponses(article.insights())
        );
    }

    private NewsPublisherResponse mapToPublisherResponse(MassiveNewsPublisher publisher) {
        if (publisher == null) return null;
        return new NewsPublisherResponse(
                publisher.name(),
                publisher.homepageUrl(),
                publisher.logoUrl(),
                publisher.faviconUrl()
        );
    }

    private List<NewsInsightResponse> mapToInsightResponses(List<MassiveNewsInsight> insights) {
        if (insights == null) return Collections.emptyList();
        return insights.stream()
                .map(i -> new NewsInsightResponse(i.ticker(), i.sentiment(), i.sentimentReasoning()))
                .toList();
    }
}
