package services.routingservice.routecalculation.service;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import services.routingservice.dangerouszone.repository.DangerousZoneRepository;
import services.routingservice.routecalculation.dto.PointDto;
import services.routingservice.routecalculation.dto.RouteRequestDto;
import services.routingservice.routecalculation.exception.NoSafeRouteException;

import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class RouteServiceImpl implements RouteService {
    private static final Logger log = LoggerFactory.getLogger(RouteServiceImpl.class);

    @Value("${app.routing.h3-resolution:9}")
    private int h3Resolution;
    @Value("${app.routing.danger-zones-size-limit:50}")
    private int dangerZonesSizeLimit;
    @Value("${app.routing.extra-radius:0.02}")
    private double extraRadius;

    private final DangerousZoneRepository dangerZoneRepo;
    private final RestTemplate restTemplate;
    private final H3Core h3;

    @Value("${app.valhalla.url}")
    private String valhallaUrl;

    @Value("${app.valhalla.language:en-US}")
    private String valhallaLanguage;

    public RouteServiceImpl(DangerousZoneRepository repo) throws IOException {
        this.dangerZoneRepo = repo;
        this.restTemplate = new RestTemplate();
        this.h3 = H3Core.newInstance();
    }

    private record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {}

    public String calculatePedestrianRoute(RouteRequestDto request) {

        Map<String, Object> requestBody = new HashMap<>(Map.of(
                "locations", getLocations(request),
                "costing", "pedestrian",
                "directions_options", Map.of(
                        "units", "kilometers",
                        "language", valhallaLanguage
                )
        ));

        if (request.avoidDanger()) {
            Set<String> activeDangerCells = getActiveDangerZonesCells(request);

            if (!activeDangerCells.isEmpty()) {
                validatePointsNotInDangerZones(request, activeDangerCells);
                List<List<List<Double>>> avoidPolygons = getAvoidPolygons(activeDangerCells);

                if (avoidPolygons.size() > dangerZonesSizeLimit) {
                    throw new NoSafeRouteException(
                            NoSafeRouteException.Reason.TOO_MANY_ZONES,
                            "Your route passes through an area with too many fragmented danger zones."
                    );
                }

                if (!avoidPolygons.isEmpty()) {
                    requestBody.put("exclude_polygons", avoidPolygons);
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(valhallaUrl, httpEntity, String.class);
        } catch (HttpClientErrorException.BadRequest exception) {
            log.warn("Valhalla could not find a path excluding the danger zones. Response: {}", exception.getResponseBodyAsString());
            throw new NoSafeRouteException(
                    NoSafeRouteException.Reason.NO_PATH,
                    "A safe route that avoids all danger zones could not be calculated."
            );
        } catch (RestClientResponseException exception) {
            log.warn("Valhalla route request returned HTTP {}", exception.getStatusCode().value());
            throw exception;
        }
    }

    private Set<String> getActiveDangerZonesCells(RouteRequestDto request) {
        BoundingBox bbox = calculateBoundingBox(request.allPoints());
        LocalTime currentTime = resolveDepartureTime(request.departureTime());

        return dangerZoneRepo.findDangerCells(
                bbox.minLat(),
                bbox.maxLat(),
                bbox.minLng(),
                bbox.maxLng(),
                currentTime
        );
    }

    private void validatePointsNotInDangerZones(RouteRequestDto request, Set<String> activeDangerCells) {
        for (PointDto point : request.allPoints()) {
            String cell = h3.latLngToCellAddress(point.lat(), point.lng(), h3Resolution);
            if (activeDangerCells.contains(cell)) {
                throw new NoSafeRouteException(
                        NoSafeRouteException.Reason.POINT_IN_DANGER_ZONE,
                        "A point on your route is located directly inside an active danger zone."
                );
            }
        }
    }

    private List<List<List<Double>>> getAvoidPolygons(Set<String> dangerCells) {
        List<List<List<Double>>> avoidPolygons = new ArrayList<>();
        List<List<List<LatLng>>> multiPolygons = h3.cellAddressesToMultiPolygon(dangerCells, true);

        for (List<List<LatLng>> polygon : multiPolygons) {
            List<LatLng> outerRing = polygon.get(0);

            List<List<Double>> valhallaRing = new ArrayList<>();
            for (LatLng point : outerRing) {
                valhallaRing.add(List.of(point.lng, point.lat));
            }
            avoidPolygons.add(valhallaRing);
        }

        return avoidPolygons;
    }

    private BoundingBox calculateBoundingBox(List<PointDto> points) {
        PointDto firstPoint = points.get(0);

        double minLat = firstPoint.lat();
        double maxLat = firstPoint.lat();
        double minLng = firstPoint.lng();
        double maxLng = firstPoint.lng();

        for (PointDto p : points) {
            minLat = Math.min(minLat, p.lat());
            maxLat = Math.max(maxLat, p.lat());
            minLng = Math.min(minLng, p.lng());
            maxLng = Math.max(maxLng, p.lng());
        }

        return new BoundingBox(
                minLat - extraRadius,
                maxLat + extraRadius,
                minLng - extraRadius,
                maxLng + extraRadius
        );
    }

    private LocalTime resolveDepartureTime(ZonedDateTime departureTime) {
        return (departureTime != null)
                ? departureTime.toLocalTime()
                : ZonedDateTime.now(ZoneId.of("Europe/Bucharest")).toLocalTime();
    }

    private List<Map<String, Object>> getLocations(RouteRequestDto request) {
        return request.allPoints().stream()
                .map(this::location)
                .toList();
    }

    private Map<String, Object> location(PointDto point) {
        return Map.of("lat", point.lat(), "lon", point.lng());
    }
}