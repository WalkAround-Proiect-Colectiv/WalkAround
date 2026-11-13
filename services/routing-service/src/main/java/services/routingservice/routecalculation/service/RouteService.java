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

public interface RouteService {
    String calculatePedestrianRoute(RouteRequestDto request);
}
