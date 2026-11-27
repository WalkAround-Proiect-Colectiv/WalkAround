package services.applicationapi.journal.configuration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JournalConfiguration {
    @Bean("journalClock")
    Clock journalClock() { return Clock.systemUTC(); }
}