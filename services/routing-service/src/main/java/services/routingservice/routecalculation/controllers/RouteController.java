package services.routingservice.routecalculation.controllers;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import services.routingservice.routecalculation.dto.RouteRequestDto;
import services.routingservice.routecalculation.service.RouteService;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/pedestrian")
    public ResponseEntity<String> getPedestrianRoute(@RequestBody RouteRequestDto request) {
        String valhallaResponse = routeService.calculatePedestrianRoute(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(valhallaResponse);
    }
}
