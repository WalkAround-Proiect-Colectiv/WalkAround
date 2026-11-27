package services.applicationapi.journal.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import services.applicationapi.journal.service.JournalDates.StartMode;

public record JournalTripRequest(
        @NotNull UUID requestId,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 100) String destinationCity,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{2}") String countryCode,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        UUID sourcePlanId,
        StartMode startMode,
        Boolean keepNights) {}