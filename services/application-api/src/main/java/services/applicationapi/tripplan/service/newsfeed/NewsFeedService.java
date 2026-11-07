package services.applicationapi.tripplan.service.newsfeed;

import java.util.List;
import services.applicationapi.tripplan.dto.NewsArticleResponse;

public interface NewsFeedService {

    List<NewsArticleResponse> get(String city, String countryCode);
}
