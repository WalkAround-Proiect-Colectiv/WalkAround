package services.applicationapi.placesearch.dto;

public record PlaceSearchResult(
        String name,
        String detail,
        double latitude,
        double longitude
) {
}
