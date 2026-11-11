package services.routingservice.routecalculation.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import services.routingservice.routecalculation.dto.PointDto;
import services.routingservice.routecalculation.dto.RouteRequestDto;

@Service
public class RouteService
{
    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final RestTemplate restTemplate;

    @Value("${app.valhalla.url}")
    private String valhallaUrl;

    @Value("${app.valhalla.language:en-US}")
    private String valhallaLanguage;

    public RouteService()
    {
        this.restTemplate = new RestTemplate();
    }

    public String calculatePedestrianRoute(RouteRequestDto request) {
        log.info("Calculating pedestrian route with {} stops", request.stops().size());
        log.debug("Route from ({}, {}) to ({}, {})",
                request.startLat(), request.startLng(), request.endLat(), request.endLng());

        List<Map<String, Object>> locations = new ArrayList<>();

        locations.add(
                location(request.startLat(), request.startLng())
        );

        for (PointDto stop : request.stops()) {
            locations.add(
                    location(stop.lat(), stop.lng())
            );
        }

        locations.add(
                location(request.endLat(), request.endLng())
        );

        Map<String, Object> requestBody = Map.of(
                "locations", locations,
                "costing", "pedestrian",
                "directions_options", Map.of(
                        "units", "kilometers",
                        "language", valhallaLanguage
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(valhallaUrl, httpEntity, String.class);
        } catch (RestClientResponseException exception) {
            log.warn("Valhalla route request returned HTTP {}", exception.getStatusCode().value());
            throw exception;
        }
    }

    private static Map<String, Object> location(double lat, double lng) {
        return Map.of("lat", lat, "lon", lng);
    }
}
