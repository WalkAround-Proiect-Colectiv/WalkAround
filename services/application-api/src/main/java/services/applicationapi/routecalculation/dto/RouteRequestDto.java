package services.applicationapi.routecalculation.dto;

import java.util.List;

public record RouteRequestDto(
        double startLat,
        double startLng,
        List<PointDto> stops,
        double endLat,
        double endLng
) {
    public RouteRequestDto {
        stops = (stops == null) ? List.of() : List.copyOf(stops);
    }
}

