package services.applicationapi.tripplan.service.hotlines;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.country.repository.CountryRepository;
import services.applicationapi.emergencynumber.model.EmergencyNumber;
import services.applicationapi.emergencynumber.repository.EmergencyNumberRepository;
import services.applicationapi.tripplan.dto.CountryHotlineResponse;
import services.applicationapi.tripplan.service.exceptions.InvalidCountryHotlineRequestException;

@Service
public class CountryHotlineServiceImpl implements CountryHotlineService {

    private static final Logger log = LoggerFactory.getLogger(CountryHotlineServiceImpl.class);

    private final CountryRepository countryRepository;
    private final EmergencyNumberRepository emergencyNumberRepository;

    public CountryHotlineServiceImpl(
            CountryRepository countryRepository,
            EmergencyNumberRepository emergencyNumberRepository
    ) {
        this.countryRepository = countryRepository;
        this.emergencyNumberRepository = emergencyNumberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CountryHotlineResponse get(String countryCode) {
        if (countryCode == null || !countryCode.matches("[A-Za-z]{2}")) {
            throw new InvalidCountryHotlineRequestException("Country code must contain two letters");
        }

        String code = countryCode.toUpperCase(Locale.ROOT);
        if (countryRepository.findByCode(code).isEmpty()) {
            throw new InvalidCountryHotlineRequestException("Destination country is not supported");
        }

        List<EmergencyNumber> rows = emergencyNumberRepository.findByCountryCode(code);
        Map<String, List<EmergencyNumber>> grouped = rows.stream().collect(Collectors.groupingBy(
                EmergencyNumber::getSourceRecordId,
                LinkedHashMap::new,
                Collectors.toList()
        ));
        List<CountryHotlineResponse.Hotline> hotlines = grouped.values().stream()
                .map(this::toHotline)
                .toList();

        log.debug("Loaded {} hotline services and {} numbers for {}", hotlines.size(), rows.size(), code);
        return new CountryHotlineResponse(code, hotlines);
    }

    private CountryHotlineResponse.Hotline toHotline(List<EmergencyNumber> rows) {
        EmergencyNumber first = rows.getFirst();
        List<CountryHotlineResponse.Number> numbers = rows.stream()
                .map(row -> new CountryHotlineResponse.Number(row.getNumber(), row.getNumberType()))
                .toList();

        return new CountryHotlineResponse.Hotline(
                first.getSourceRecordId(), first.getName(), first.getOrganization(), first.getCategory(),
                rows.stream().anyMatch(EmergencyNumber::isGeneralEmergency), numbers,
                first.getLastVerified(), first.getVerificationStatus(), first.getScope(),
                first.getGeography(), first.getHours(), first.getCost(), first.getTarget(),
                first.getLanguages(), first.getWebsite(), first.getChatUrl(), first.getEmail(),
                first.getNotes(), first.getSourceLinks(), first.getSource(), first.getDatasetVersion()
        );
    }
}
