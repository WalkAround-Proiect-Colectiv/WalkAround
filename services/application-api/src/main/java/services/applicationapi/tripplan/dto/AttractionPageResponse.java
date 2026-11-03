package services.applicationapi.tripplan.dto;

import java.util.List;

public record AttractionPageResponse(List<AttractionResponse> items, Integer nextPage) {
}
