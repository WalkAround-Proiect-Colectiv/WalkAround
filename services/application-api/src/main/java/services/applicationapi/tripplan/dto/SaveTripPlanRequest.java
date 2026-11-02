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

public record SaveTripPlanRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{3}") String originAirport,
        @NotBlank @Size(max = 100) String departureCity,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{2}") String departureCountryCode,
        @NotBlank @Size(max = 100) String destinationCity,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{2}") String countryCode,
        @NotNull LocalDateTime departureFrom,
        @NotNull LocalDateTime departureUntil,
        @NotNull @Min(1) @Max(365) Integer stayingNights,
        @NotNull TicketOption ticketOption,
        @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal flightBudget,
        @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal accommodationBudget
) {
}
