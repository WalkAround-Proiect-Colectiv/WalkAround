package services.applicationapi.routingproxy.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import services.applicationapi.routingproxy.dto.SavedRouteRequestDto;
import services.applicationapi.routingproxy.dto.SavedRouteResponseDto;

import java.util.List;

@RestController
@RequestMapping("/api/v1/saved-routes")
public class SavedRouteProxyController
{
    private static final Logger log = LoggerFactory.getLogger(SavedRouteProxyController.class);
    @Value("${app.routing-service.url}")
    private String routingServiceBaseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping
    public ResponseEntity<?> saveRoute(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody SavedRouteRequestDto request)
    {
        log.info("Proxying request to save a new route to routing-service");
        String url = routingServiceBaseUrl + "/api/v1/saved-routes";
        HttpEntity<SavedRouteRequestDto> entity = new HttpEntity<>(request, buildAuthHeaders(authHeader));
        try
        {
            ResponseEntity<SavedRouteResponseDto> response = restTemplate.exchange(url, HttpMethod.POST, entity, SavedRouteResponseDto.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
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
            log.error("Unexpected saved route proxy failure", exception);
            return internalError("Route could not be saved");
        }
    }

    @PutMapping("/{routeId}")
    public ResponseEntity<?> updateRoute(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long routeId,
            @RequestBody SavedRouteRequestDto request)
    {
        log.info("Proxying request to update route ID: {}", routeId);
        String url = routingServiceBaseUrl + "/api/v1/saved-routes/" + routeId;
        HttpEntity<SavedRouteRequestDto> entity = new HttpEntity<>(request, buildAuthHeaders(authHeader));
        try
        {
            ResponseEntity<SavedRouteResponseDto> response = restTemplate.exchange(
                    url, HttpMethod.PUT, entity, SavedRouteResponseDto.class);
            return ResponseEntity.ok(response.getBody());
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
            log.error("Unexpected saved route proxy failure", exception);
            return internalError("Route could not be updated");
        }
    }

    @GetMapping
    public ResponseEntity<?> getUserRoutes(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader)
    {
        log.info("Proxying request to fetch user routes from routing-service");
        String url = routingServiceBaseUrl + "/api/v1/saved-routes";
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(authHeader));
        try
        {
            ResponseEntity<List<SavedRouteResponseDto>> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
            return ResponseEntity.ok(response.getBody());
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
            log.error("Unexpected saved route proxy failure", exception);
            return internalError("Routes could not be retrieved");
        }
    }

    @DeleteMapping("/{routeId}")
    public ResponseEntity<?> deleteRoute(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long routeId)
    {
        log.info("Proxying request to delete route ID: {}", routeId);
        String url = routingServiceBaseUrl + "/api/v1/saved-routes/" + routeId;
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(authHeader));
        try
        {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            return ResponseEntity.noContent().build();
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
            log.error("Unexpected saved route proxy failure", exception);
            return internalError("Route could not be deleted");
        }
    }

    private HttpHeaders buildAuthHeaders(String authHeader)
    {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<ProblemDetail> unavailable()
    {
        HttpStatus status = HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, "Routing is temporarily unavailable"));
    }

    private ResponseEntity<ProblemDetail> internalError(String detail)
    {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
