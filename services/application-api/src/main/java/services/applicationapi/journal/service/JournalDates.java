package services.applicationapi.journal.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class JournalDates {
    public enum StartMode { PLANNED, CUSTOM }
    public record PlanWindow(LocalDate from, LocalDate until, int nights) {}
    public record Range(LocalDate start, LocalDate end) {}

    private JournalDates() {}

    public static Range resolve(LocalDate today, LocalDate start, LocalDate end, PlanWindow plan,
                                StartMode mode, Boolean keepNights) {
        if (start == null || start.isBefore(today) || start.getYear() > 9999) {
            throw invalid("Start date must be today or later.");
        }
        if (plan != null) {
            if (mode == null || keepNights == null) {
                throw invalid("Choose a start-date option and confirm whether to keep the saved nights.");
            }
            if (mode == StartMode.PLANNED && (start.isBefore(plan.from()) || start.isAfter(plan.until()))) {
                throw invalid("Choose a date in the saved departure window or select a custom date.");
            }
            if (keepNights) {
                if (plan.nights() < 1 || plan.nights() > 365) throw invalid("The saved trip nights are invalid.");
                try { end = start.plusDays(plan.nights()); }
                catch (DateTimeException exception) { throw invalid("The trip dates are out of range."); }
            }
        } else if (mode != null || keepNights != null) {
            throw invalid("Saved-plan date options require a personal source plan.");
        }
        if (end == null || end.isBefore(start) || end.getYear() > 9999) {
            throw invalid("End date must be the same as or later than the start date.");
        }
        return new Range(start, end);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}