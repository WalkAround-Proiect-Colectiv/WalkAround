package services.routingservice.routecalculation.service;

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

@Service
public class RouteService
{
    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final RestTemplate restTemplate;

    @Value("${app.valhalla.url}")
    private String valhallaUrl;
    public RouteService()
    {
        this.restTemplate = new RestTemplate();
    }

    public String calculatePedestrianRoute(double startLat, double startLng, double endLat, double endLng)
    {
        Map<String, Object> requestBody = Map.of(
                "locations", List.of(
                        Map.of("lat", startLat, "lon", startLng),
                        Map.of("lat", endLat, "lon", endLng)
                ),
                "costing", "pedestrian",
                "directions_options", Map.of("units", "kilometers")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(valhallaUrl, request, String.class);
        } catch (RestClientResponseException exception) {
            log.warn("Valhalla route request returned HTTP {}", exception.getStatusCode().value());
            throw exception;
        }
    }
}
