package services.applicationapi.tripplan.dto;

import java.util.List;

public record NewsArticleResponse(
        String id,
        String title,
        String description,
        String url,
        String imageUrl,
        String author,
        String language,
        List<String> categories,
        String publishedAt
) {
}
