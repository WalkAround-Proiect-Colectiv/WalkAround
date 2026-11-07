package services.applicationapi.preferences.dto;

import java.util.List;
import jakarta.validation.constraints.*;

public record UpdatePreferencesRequest(
        @NotNull(message = "Selected options are required; use an empty list to clear preferences")
        @Size(max = 100, message = "Select at most 100 options")
        List<@NotBlank(message = "Option IDs cannot be blank")
             @Size(max = 80, message = "Invalid option ID") String> selectedOptionIds) {}
