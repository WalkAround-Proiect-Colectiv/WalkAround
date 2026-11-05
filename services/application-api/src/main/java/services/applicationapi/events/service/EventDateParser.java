package services.applicationapi.events.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the provider's human-readable dates into instants.
 *
 * <p>Google Events lists dates like {@code "Nov 6, 8:00 PM"} or {@code "Feb 11"} without a year, while
 * event details use {@code "Fri, 6 Nov 2026, 20:00"}. When the year is missing, the next occurrence of
 * that day (today included) is assumed, since the provider only lists upcoming events. Dates without a
 * time are treated as all-day events starting at local midnight.
 */
public final class EventDateParser {

    public record ParsedDate(Instant startsAt, boolean allDay) {
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    private static final String MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern MONTH_DAY = Pattern.compile("\\b" + MONTH + "\\s+(\\d{1,2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DAY_MONTH = Pattern.compile("\\b(\\d{1,2})\\s+" + MONTH + "(?![a-z])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern TIME_RANGE_12H = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2}))?\\s*[–—-]\\s*\\d{1,2}(?::\\d{2})?\\s*([AaPp])\\.?[Mm]\\b");
    private static final Pattern TIME_12H = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*([AaPp])\\.?[Mm]\\b");
    private static final Pattern TIME_24H = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");

    private EventDateParser() {
    }

    public static Optional<ParsedDate> parse(String startDate, String when, LocalDate today, ZoneId zone) {
        String text = when == null ? "" : when;
        Optional<int[]> monthDay = monthDay(startDate).or(() -> monthDay(text));
        if (monthDay.isEmpty()) {
            return Optional.empty();
        }
        int month = monthDay.get()[0];
        int day = monthDay.get()[1];
        LocalDate date;
        try {
            Matcher year = YEAR.matcher(text);
            if (year.find()) {
                date = LocalDate.of(Integer.parseInt(year.group(1)), month, day);
            } else {
                date = LocalDate.of(today.getYear(), month, day);
                if (date.isBefore(today)) {
                    date = date.plusYears(1);
                }
            }
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
        Optional<LocalTime> time = time(text);
        Instant startsAt = time.isPresent()
                ? date.atTime(time.get()).atZone(zone).toInstant()
                : date.atStartOfDay(zone).toInstant();
        return Optional.of(new ParsedDate(startsAt, time.isEmpty()));
    }

    private static Optional<int[]> monthDay(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Matcher monthFirst = MONTH_DAY.matcher(value);
        Matcher dayFirst = DAY_MONTH.matcher(value);
        boolean hasMonthFirst = monthFirst.find();
        boolean hasDayFirst = dayFirst.find();
        if (hasMonthFirst && (!hasDayFirst || monthFirst.start() <= dayFirst.start())) {
            return Optional.of(new int[] {month(monthFirst.group(1)), Integer.parseInt(monthFirst.group(2))});
        }
        if (hasDayFirst) {
            return Optional.of(new int[] {month(dayFirst.group(2)), Integer.parseInt(dayFirst.group(1))});
        }
        return Optional.empty();
    }

    private static int month(String name) {
        return MONTHS.get(name.substring(0, 3).toLowerCase(Locale.ROOT));
    }

    private static Optional<LocalTime> time(String text) {
        Matcher range = TIME_RANGE_12H.matcher(text);
        if (range.find()) {
            return twelveHour(range.group(1), range.group(2), range.group(3));
        }
        Matcher twelve = TIME_12H.matcher(text);
        if (twelve.find()) {
            return twelveHour(twelve.group(1), twelve.group(2), twelve.group(3));
        }
        Matcher twentyFour = TIME_24H.matcher(text);
        if (twentyFour.find()) {
            return Optional.of(LocalTime.of(Integer.parseInt(twentyFour.group(1)), Integer.parseInt(twentyFour.group(2))));
        }
        return Optional.empty();
    }

    private static Optional<LocalTime> twelveHour(String hourText, String minuteText, String meridiem) {
        int hour = Integer.parseInt(hourText);
        int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (hour < 1 || hour > 12 || minute > 59) {
            return Optional.empty();
        }
        boolean pm = meridiem.equalsIgnoreCase("p");
        int hour24 = hour % 12 + (pm ? 12 : 0);
        return Optional.of(LocalTime.of(hour24, minute));
    }
}
