package services.routingservice.savedroutes.service;

import services.routingservice.savedroutes.dto.SavedRouteRequestDto;
import services.routingservice.savedroutes.dto.SavedRouteResponseDto;

import java.util.List;

public interface SavedRouteService
{
    SavedRouteResponseDto saveRoute(String userId, SavedRouteRequestDto request);
    SavedRouteResponseDto updateRoute(String userId, Long routeId, SavedRouteRequestDto request);
    List<SavedRouteResponseDto> getUserSavedRoutes(String userId);
    void deleteRoute(String userId, Long routeId);
}
