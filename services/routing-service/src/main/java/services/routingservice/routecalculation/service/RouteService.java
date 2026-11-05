package services.routingservice.routecalculation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RouteService
{
    private final RestTemplate restTemplate;

    @Value("${app.valhalla.url}")
    private String valhallaUrl;
    public RouteService()
    {
        this.restTemplate = new RestTemplate();
    }

    public String calculatePedestrianRoute(double startLat, double startLng, double endLat, double endLng)
    {
        String requestJson = String.format("""
            {
              "locations": [
                {"lat": %f, "lon": %f},
                {"lat": %f, "lon": %f}
              ],
              "costing": "pedestrian",
              "directions_options": {
                "units": "kilometers"
              }
            }
            """, startLat, startLng, endLat, endLng);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestJson, headers);
        return restTemplate.postForObject(valhallaUrl, request, String.class);
    }
}
