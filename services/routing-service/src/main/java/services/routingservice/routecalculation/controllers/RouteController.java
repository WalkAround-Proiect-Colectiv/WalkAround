package services.routingservice.routecalculation.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import services.routingservice.routecalculation.dto.RouteRequestDto;
import services.routingservice.routecalculation.service.RouteService;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);
    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/pedestrian")
    public ResponseEntity<String> getPedestrianRoute(@RequestBody RouteRequestDto request)
    {
        try {
            String valhallaResponse = routeService.calculatePedestrianRoute(request);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(valhallaResponse);
        }
        catch (Exception e)
        {
            log.error("Unexpected error while processing pedestrian route request", e);
            return ResponseEntity.internalServerError().body("{\"error\": \"Could not calculate route\"}");
        }
    }
}
