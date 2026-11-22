package services.applicationapi.poicomments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import services.applicationapi.poiimages.service.PoiImageStorage;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONSUMER_GROUP_ID=poi-comments-test", "SERVER_PORT=0",
        "ROUTING_SERVICE_URL=http://localhost:1", "SERP_API_KEY=test-key", "GEONAMES_USERNAME=test",
        "app.login.signing-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.events.provider=recorded", "app.events.reminders-enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.fail-fast=false",
        "spring.kafka.admin.auto-create=false",
        "POI_COMMENTS_MODERATOR_IDS=33333333-3333-4333-8333-333333333333"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PoiCommentFlowIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getProperty("poi.comments.test-image", "postgres:18")).asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("recommendation.poi-database-url", () -> "");
    }

    @MockitoBean PoiImageStorage imageStorage;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("applicationJwtEncoder") JwtEncoder jwtEncoder;
    private MockMvc mvc;
    private String alice;
    private String bob;
    private String moderator;

    @BeforeEach
    void fixtures() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS places; CREATE SCHEMA IF NOT EXISTS osm");
        jdbc.execute("CREATE TABLE IF NOT EXISTS places.published_pois(id uuid PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS places.published_osm_references(poi_id uuid NOT NULL, value text NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS osm.features(osm_type text, osm_id bigint, kind text, minzoom integer)");
        jdbc.execute("TRUNCATE poi_comment_subjects, users, places.published_pois, places.published_osm_references, osm.features CASCADE");
        jdbc.execute("INSERT INTO osm.features VALUES ('N', 123, 'poi', 15)");
        alice = user(UUID.randomUUID(), "Alice");
        bob = user(UUID.randomUUID(), "Bob");
        moderator = user(UUID.fromString("33333333-3333-4333-8333-333333333333"), "Moderator");
    }

    @Test
    void photosAndCommentsMigrateTogetherAndCommentsPersistOverHttp() throws Exception {
        assertThat(jdbc.queryForObject("SELECT to_regclass('poi_images')::text", String.class)).isEqualTo("poi_images");
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version IN ('18', '19') ORDER BY version", String.class))
                .containsExactly("18", "19");
        mvc.perform(get("/api/poi-comments").param("poiId", "osm:n/123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        String id = createComment();
        mvc.perform(get("/api/poi-comments").param("poiId", "osm:n/123"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].body").value("Entrance on the left"))
                .andExpect(jsonPath("$.items[0].authorName").value("Alice"));
        mvc.perform(put("/api/poi-comments/" + id).header("Authorization", alice)
                .contentType("application/json").content("{\"body\":\"Updated tip\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Updated tip"));
        mvc.perform(delete("/api/poi-comments/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/poi-comments").param("poiId", "osm:n/123"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void authenticationOwnershipValidationAndModerationAreEnforcedOverHttp() throws Exception {
        mvc.perform(post("/api/poi-comments").contentType("application/json")
                .content("{\"poiId\":\"osm:n/123\",\"body\":\"Tip\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/poi-comments").header("Authorization", alice).contentType("application/json")
                .content("{\"poiId\":\"selected-coordinate\",\"body\":\"Tip\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/poi-comments").param("poiId", "osm:n/999"))
                .andExpect(status().isNotFound());
        String id = createComment();
        mvc.perform(put("/api/poi-comments/" + id).header("Authorization", bob)
                .contentType("application/json").content("{\"body\":\"Changed by another user\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/poi-comments/" + id).header("Authorization", bob)).andExpect(status().isForbidden());
        mvc.perform(post("/api/poi-comments/" + id + "/reports").header("Authorization", bob)
                .contentType("application/json").content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/poi-comments/reports").header("Authorization", bob)).andExpect(status().isForbidden());
        mvc.perform(get("/api/poi-comments/reports").header("Authorization", moderator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].reason").value("Spam"));
        mvc.perform(delete("/api/poi-comments/" + id).header("Authorization", moderator)).andExpect(status().isNoContent());
    }

    private String createComment() throws Exception {
        String response = mvc.perform(post("/api/poi-comments").header("Authorization", alice)
                .contentType("application/json").content("{\"poiId\":\"osm:n/123\",\"body\":\"  Entrance on the left  \"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asString();
    }

    private String user(UUID id, String username) {
        jdbc.update("INSERT INTO users(id, username, first_name, created_at, updated_at) VALUES (?, ?, ?, now(), now())", id, username, username);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("walkaround").subject(id.toString())
                .audience(List.of("walkaround-api")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("token_use", "access").build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}