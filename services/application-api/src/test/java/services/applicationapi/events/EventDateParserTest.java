package services.applicationapi.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import services.applicationapi.events.service.EventDateParser;
import services.applicationapi.events.service.EventDateParser.ParsedDate;

class EventDateParserTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    @Test
    void parsesListFormatWithTwelveHourTime() {
        ParsedDate parsed = EventDateParser.parse("Nov 6", "Nov 6, 8:00 PM", TODAY, ZONE).orElseThrow();

        assertThat(parsed.startsAt()).isEqualTo(local(2026, 11, 6, 20, 0));
        assertThat(parsed.allDay()).isFalse();
    }

    @Test
    void parsesDetailsFormatWithYearAndTwentyFourHourTime() {
        ParsedDate parsed = EventDateParser.parse(null, "Fri, 6 Nov 2026, 20:00", TODAY, ZONE).orElseThrow();

        assertThat(parsed.startsAt()).isEqualTo(local(2026, 11, 6, 20, 0));
    }

    @Test
    void assumesNextYearForDatesEarlierThanToday() {
        ParsedDate parsed = EventDateParser.parse("Feb 11", "Feb 11", TODAY, ZONE).orElseThrow();

        assertThat(parsed.startsAt()).isEqualTo(local(2027, 2, 11, 0, 0));
        assertThat(parsed.allDay()).isTrue();
    }

    @Test
    void keepsTodayInTheCurrentYear() {
        ParsedDate parsed = EventDateParser.parse("Nov 5", "Nov 5, 11:00 PM", TODAY, ZONE).orElseThrow();

        assertThat(parsed.startsAt()).isEqualTo(local(2026, 11, 5, 23, 0));
    }

    @Test
    void usesTheStartOfATimeRange() {
        ParsedDate parsed = EventDateParser.parse(null, "Sat, Nov 8, 5 – 9 PM", TODAY, ZONE).orElseThrow();

        assertThat(parsed.startsAt()).isEqualTo(local(2026, 11, 8, 17, 0));
    }

    @Test
    void usesTheFirstDayOfADateRange() {
        ParsedDate parsed = EventDateParser.parse(null, "Nov 21 – Dec 24", TODAY, ZONE).orElseThrow();

        assertThat(parsed.startsAt()).isEqualTo(local(2026, 11, 21, 0, 0));
        assertThat(parsed.allDay()).isTrue();
    }

    @Test
    void handlesNoonAndMidnight() {
        assertThat(EventDateParser.parse("Nov 7", "Nov 7, 12:00 PM", TODAY, ZONE).orElseThrow().startsAt())
                .isEqualTo(local(2026, 11, 7, 12, 0));
        assertThat(EventDateParser.parse("Nov 7", "Nov 7, 12:30 AM", TODAY, ZONE).orElseThrow().startsAt())
                .isEqualTo(local(2026, 11, 7, 0, 30));
    }

    @Test
    void rejectsTextWithoutADate() {
        assertThat(EventDateParser.parse(null, "Tonight", TODAY, ZONE)).isEmpty();
        assertThat(EventDateParser.parse(null, null, TODAY, ZONE)).isEmpty();
        assertThat(EventDateParser.parse("Feb 30", "Feb 30", TODAY, ZONE)).isEmpty();
    }
}
