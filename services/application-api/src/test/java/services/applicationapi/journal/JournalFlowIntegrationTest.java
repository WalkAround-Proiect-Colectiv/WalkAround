package services.applicationapi.journal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import services.applicationapi.poiimages.service.PoiImageStorage;
import services.applicationapi.tripplan.service.flightsearch.DestinationCityValidator;
import services.applicationapi.tripplan.service.groupchat.GroupChatOutboxRelay;
import tools.jackson.databind.*;

@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONSUMER_GROUP_ID=journal-test", "SERVER_PORT=0",
        "ROUTING_SERVICE_URL=http://localhost:1", "SERP_API_KEY=test-key", "GEONAMES_USERNAME=test",
        "app.login.signing-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.events.provider=recorded", "app.events.reminders-enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JournalFlowIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getProperty("journal.test-image", "postgres:18")).asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @MockitoBean DestinationCityValidator cities;
    @MockitoBean GroupChatOutboxRelay relay;
    @MockitoBean PoiImageStorage storage;
    @MockitoBean(name = "journalClock") Clock clock;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("applicationJwtEncoder") JwtEncoder encoder;
    private MockMvc mvc;
    private UUID aliceId;
    private String alice;
    private String bob;

    @BeforeEach
    void fixtures() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(clock.instant()).thenReturn(Instant.parse("2028-02-28T12:00:00Z")); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        jdbc.execute("TRUNCATE users CASCADE");
        jdbc.execute("INSERT INTO countries(id,code,name,continent) VALUES (642,'RO','Romania','EU') ON CONFLICT(code) DO NOTHING");
        aliceId = UUID.randomUUID(); alice = user(aliceId, "Alice"); bob = user(UUID.randomUUID(), "Bob");
        when(cities.validate(anyString(), eq("RO"))).thenAnswer(call -> new DestinationCityValidator.ValidatedCity(call.getArgument(0), "Romania"));
    }

    @Test
    void manualCrudIsPrivateAndCreationRetriesAreIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        var input = input(id);
        create(input, alice); create(input, alice);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_trips", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/journal/trips/" + id).header("Authorization", alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2028-02-28")).andExpect(jsonPath("$.endDate").value("2028-02-28"));
        mvc.perform(get("/api/journal/trips/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(put("/api/journal/trips/" + id).header("Authorization", bob).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/journal/trips/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/journal/trips").header("Authorization", bob)).andExpect(jsonPath("$.items").isEmpty());
        input.put("title", "Changed");
        mvc.perform(post("/api/journal/trips").header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/journal/trips/" + id).header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Changed"));
        mvc.perform(delete("/api/journal/trips/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/journal/trips/" + id).header("Authorization", alice)).andExpect(status().isNotFound());
    }

    @Test
    void onlyPersonalPlansAreListedAndForeignOrGroupSourcesCannotBeImported() throws Exception {
        UUID own = plan(alice, "My plan"); UUID other = plan(bob, "Not mine");
        UUID group = UUID.randomUUID();
        jdbc.update("INSERT INTO trip_plan_groups(id,name,created_by_user_id,created_at) VALUES (?, 'Group', ?, now())", group, aliceId);
        UUID groupPlan = plan(alice, "Group plan");
        jdbc.update("UPDATE trip_plans SET user_id = NULL, group_id = ?, created_by_user_id = ? WHERE id = ?", group, aliceId, groupPlan);
        mvc.perform(get("/api/trip-plans").header("Authorization", alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(own.toString()));
        for (UUID source : List.of(other, groupPlan, UUID.randomUUID())) {
            var input = input(UUID.randomUUID()); input.put("sourcePlanId", source); input.put("startMode", "CUSTOM"); input.put("keepNights", true);
            mvc.perform(post("/api/journal/trips").header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void oldPlanImportComputesNightsAllowsRepeatImportsAndSurvivesSourceDeletion() throws Exception {
        UUID source = plan(alice, "Old plan");
        var input = input(UUID.randomUUID()); input.put("sourcePlanId", source); input.put("startMode", "CUSTOM"); input.put("keepNights", true); input.remove("endDate");
        JsonNode first = create(input, alice);
        assertThat(first.get("endDate").asString()).isEqualTo("2028-03-03");
        input.put("requestId", UUID.randomUUID()); create(input, alice);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_trips", Integer.class)).isEqualTo(2);
        input.put("requestId", UUID.randomUUID()); input.put("startMode", "PLANNED");
        mvc.perform(post("/api/journal/trips").header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM trip_plans WHERE id = ?", source);
        mvc.perform(get("/api/journal/trips/" + first.get("id").asString()).header("Authorization", alice)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Weekend")).andExpect(jsonPath("$.destinationCity").value("Cluj-Napoca"));
    }

    @Test
    void invalidDatesDescriptionsCatalogAndMissingImportChoicesAreRejected() throws Exception {
        for (String invalid : List.of("past", "end", "description", "country")) {
            var input = input(UUID.randomUUID());
            switch (invalid) {
                case "past" -> input.put("startDate", "2028-02-27");
                case "end" -> input.put("endDate", "2028-02-27");
                case "description" -> input.put("description", " ");
                case "country" -> input.put("countryCode", "US");
            }
            mvc.perform(post("/api/journal/trips").header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        }
        UUID source = plan(alice, "Source");
        var input = input(UUID.randomUUID()); input.put("sourcePlanId", source); input.put("startMode", "CUSTOM");
        mvc.perform(post("/api/journal/trips").header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        input.put("keepNights", false); input.remove("endDate");
        mvc.perform(post("/api/journal/trips").header("Authorization", alice).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
    }

    private Map<String,Object> input(UUID id) {
        var input = new HashMap<String,Object>(); input.put("requestId", id); input.put("title", "Weekend"); input.put("description", "Private trip");
        input.put("destinationCity", "Cluj-Napoca"); input.put("countryCode", "RO"); input.put("startDate", "2028-02-28"); input.put("endDate", "2028-02-28"); return input;
    }
    private JsonNode create(Map<String,Object> input, String token) throws Exception {
        return json.readTree(mvc.perform(post("/api/journal/trips").header("Authorization", token).contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private UUID plan(String token, String title) throws Exception {
        String body = """
                {"title":"%s","description":null,"transportMode":"BUS","departureCity":"Cluj-Napoca","departureCountryCode":"RO",
                "destinationCity":"Cluj-Napoca","countryCode":"RO","departureFrom":"2025-01-01T10:00:00","departureUntil":"2025-01-02T10:00:00",
                "stayingNights":4,"ticketOption":"ROUND_TRIP","transportBudget":100,"accommodationBudget":200,"adults":1}
                """.formatted(title);
        return UUID.fromString(json.readTree(mvc.perform(post("/api/trip-plans").header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString());
    }
    private String user(UUID id, String name) {
        jdbc.update("INSERT INTO users(id,username,created_at,updated_at) VALUES (?,?,now(),now())", id, name);
        var claims = JwtClaimsSet.builder().issuer("walkaround").subject(id.toString()).audience(List.of("walkaround-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("token_use", "access").build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}