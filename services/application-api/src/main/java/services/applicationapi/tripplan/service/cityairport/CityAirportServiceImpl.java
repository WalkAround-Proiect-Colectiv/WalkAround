package services.applicationapi.tripplan.service.cityairport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.airport.model.Airport;
import services.applicationapi.airport.repository.AirportRepository;
import services.applicationapi.municipality.model.Municipality;
import services.applicationapi.municipality.repository.MunicipalityRepository;
import services.applicationapi.tripplan.configurations.CityAirportProperties;
import services.applicationapi.tripplan.dto.AirportMatchType;
import services.applicationapi.tripplan.dto.AirportResponse;
import services.applicationapi.tripplan.service.exceptions.AmbiguousCitySelectionException;
import services.applicationapi.tripplan.service.exceptions.InvalidCitySelectionException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Service
public class CityAirportServiceImpl implements CityAirportService {

    private static final Logger log = LoggerFactory.getLogger(CityAirportServiceImpl.class);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final AirportRepository airportRepository;
    private final MunicipalityRepository municipalityRepository;
    private final CityLocationProvider cityLocationProvider;
    private final CityAirportProperties properties;

    public CityAirportServiceImpl(
            AirportRepository airportRepository,
            MunicipalityRepository municipalityRepository,
            CityLocationProvider cityLocationProvider,
            CityAirportProperties properties
    ) {
        this.airportRepository = airportRepository;
        this.municipalityRepository = municipalityRepository;
        this.cityLocationProvider = cityLocationProvider;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AirportResponse> findAirports(String city, String countryCode) {
        if (city == null || city.isBlank()) {
            throw new InvalidCitySelectionException("City is required");
        }

        String normalizedCity = city.trim();

        if (normalizedCity.length() > 100
                || normalizedCity.codePoints().anyMatch(Character::isISOControl)
                || normalizedCity.codePoints().noneMatch(Character::isLetterOrDigit)) {
            throw new InvalidCitySelectionException(
                    "City must contain 1 to 100 characters and at least one letter or digit"
            );
        }

        if (countryCode == null || !countryCode.trim().matches("[A-Za-z]{2}")) {
            throw new InvalidCitySelectionException("Country code must contain two letters");
        }

        String normalizedCountryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        List<Municipality> municipalities = municipalityRepository.findByCountry_Code(normalizedCountryCode);
        String exactCityKey = CityNameNormalizer.toExactKey(normalizedCity);

        List<Municipality> matches = municipalities.stream()
                .filter(municipality -> CityNameNormalizer.toExactKey(municipality.getName())
                        .equals(exactCityKey))
                .toList();

        if (matches.isEmpty()) {
            Set<String> cityKeys = CityNameNormalizer.toComparisonKeys(normalizedCity);

            matches = municipalities.stream()
                    .filter(municipality -> CityNameNormalizer.toComparisonKeys(municipality.getName())
                            .stream()
                            .anyMatch(cityKeys::contains))
                    .toList();

            if (matches.stream()
                    .map(municipality -> CityNameNormalizer.toExactKey(municipality.getName()))
                    .distinct()
                    .count() > 1) {
                throw new AmbiguousCitySelectionException(
                        "City name matches more than one municipality; use its exact spelling"
                );
            }
        }

        List<Long> municipalityIds = matches
                .stream()
                .map(Municipality::getId)
                .toList();

        List<Airport> cityAirports = municipalityIds.isEmpty()
                ? List.of()
                : airportRepository.findByMunicipality_IdInOrderByNameAscIdAsc(municipalityIds);

        CityLocationProvider.CityLocation location = cityLocationProvider.find(
                normalizedCity, normalizedCountryCode);
        List<AirportResponse> results = new ArrayList<>();
        Set<Long> cityAirportIds = cityAirports.stream()
                .map(Airport::getId)
                .collect(Collectors.toSet());

        for (Airport airport : cityAirports) {
            results.add(toResponse(airport, AirportMatchType.CITY,
                    distanceKm(location, airport)));
        }

        double latitudeDelta = Math.toDegrees(properties.nearbyAirportRadiusKm() / EARTH_RADIUS_KM);
        double cosine = Math.cos(Math.toRadians(location.latitude()));
        double longitudeDelta = cosine > 0.001 ? Math.min(180, latitudeDelta / cosine) : 180;

        List<AirportResponse> nearbyAirports = airportRepository
                .findByCountry_CodeAndLatitudeBetweenAndLongitudeBetweenAndIataCodeIsNotNull(
                        normalizedCountryCode,
                        Math.max(-90, location.latitude() - latitudeDelta),
                        Math.min(90, location.latitude() + latitudeDelta),
                        Math.max(-180, location.longitude() - longitudeDelta),
                        Math.min(180, location.longitude() + longitudeDelta))
                .stream()
                .filter(airport -> !cityAirportIds.contains(airport.getId()))
                .map(airport -> new NearbyAirport(airport, distanceKm(location, airport)))
                .filter(candidate -> candidate.distanceKm() <= properties.nearbyAirportRadiusKm())
                .sorted(Comparator.comparingDouble(NearbyAirport::distanceKm)
                        .thenComparing(candidate -> candidate.airport().getId()))
                .limit(properties.nearbyAirportMaxResults())
                .map(candidate -> toResponse(candidate.airport(), AirportMatchType.NEARBY,
                        candidate.distanceKm()))
                .toList();

        results.addAll(nearbyAirports);
        log.debug("Found {} city airports and {} nearby airports for '{}' in {}",
                cityAirports.size(), nearbyAirports.size(), normalizedCity, normalizedCountryCode);
        return results;
    }

    private double distanceKm(CityLocationProvider.CityLocation city, Airport airport) {
        double latitudeDelta = Math.toRadians(airport.getLatitude() - city.latitude());
        double longitudeDelta = Math.toRadians(airport.getLongitude() - city.longitude());
        double arc = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(city.latitude()))
                * Math.cos(Math.toRadians(airport.getLatitude()))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(arc)));
    }

    private AirportResponse toResponse(Airport airport, AirportMatchType matchType, double distanceKm) {
        return new AirportResponse(
                airport.getId(),
                airport.getName(),
                airport.getIataCode(),
                airport.getCountry().getCode(),
                airport.getLatitude(),
                airport.getLongitude(),
                matchType,
                Math.round(distanceKm * 10.0) / 10.0
        );
    }

    private record NearbyAirport(Airport airport, double distanceKm) {
    }
}
