package services.applicationapi.events.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Marks an event; {@code remindBeforeMinutes} is null for no reminder (at most 30 days before). */
public record InterestRequest(
        @NotBlank @Size(max = 200) String localityKey,
        @NotBlank @Size(max = 64) String eventKey,
        @Min(1) @Max(43200) Integer remindBeforeMinutes
) {
}
