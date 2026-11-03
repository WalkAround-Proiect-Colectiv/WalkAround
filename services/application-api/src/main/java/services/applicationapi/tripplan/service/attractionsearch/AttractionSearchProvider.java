package services.applicationapi.tripplan.service.attractionsearch;

import java.math.BigDecimal;
import java.util.List;

public interface AttractionSearchProvider {

    List<Attraction> search(String city, String country);

    record Attraction(
            String name,
            String category,
            String address,
            String description,
            BigDecimal rating,
            Integer reviews,
            Double latitude,
            Double longitude,
            String mapsUrl,
            String imageUrl,
            String websiteUrl
    ) {
    }
}
