package services.applicationapi.preferences.dto;

import java.util.List;

public record PreferencesResponse(List<Question> questions, List<String> selectedOptionIds) {
    public record Question(String id, String title, List<Option> options) {}
    public record Option(String id, String label) {}
}
