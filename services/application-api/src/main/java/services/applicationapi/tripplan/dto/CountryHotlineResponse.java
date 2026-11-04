package services.applicationapi.tripplan.dto;

import java.time.LocalDate;
import java.util.List;

public record CountryHotlineResponse(
        String countryCode,
        List<Hotline> hotlines
) {
    public record Hotline(
            String id,
            String name,
            String organization,
            String category,
            boolean generalEmergency,
            List<Number> numbers,
            LocalDate lastVerified,
            String verificationStatus,
            String scope,
            String geography,
            String hours,
            String cost,
            String target,
            List<String> languages,
            String website,
            String chatUrl,
            String email,
            String notes,
            List<String> sourceLinks,
            String source,
            String datasetVersion
    ) {
    }

    public record Number(String value, String type) {
    }
}
