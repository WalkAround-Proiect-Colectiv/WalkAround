package services.applicationapi.tripplan.service.newsfeed;

import java.util.List;
import services.applicationapi.tripplan.dto.NewsArticleResponse;

public interface NewsFeedProvider {

    List<NewsArticleResponse> search(String city, String countryCode);
}
