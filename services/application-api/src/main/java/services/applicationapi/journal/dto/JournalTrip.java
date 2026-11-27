package services.applicationapi.journal.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JournalTrip(UUID id, String title, String description, String destinationCity,
                          String countryCode, String countryName, LocalDate startDate, LocalDate endDate,
                          UUID sourcePlanId, Instant createdAt, Instant updatedAt) {}