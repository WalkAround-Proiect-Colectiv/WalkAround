package services.applicationapi.tripplan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrainSearchRequest(
        @NotBlank @Size(max = 100) String originCity,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{2}") String originCountryCode,
        @NotBlank @Size(max = 100) String destinationCity,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{2}") String countryCode,
        @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal budget,
        @NotNull LocalDateTime departureFrom,
        @NotNull LocalDateTime departureUntil,
        @NotNull @Min(1) @Max(365) Integer stayingNights,
        @NotNull TicketOption ticketOption,
        @Min(1) @Max(9) Integer adults
) {
    public TrainSearchRequest {
        if (adults == null) adults = 1;
    }
}
