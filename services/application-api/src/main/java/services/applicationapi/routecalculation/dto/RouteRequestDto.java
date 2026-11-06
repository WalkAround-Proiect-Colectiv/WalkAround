package services.applicationapi.routecalculation.dto;

public record RouteRequestDto(
        double startLat,
        double startLng,
        double endLat,
        double endLng
) {}
