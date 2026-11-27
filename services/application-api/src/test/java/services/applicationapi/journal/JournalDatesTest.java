package services.applicationapi.journal;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.journal.service.JournalDates;
import services.applicationapi.journal.service.JournalDates.PlanWindow;
import services.applicationapi.journal.service.JournalDates.StartMode;

class JournalDatesTest {
    private final LocalDate today = LocalDate.of(2028, 2, 28);
    private final PlanWindow window = new PlanWindow(today, today.plusDays(5), 2);

    @Test
    void allowsTodayAndSameDayButNotPastOrReversedDates() {
        assertThat(JournalDates.resolve(today, today, today, null, null, null).end()).isEqualTo(today);
        assertThatThrownBy(() -> JournalDates.resolve(today, today.minusDays(1), today, null, null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> JournalDates.resolve(today, today, today.minusDays(1), null, null, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void keepingNightsComputesTheEndAcrossLeapDaysAndYearBoundaries() {
        assertThat(JournalDates.resolve(today, today, null, window, StartMode.PLANNED, true).end()).isEqualTo(LocalDate.of(2028, 3, 1));
        LocalDate december = LocalDate.of(2028, 12, 31);
        assertThat(JournalDates.resolve(today, december, null, window, StartMode.CUSTOM, true).end()).isEqualTo(LocalDate.of(2029, 1, 2));
    }

    @Test
    void expiredPlansCanUseCustomDatesButNotTheExpiredWindow() {
        var old = new PlanWindow(today.minusDays(20), today.minusDays(10), 3);
        assertThat(JournalDates.resolve(today, today, null, old, StartMode.CUSTOM, true).end()).isEqualTo(today.plusDays(3));
        assertThatThrownBy(() -> JournalDates.resolve(today, today, null, old, StartMode.PLANNED, true)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void changingNightsRequiresAnEndAndConfirmationIsExplicit() {
        assertThat(JournalDates.resolve(today, today, today, window, StartMode.CUSTOM, false).end()).isEqualTo(today);
        assertThatThrownBy(() -> JournalDates.resolve(today, today, null, window, StartMode.CUSTOM, false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> JournalDates.resolve(today, today, today, window, null, false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> JournalDates.resolve(today, today, today, window, StartMode.CUSTOM, null)).isInstanceOf(ResponseStatusException.class);
    }
}