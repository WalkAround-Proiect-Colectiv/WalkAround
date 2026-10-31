package services.applicationapi.tripplan.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.airport.model.Airport;
import services.applicationapi.airport.repository.AirportRepository;
import services.applicationapi.municipality.model.Municipality;
import services.applicationapi.municipality.repository.MunicipalityRepository;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;
import services.applicationapi.tripplan.dto.AirportResponse;
import services.applicationapi.tripplan.service.exceptions.AmbiguousCitySelectionException;
import services.applicationapi.tripplan.service.exceptions.InvalidCitySelectionException;

@Service
public class CityAirportServiceImpl implements CityAirportService {

    private static final Logger log = LoggerFactory.getLogger(CityAirportServiceImpl.class);

    private final AirportRepository airportRepository;
    private final MunicipalityRepository municipalityRepository;

    public CityAirportServiceImpl(
            AirportRepository airportRepository,
            MunicipalityRepository municipalityRepository
    ) {
        this.airportRepository = airportRepository;
        this.municipalityRepository = municipalityRepository;
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

        if (municipalityIds.isEmpty()) {
            log.debug("No airport municipalities match city '{}' in country {}",
                    normalizedCity, normalizedCountryCode);
            return List.of();
        }

        List<AirportResponse> airports = airportRepository
                .findByMunicipality_IdInOrderByNameAscIdAsc(municipalityIds)
                .stream()
                .map(this::toResponse)
                .toList();

        log.debug("Found {} airports for city '{}' in country {}",
                airports.size(), normalizedCity, normalizedCountryCode);

        return airports;
    }

    private AirportResponse toResponse(Airport airport) {
        return new AirportResponse(
                airport.getId(),
                airport.getName(),
                airport.getIataCode(),
                airport.getLatitude(),
                airport.getLongitude()
        );
    }
}
