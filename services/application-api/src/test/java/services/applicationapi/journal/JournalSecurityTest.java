package services.applicationapi.journal;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import services.applicationapi.configuration.SecurityConfiguration;
import services.applicationapi.journal.controllers.JournalTripController;
import services.applicationapi.journal.service.JournalTripService;

class JournalSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfiguration.class)
    static class Config {
        @Bean("applicationJwtDecoder") JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean JournalTripService service() { return mock(JournalTripService.class); }
        @Bean JournalTripController controller(JournalTripService service) { return new JournalTripController(service); }
    }
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JournalTripService service;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh(); service = context.getBean(JournalTripService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    @Test
    void everyJournalEndpointRequiresAuthentication() throws Exception {
        String path = "/api/journal/trips/" + UUID.randomUUID();
        mvc.perform(get("/api/journal/trips")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/journal/trips/settings")).andExpect(status().is4xxClientError());
        mvc.perform(get(path)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/journal/trips").contentType("application/json").content("{}" )).andExpect(status().is4xxClientError());
        mvc.perform(put(path).contentType("application/json").content("{}" )).andExpect(status().is4xxClientError());
        mvc.perform(delete(path)).andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    void authorComesFromJwtAndMissingDescriptionIsRejected() throws Exception {
        UUID user = UUID.randomUUID();
        when(service.today()).thenReturn(LocalDate.of(2028, 2, 28));
        mvc.perform(get("/api/journal/trips/settings").with(jwt().jwt(token -> token.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.today").value("2028-02-28"));
        String body = """
                {"requestId":"%s","title":"Weekend","description":" ","destinationCity":"Cluj-Napoca",
                "countryCode":"RO","startDate":"2028-02-28","endDate":"2028-02-28"}
                """.formatted(UUID.randomUUID());
        mvc.perform(post("/api/journal/trips").with(jwt().jwt(token -> token.subject(user.toString())))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/journal/trips").with(jwt().jwt(token -> token.subject(user.toString())))
                .contentType("application/json").content(body.replace("\"description\":\" \"", "\"description\":\"Private\"")))
                .andExpect(status().isCreated());
        verify(service).create(eq(user), argThat(request -> "Private".equals(request.description())));
    }
}