package services.routingservice.routecalculation.service;

import services.routingservice.routecalculation.dto.RouteRequestDto;

public interface RouteService {
    String calculatePedestrianRoute(RouteRequestDto request);
}
