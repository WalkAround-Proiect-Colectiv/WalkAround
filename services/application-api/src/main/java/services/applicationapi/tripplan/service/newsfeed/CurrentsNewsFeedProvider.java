package services.applicationapi.tripplan.service.newsfeed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.tripplan.configurations.NewsFeedProperties;
import services.applicationapi.tripplan.dto.NewsArticleResponse;
import services.applicationapi.tripplan.service.exceptions.NewsFeedUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.news-provider", havingValue = "currents", matchIfMissing = true)
public class CurrentsNewsFeedProvider implements NewsFeedProvider {

    private static final Logger log = LoggerFactory.getLogger(CurrentsNewsFeedProvider.class);

    private final RestClient restClient;
    private final NewsFeedProperties properties;

    public CurrentsNewsFeedProvider(
            @Qualifier("currentsRestClient") RestClient restClient,
            NewsFeedProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<NewsArticleResponse> search(String city, String countryCode) {
        if (properties.currentsApiKey() == null || properties.currentsApiKey().isBlank()) {
            log.warn("Currents API key is not configured");
            throw new NewsFeedUnavailableException("News is temporarily unavailable");
        }

        SearchResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/v1/search")
                            .queryParam("keywords", "{city}")
                            .queryParam("language", "en")
                            .queryParam("country", countryCode)
                            .queryParam("page_size", properties.newsPageSize())
                            .build(Map.of("city", city)))
                    .header("Authorization", "Bearer " + properties.currentsApiKey())
                    .retrieve()
                    .body(SearchResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Currents API returned HTTP {}", exception.getStatusCode().value());
            throw new NewsFeedUnavailableException("News is temporarily unavailable", exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Currents API request failed: {}", exception.getClass().getSimpleName());
            throw new NewsFeedUnavailableException("News is temporarily unavailable", exception);
        }

        if (response == null || !"ok".equals(response.status()) || response.news() == null) {
            log.warn("Currents API returned an invalid news response");
            throw new NewsFeedUnavailableException("News is temporarily unavailable");
        }

        List<NewsArticleResponse> articles = response.news().stream()
                .filter(Objects::nonNull)
                .filter(article -> article.title() != null && !article.title().isBlank())
                .filter(article -> safeUrl(article.url()) != null)
                .map(article -> new NewsArticleResponse(
                        article.id(), article.title(), article.description(), safeUrl(article.url()),
                        safeUrl(article.image()), article.author(), article.language(),
                        article.category() == null ? List.of() : article.category().stream()
                                .filter(Objects::nonNull)
                                .toList(),
                        article.published()))
                .limit(properties.newsPageSize())
                .toList();
        log.debug("Currents API returned {} usable articles for {}", articles.size(), countryCode);
        return articles;
    }

    private String safeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null
                    ? uri.toString() : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(String status, List<Article> news) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Article(
            String id,
            String title,
            String description,
            String url,
            String image,
            String author,
            String language,
            List<String> category,
            String published
    ) {
    }
}
