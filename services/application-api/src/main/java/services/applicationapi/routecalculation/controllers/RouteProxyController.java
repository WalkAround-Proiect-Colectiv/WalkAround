package services.applicationapi.routecalculation.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import services.applicationapi.routecalculation.dto.RouteRequestDto;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteProxyController {

    private static final Logger log = LoggerFactory.getLogger(RouteProxyController.class);

    @Value("${app.routing-service.url}")
    private String routingServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/pedestrian")
    public ResponseEntity<?> getPedestrianRoute(@RequestBody RouteRequestDto request)
    {
        String url = routingServiceUrl + "/api/v1/routes/pedestrian";
        try
        {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        }
        catch (RestClientResponseException exception)
        {
            log.warn("Routing service returned HTTP {}", exception.getStatusCode().value());
            return unavailable();
        }
        catch (RestClientException exception)
        {
            log.warn("Routing service request failed: {}", exception.getClass().getSimpleName());
            return unavailable();
        }
        catch (Exception exception)
        {
            log.error("Unexpected routing proxy failure", exception);
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status)
                    .body(ProblemDetail.forStatusAndDetail(status, "Route could not be retrieved"));
        }
    }

    private ResponseEntity<ProblemDetail> unavailable()
    {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, "Routing is temporarily unavailable"));
    }
}
