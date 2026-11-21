package services.applicationapi.poiimages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import services.applicationapi.poiimages.model.StoredPoiImage;
import services.applicationapi.poiimages.service.PoiImageStorage;
import services.applicationapi.poiimages.service.exceptions.PoiImageStorageException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The whole POI photo flow over HTTP against a real Postgres: upload, list, delete, limits. ImageKit is
 * replaced by a fake storage that records uploaded files, so no external service is called.
 */
@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONSUMER_GROUP_ID=poi-images-test", "SERVER_PORT=0",
        "ROUTING_SERVICE_URL=http://localhost:1", "SERP_API_KEY=test-key", "GEONAMES_USERNAME=test",
        "app.login.signing-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.events.provider=recorded", "app.events.reminders-enabled=false",
        "app.poi-images.max-per-user-per-poi=2"
})
@Testcontainers(disabledWithoutDocker = true)
class PoiImagesFlowIntegrationTest {

    private static final String POI = "osm:n/123456";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean PoiImageStorage storage;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired @Qualifier("applicationJwtEncoder") JwtEncoder jwtEncoder;

    private MockMvc mvc;
    private String alice;
    private String bob;
    private final List<byte[]> uploadedFiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM poi_images");
        jdbc.update("DELETE FROM users");
        alice = "Bearer " + accessToken(createUser("alice", "Alice"));
        bob = "Bearer " + accessToken(createUser(null, "Bob"));
        uploadedFiles.clear();
        when(storage.upload(any(), anyString(), any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            uploadedFiles.add(invocation.getArgument(2));
            return new StoredPoiImage("file_" + id.toString().replace("-", ""), "/walkaround/poi-images/test/" + id + ".jpg");
        });
        when(storage.imageUrl(anyString())).thenAnswer(invocation -> "https://ik.example/" + invocation.getArgument(0) + "?ik-s=x");
        when(storage.thumbnailUrl(anyString())).thenAnswer(invocation -> "https://ik.example/tr:w-320" + invocation.getArgument(0) + "?ik-s=x");
    }

    private UUID createUser(String username, String firstName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, first_name, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
                id, username, firstName);
        return id;
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

    private MockMultipartHttpServletRequestBuilder upload(String poiId, byte[] bytes, String caption) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/poi-images")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes));
        if (caption != null) {
            request.file(new MockMultipartFile("caption", "", "text/plain", caption.getBytes()));
        }
        request.param("poiId", poiId);
        return request;
    }

    @Test
    void requiresSignIn() throws Exception {
        mvc.perform(get("/api/poi-images").param("poiId", POI)).andExpect(status().isUnauthorized());
        mvc.perform(upload(POI, PoiImageTestImages.encode(40, 40, "jpeg"), null)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/poi-images/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsListsAndDeletesPhotosStoredInImageKit() throws Exception {
        JsonNode saved = objectMapper.readTree(mvc.perform(
                        upload(POI, PoiImageTestImages.jpegWithExif(2400, 1200), "  Main entrance  ").header("Authorization", alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poiId").value(POI))
                .andExpect(jsonPath("$.caption").value("Main entrance"))
                .andExpect(jsonPath("$.width").value(1600))
                .andExpect(jsonPath("$.height").value(800))
                .andExpect(jsonPath("$.uploadedByName").value("alice"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("https://ik.example/")))
                .andExpect(jsonPath("$.thumbnailUrl").value(org.hamcrest.Matchers.containsString("tr:w-320")))
                .andReturn().getResponse().getContentAsString());
        String id = saved.get("id").asString();

        // The file sent to ImageKit is the cleaned JPEG, and the database holds only the reference.
        assertThat(uploadedFiles).hasSize(1);
        assertThat(PoiImageTestImages.contains(uploadedFiles.getFirst(), "GPS-SECRET")).isFalse();
        assertThat(jdbc.queryForObject("SELECT imagekit_file_path FROM poi_images WHERE id = ?::uuid", String.class, id))
                .isEqualTo("/walkaround/poi-images/test/" + id + ".jpg");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'poi_images' AND data_type = 'bytea'",
                Integer.class)).isZero();

        // Bob sees Alice's photo, not marked as his.
        mvc.perform(get("/api/poi-images").param("poiId", POI).header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].mine").value(false));
        mvc.perform(get("/api/poi-images").param("poiId", "osm:n/999").header("Authorization", bob))
                .andExpect(jsonPath("$.length()").value(0));

        // Only the uploader can delete it; the ImageKit file is removed too.
        mvc.perform(delete("/api/poi-images/" + id).header("Authorization", bob)).andExpect(status().isForbidden());
        verify(storage, never()).delete(anyString());
        mvc.perform(delete("/api/poi-images/" + id).header("Authorization", alice)).andExpect(status().isNoContent());
        verify(storage).delete("file_" + id.replace("-", ""));
        mvc.perform(get("/api/poi-images").param("poiId", POI).header("Authorization", alice))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void limitsPhotosPerUserAndPlace() throws Exception {
        byte[] photo = PoiImageTestImages.encode(60, 60, "png");
        mvc.perform(upload(POI, photo, null).header("Authorization", alice)).andExpect(status().isCreated());
        mvc.perform(upload(POI, photo, null).header("Authorization", alice)).andExpect(status().isCreated());

        mvc.perform(upload(POI, photo, null).header("Authorization", alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can add up to 2 photos to this place."));
        mvc.perform(upload("osm:w/42", photo, null).header("Authorization", alice)).andExpect(status().isCreated());
        mvc.perform(upload(POI, photo, null).header("Authorization", bob))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadedByName").value("Bob"));
        assertThat(uploadedFiles).hasSize(4);
    }

    @Test
    void longAndShortOsmIdsShareOnePhotoList() throws Exception {
        mvc.perform(upload("osm:node/123456", PoiImageTestImages.encode(60, 60, "jpeg"), null).header("Authorization", alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poiId").value(POI));
        mvc.perform(get("/api/poi-images").param("poiId", POI).header("Authorization", bob))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void savesNothingWhenImageKitIsUnavailable() throws Exception {
        doThrow(PoiImageStorageException.unavailable(null)).when(storage).upload(any(), anyString(), any());

        mvc.perform(upload(POI, PoiImageTestImages.encode(60, 60, "jpeg"), null).header("Authorization", alice))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Photo storage is temporarily unavailable. Please try again."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poi_images", Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidRequestsBeforeUploadingAnything() throws Exception {
        byte[] photo = PoiImageTestImages.encode(60, 60, "jpeg");
        mvc.perform(upload("not a poi id", photo, null).header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/poi-images").param("poiId", "../etc").header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mvc.perform(upload(POI, "plain text".getBytes(), null).header("Authorization", alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose a valid JPEG or PNG photo."));
        mvc.perform(upload(POI, photo, "x".repeat(201)).header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/poi-images").param("poiId", POI).header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/poi-images/" + UUID.randomUUID()).header("Authorization", alice))
                .andExpect(status().isNotFound());
        assertThat(uploadedFiles).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poi_images", Integer.class)).isZero();
    }
}
