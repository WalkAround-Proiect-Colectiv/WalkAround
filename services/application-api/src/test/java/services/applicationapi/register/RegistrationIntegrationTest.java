package services.applicationapi.register;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import services.applicationapi.register.service.*;
import services.applicationapi.user.repository.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONSUMER_GROUP_ID=registration-test", "SERVER_PORT=0",
        "app.registration.google.client-ids=test-client",
        "app.registration.allowed-origins=http://localhost:8082"
})
@Testcontainers(disabledWithoutDocker = true)
class RegistrationIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean GoogleIdentityVerifier verifier;
    @Autowired RegistrationService service;
    @Autowired UserRepository users;
    @Autowired UserIdentityRepository identities;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void clearDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        identities.deleteAll();
        users.deleteAll();
        when(verifier.verify("valid")).thenReturn(
                new GoogleIdentity("subject-123", "alex@example.com", "Alex", "Pop", "https://example.com/photo"));
    }

    @Test
    void publicRegistrationEndpointValidatesRequestsAndReturnsCreatedProfile() throws Exception {
        mvc.perform(post("/api/registrations/google").contentType("application/json")
                .content("{\"idToken\":\"valid\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.firstName").value("Alex"))
                .andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.idToken").doesNotExist());
        mvc.perform(post("/api/registrations/google").contentType("application/json")
                .content("{\"idToken\":\"valid\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/registrations/google").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/registrations/google").contentType("application/json")
                .content("{\"idToken\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidTokenOverHttpAndKeepsOtherEndpointsProtected() throws Exception {
        when(verifier.verify("invalid")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid Google identity token"));
        mvc.perform(post("/api/registrations/google").contentType("application/json")
                .content("{\"idToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid Google identity token"));
        mvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    void allowsOnlyConfiguredBrowserOrigin() throws Exception {
        mvc.perform(options("/api/registrations/google").header("Origin", "http://localhost:8082")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8082"));
        mvc.perform(options("/api/registrations/google").header("Origin", "https://attacker.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void migratesAndRegistersGoogleProfile() {
        var response = service.registerWithGoogle("valid");
        assertThat(response.id()).isNotNull();
        assertThat(response.firstName()).isEqualTo("Alex");
        assertThat(response.username()).isNull();
        assertThat(users.count()).isEqualTo(1);
        assertThat(identities.findAll().getFirst().getProviderSubject()).isEqualTo("subject-123");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateWithoutChangingProfile() {
        var first = service.registerWithGoogle("valid");
        jdbc.update("UPDATE users SET first_name = 'Edited' WHERE id = ?", first.id());
        assertThatThrownBy(() -> service.registerWithGoogle("valid"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findById(first.id()).orElseThrow().getFirstName()).isEqualTo("Edited");
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneUserAndIdentity() throws Exception {
        var barrier = new CyclicBarrier(2);
        when(verifier.verify("valid")).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return new GoogleIdentity("subject-123", "alex@example.com", "Alex", null, null);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> register = () -> {
                try { service.registerWithGoogle("valid"); return 201; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            };
            var first = executor.submit(register);
            var second = executor.submit(register);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(identities.count()).isEqualTo(1);
    }

    @Test
    void rejectedIdentityDoesNotWriteAnything() {
        when(verifier.verify("invalid")).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.registerWithGoogle("invalid"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(users.count()).isZero();
    }

    @Test
    void doesNotMergeDifferentSubjectsByEmail() {
        service.registerWithGoogle("valid");
        when(verifier.verify("second")).thenReturn(
                new GoogleIdentity("different-subject", "alex@example.com", null, null, null));
        service.registerWithGoogle("second");
        assertThat(users.count()).isEqualTo(2);
    }
}
