package services.applicationapi.placesearch.dto;

import java.util.List;

public record NearbyPlacePage(List<NearbyPlaceResult> items, Integer nextPage) {}
