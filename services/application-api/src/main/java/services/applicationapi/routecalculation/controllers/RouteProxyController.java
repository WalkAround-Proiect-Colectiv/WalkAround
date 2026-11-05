package services.applicationapi.routecalculation.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import services.applicationapi.routecalculation.dto.RouteRequestDto;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteProxyController {

    @Value("${app.routing-service.url}")
    private String routingServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/pedestrian")
    public ResponseEntity<String> getPedestrianRoute(@RequestBody RouteRequestDto request)
    {
        String url = routingServiceUrl + "/api/v1/routes/pedestrian";
        try
        {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        }
        catch (Exception e)
        {
            return ResponseEntity.internalServerError().body("{\"error\": \"Proxy error: Could not reach routing service\"}");
        }
    }
}
