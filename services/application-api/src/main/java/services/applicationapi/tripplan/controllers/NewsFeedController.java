package services.applicationapi.tripplan.controllers;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.NewsArticleResponse;
import services.applicationapi.tripplan.service.newsfeed.NewsFeedService;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class NewsFeedController {

    private final NewsFeedService newsFeedService;

    public NewsFeedController(NewsFeedService newsFeedService) {
        this.newsFeedService = newsFeedService;
    }

    @GetMapping("/{city}/news")
    public ResponseEntity<List<NewsArticleResponse>> get(
            @PathVariable String city,
            @RequestParam String countryCode
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(newsFeedService.get(city, countryCode));
    }
}
