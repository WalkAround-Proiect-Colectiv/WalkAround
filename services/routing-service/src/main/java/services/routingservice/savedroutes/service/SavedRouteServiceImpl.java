package services.routingservice.savedroutes.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.routingservice.savedroutes.dto.SavedRouteRequestDto;
import services.routingservice.savedroutes.dto.SavedRouteResponseDto;
import services.routingservice.savedroutes.model.SavedRoute;
import services.routingservice.savedroutes.repository.SavedRouteRepository;
import services.routingservice.savedroutes.service.SavedRouteService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SavedRouteServiceImpl implements SavedRouteService
{
    private final SavedRouteRepository savedRouteRepository;
    public SavedRouteServiceImpl(SavedRouteRepository savedRouteRepository)
    {
        this.savedRouteRepository = savedRouteRepository;
    }
    @Override
    @Transactional
    public SavedRouteResponseDto saveRoute(String userId, SavedRouteRequestDto request)
    {
        log.info("Saving new route for user: {}", userId);
        SavedRoute route = new SavedRoute();
        route.setUserId(userId);
        route.setStartLat(request.startLat());
        route.setStartLng(request.startLng());
        route.setEndLat(request.endLat());
        route.setEndLng(request.endLng());
        SavedRoute savedRoute = savedRouteRepository.save(route);
        log.debug("Successfully saved route with ID: {} for user: {}", savedRoute.getId(), userId);
        return mapToResponseDto(savedRoute);
    }

    @Override
    @Transactional
    public SavedRouteResponseDto updateRoute(String userId, Long routeId, SavedRouteRequestDto request) {
        log.info("Updating route ID: {} for user: {}", routeId, userId);
        SavedRoute route = savedRouteRepository.findByIdAndUserId(routeId, userId)
                .orElseThrow(() -> {
                    log.error("Route ID: {} not found or does not belong to user: {}", routeId, userId);
                    return new RuntimeException("Route not found or access denied");
                });
        route.setStartLat(request.startLat());
        route.setStartLng(request.startLng());
        route.setEndLat(request.endLat());
        route.setEndLng(request.endLng());
        SavedRoute updatedRoute = savedRouteRepository.save(route);
        log.debug("Successfully updated route ID: {}", routeId);
        return mapToResponseDto(updatedRoute);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedRouteResponseDto> getUserSavedRoutes(String userId)
    {
        log.info("Fetching saved routes for user: {}", userId);
        return savedRouteRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteRoute(String userId, Long routeId)
    {
        log.info("Attempting to delete route ID: {} for user: {}", routeId, userId);
        SavedRoute route = savedRouteRepository.findByIdAndUserId(routeId, userId)
                .orElseThrow(() -> {
                    log.error("Route ID: {} not found or does not belong to user: {}", routeId, userId);
                    return new RuntimeException("Route not found or access denied");
                });
        savedRouteRepository.delete(route);
        log.debug("Successfully deleted route ID: {}", routeId);
    }

    private SavedRouteResponseDto mapToResponseDto(SavedRoute route)
    {
        return new SavedRouteResponseDto(
                route.getId(),
                route.getStartLat(),
                route.getStartLng(),
                route.getEndLat(),
                route.getEndLng(),
                route.getCreatedAt()
        );
    }
}
