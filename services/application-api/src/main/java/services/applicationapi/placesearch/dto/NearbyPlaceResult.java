package services.applicationapi.placesearch.dto;

public record NearbyPlaceResult(
        String id,
        String name,
        double latitude,
        double longitude,
        double distanceMeters
) {}
