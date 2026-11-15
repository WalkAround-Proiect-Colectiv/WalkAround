package services.applicationapi.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import services.applicationapi.events.model.EventInterest;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.service.locality.LocalityResolver;
import services.applicationapi.events.service.reminders.EventReminderJob;
import services.applicationapi.events.service.reminders.ReminderPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The whole events flow over HTTP against a real Postgres: nearby list, marking, reminders, unmarking.
 * Uses the recorded Cluj-Napoca responses, a fixed clock on the day they were recorded, a stubbed town
 * lookup and a captured reminder publisher, so no external service is called.
 */
@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONSUMER_GROUP_ID=events-test", "SERVER_PORT=0",
        "ROUTING_SERVICE_URL=http://localhost:1", "SERP_API_KEY=test-key", "GEONAMES_USERNAME=test",
        "app.login.signing-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.events.provider=recorded", "app.events.reminders-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class EventsFlowIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final Instant NOW = LocalDateTime.of(2026, 11, 5, 9, 0).atZone(ZONE).toInstant();
    private static final Locality CLUJ = new Locality("RO/cluj/cluj-napoca", "Cluj-Napoca", "Cluj", "RO");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean LocalityResolver localityResolver;
    @MockitoBean ReminderPublisher reminderPublisher;
    @MockitoBean(name = "eventsClock") Clock clock;

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventReminderJob reminderJob;
    @Autowired ObjectMapper objectMapper;
    @Autowired @Qualifier("applicationJwtEncoder") JwtEncoder jwtEncoder;

    private MockMvc mvc;
    private String token;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // Town snapshots are kept: the server's memory cache is shared by all tests in this context.
        jdbc.update("DELETE FROM event_interests");
        jdbc.update("DELETE FROM users");
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        token = accessToken(userId);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZONE);
        when(localityResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.of(CLUJ));
        reset(reminderPublisher);
    }

    private String accessToken(UUID userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("walkaround")
                .subject(userId.toString())
                .audience(List.of("walkaround-api"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("token_use", "access")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String interestBody(String eventKey, Integer remindBeforeMinutes) {
        return "{\"localityKey\":\"" + CLUJ.key() + "\",\"eventKey\":\"" + eventKey + "\",\"remindBeforeMinutes\":"
                + remindBeforeMinutes + "}";
    }

    @Test
    void requiresSignIn() throws Exception {
        mvc.perform(get("/api/events/nearby").param("lat", "46.77").param("lng", "23.59"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/events/interests")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsMarksRemindsAndUnmarks() throws Exception {
        // Nearby list for the user's town, from the recorded responses and then from the cache.
        JsonNode nearby = json(mvc.perform(get("/api/events/nearby").param("lat", "46.77").param("lng", "23.59")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locality.key").value(CLUJ.key()))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.attribution").isNotEmpty())
                .andReturn());
        assertThat(nearby.get("events")).hasSize(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_locality_snapshots WHERE locality_key = ?",
                Integer.class, CLUJ.key())).isEqualTo(1);
        String eventKey = null;
        for (JsonNode event : nearby.get("events")) {
            assertThat(event.has("detailsToken")).isFalse();
            if (event.get("title").asString().equals("THE RITE OF SPRING")) {
                eventKey = event.get("key").asString();
            }
        }
        assertThat(eventKey).isNotNull();

        // Mark it with a reminder one week before; that time has passed, so it is due immediately.
        mvc.perform(put("/api/events/interests").header("Authorization", bearer())
                        .contentType("application/json").content(interestBody(eventKey, 10080)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("THE RITE OF SPRING"))
                .andExpect(jsonPath("$.remindBeforeMinutes").value(10080))
                .andExpect(jsonPath("$.remindAt").value(NOW.toString()));
        mvc.perform(get("/api/events/nearby").param("lat", "46.77").param("lng", "23.59")
                        .header("Authorization", bearer()))
                .andExpect(jsonPath("$.events[?(@.key == '" + eventKey + "')].interested").value(true));

        // The reminder job sends it exactly once.
        assertThat(reminderJob.sendDueReminders()).isEqualTo(1);
        assertThat(reminderJob.sendDueReminders()).isZero();
        verify(reminderPublisher, times(1)).publish(any(EventInterest.class));

        // Unseen until the user dismisses it.
        mvc.perform(get("/api/events/reminders/unseen").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/events/interests/" + eventKey + "/reminder-seen").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/events/reminders/unseen").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(0));

        // Details combine the cached event and the user's interest.
        mvc.perform(get("/api/events/details").param("localityKey", CLUJ.key()).param("eventKey", eventKey)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.title").value("THE RITE OF SPRING"))
                .andExpect(jsonPath("$.interest.remindBeforeMinutes").value(10080));

        // Unmark.
        mvc.perform(delete("/api/events/interests/" + eventKey).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/events/interests").header("Authorization", bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        mvc.perform(get("/api/events/nearby").param("lat", "46.77").param("lng", "23.59")
                .header("Authorization", bearer())).andExpect(status().isOk());

        mvc.perform(get("/api/events/nearby").param("lat", "120").param("lng", "23.59")
                .header("Authorization", bearer())).andExpect(status().isBadRequest());
        mvc.perform(put("/api/events/interests").header("Authorization", bearer())
                        .contentType("application/json").content(interestBody("unknown", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/events/interests").header("Authorization", bearer())
                        .contentType("application/json").content(interestBody("unknown", null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/events/details").param("localityKey", CLUJ.key()).param("eventKey", "unknown")
                .header("Authorization", bearer())).andExpect(status().isNotFound());
    }

    @Test
    void returnsAnEmptyListOutsideAnyTown() throws Exception {
        when(localityResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        mvc.perform(get("/api/events/nearby").param("lat", "45.0").param("lng", "30.0")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locality").doesNotExist())
                .andExpect(jsonPath("$.events.length()").value(0));
    }
}
