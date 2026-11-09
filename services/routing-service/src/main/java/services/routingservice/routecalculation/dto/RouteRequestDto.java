package services.routingservice.routecalculation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public record RouteRequestDto(
        @NotNull PointDto start,
        @NotNull PointDto end,
        List<PointDto> stops,
        boolean avoidDanger,
        ZonedDateTime departureTime
) {
    public RouteRequestDto {
        stops = (stops == null) ? List.of() : List.copyOf(stops);
    }

    public List<PointDto> allPoints() {
        List<PointDto> all = new ArrayList<>(stops.size() + 2);
        all.add(start);
        all.addAll(stops);
        all.add(end);
        return all;
    }
}