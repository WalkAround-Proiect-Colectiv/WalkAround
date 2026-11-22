package services.applicationapi.poicomments;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import services.applicationapi.configuration.SecurityConfiguration;
import services.applicationapi.poicomments.controllers.PoiCommentController;
import services.applicationapi.poicomments.service.PoiCommentService;

class PoiCommentSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private PoiCommentService comments;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {
        @Bean("applicationJwtDecoder") JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean PoiCommentService comments() { return mock(PoiCommentService.class); }
        @Bean PoiCommentController controller(PoiCommentService comments) { return new PoiCommentController(comments); }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        comments = context.getBean(PoiCommentService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void readingIsPublicButAllMutationsAndReportsAreProtected() throws Exception {
        when(comments.list("osm:n/123", null)).thenReturn(new PoiCommentService.Page(List.of(), null));
        mvc.perform(get("/api/poi-comments").param("poiId", "osm:n/123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(post("/api/poi-comments").contentType("application/json").content("{\"poiId\":\"osm:n/123\",\"body\":\"Tip\"}"))
                .andExpect(status().is4xxClientError());
        String comment = "/api/poi-comments/" + UUID.randomUUID();
        mvc.perform(put(comment).contentType("application/json").content("{\"body\":\"Changed\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(delete(comment)).andExpect(status().is4xxClientError());
        mvc.perform(post(comment + "/reports").contentType("application/json").content("{\"reason\":\"Spam\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/poi-comments/reports")).andExpect(status().is4xxClientError());
        verify(comments, never()).create(any(), any(), any());
    }

    @Test
    void authorComesFromJwtAndInvalidBodiesAreRejected() throws Exception {
        UUID author = UUID.randomUUID();
        mvc.perform(post("/api/poi-comments").with(jwt().jwt(token -> token.subject(author.toString())))
                .contentType("application/json").content("{\"poiId\":\"osm:n/123\",\"body\":\"Tip\"}"))
                .andExpect(status().isCreated());
        verify(comments).create("osm:n/123", author, "Tip");
        mvc.perform(post("/api/poi-comments").with(jwt().jwt(token -> token.subject(author.toString())))
                .contentType("application/json").content("{\"poiId\":\"osm:n/123\",\"body\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}