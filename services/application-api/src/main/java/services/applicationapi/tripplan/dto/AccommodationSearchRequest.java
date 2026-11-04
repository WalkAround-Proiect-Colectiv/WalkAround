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
import java.time.LocalDate;

public record AccommodationSearchRequest(
        @NotBlank @Size(max = 100) String destinationCity,
        @NotBlank @Pattern(regexp = "(?i)[a-z]{2}") String countryCode,
        @NotNull LocalDate checkInDate,
        @NotNull @Min(1) @Max(365) Integer stayingNights,
        @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal budget
) {
}
