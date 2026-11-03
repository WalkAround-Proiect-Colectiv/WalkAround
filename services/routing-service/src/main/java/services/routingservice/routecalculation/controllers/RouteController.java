package services.routingservice.routecalculation.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.routingservice.routecalculation.dto.RouteRequestDto;
import services.routingservice.routecalculation.service.RouteService;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;
    public RouteController(RouteService routeService)
    {
        this.routeService = routeService;
    }

    @GetMapping("/pedestrian")
    public ResponseEntity<String> getPedestrianRoute(RouteRequestDto request)
    {
        try
        {
            String valhallaResponse = routeService.calculatePedestrianRoute(request.startLat(), request.startLng(), request.endLat(), request.endLng());
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(valhallaResponse);
        }
        catch (Exception e)
        {
            return ResponseEntity.internalServerError().body("{\"error\": \"Could not calculate route\"}");
        }
    }
}
