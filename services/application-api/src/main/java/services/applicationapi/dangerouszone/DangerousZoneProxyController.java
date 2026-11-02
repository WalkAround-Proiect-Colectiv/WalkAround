package services.applicationapi.dangerouszone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/dangerous-zones")
public class DangerousZoneProxyController
{
    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${ROUTING_SERVICE_URL:http://localhost:8002}")
    private String routingServiceUrl;

    @PostMapping
    public ResponseEntity<Void> forwardReport(@RequestBody Object requestBody, HttpServletRequest request)
    {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authHeader != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(
                routingServiceUrl + "/api/v1/dangerous-zones",
                HttpMethod.POST,
                entity,
                Void.class
        );
    }

    @GetMapping
    public ResponseEntity<Object> fetchAllZones(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        HttpHeaders headers = new HttpHeaders();
        if (authHeader != null)
        {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                routingServiceUrl + "/api/v1/dangerous-zones",
                HttpMethod.GET,
                entity,
                Object.class
        );
    }
}