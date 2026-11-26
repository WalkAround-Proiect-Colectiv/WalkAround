package services.applicationapi.tripplan;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.annotation.DirtiesContext;
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
import org.testcontainers.utility.DockerImageName;
import services.applicationapi.poiimages.service.PoiImageStorage;
import services.applicationapi.tripplan.service.groupchat.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "KAFKA_BOOTSTRAP_SERVERS=localhost:9092", "CONSUMER_GROUP_ID=reply-test", "SERVER_PORT=0",
        "ROUTING_SERVICE_URL=http://localhost:1", "SERP_API_KEY=test-key", "GEONAMES_USERNAME=test",
        "app.login.signing-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.events.provider=recorded", "app.events.reminders-enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupChatReplyIntegrationTest {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getProperty("group.chat.test-image", "postgres:18")).asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("applicationJwtEncoder") JwtEncoder encoder;
    @MockitoBean GroupChatOutboxRelay relay;
    @MockitoBean GroupChatImageStorage storage;
    @MockitoBean GroupChatImageNormalizer normalizer;
    @MockitoBean PoiImageStorage poiStorage;
    private MockMvc mvc;
    private UUID group;
    private UUID otherGroup;
    private String alice;
    private String bob;

    @BeforeEach
    void fixtures() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE users CASCADE");
        UUID user = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, created_at, updated_at) VALUES (?, 'Alice', now(), now()), (?, 'Bob', now(), now())", user, outsider);
        group = createGroup(user);
        otherGroup = createGroup(user);
        alice = token(user);
        bob = token(outsider);
        when(normalizer.normalize(any())).thenReturn(new GroupChatImageNormalizer.NormalizedImage(new byte[]{1, 2, 3}, "a".repeat(64), 16, 16));
        when(storage.upload(any(), any(), any())).thenAnswer(call -> new GroupChatImageStorage.UploadedImage(
                "file_" + call.getArgument(1), "/test/" + call.getArgument(1) + ".jpg"));
        when(storage.signedUrl(anyString())).thenReturn("https://images.example/private.jpg?signature=test");
    }

    @Test
    void textReplySurvivesHistoryPaginationCatchUpAndOutboxDelivery() throws Exception {
        JsonNode parent = send(group, UUID.randomUUID(), "Meet at the entrance", "TEXT", null, null);
        UUID parentId = UUID.fromString(parent.get("id").asString());
        JsonNode reply = send(group, UUID.randomUUID(), "See you there", "TEXT", null, parentId);
        assertQuote(reply, parentId, "TEXT", "Meet at the entrance");
        mvc.perform(get(path(group) + "/messages").header("Authorization", alice).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].replyTo.messageId").value(parentId.toString()))
                .andExpect(jsonPath("$[0].replyTo.preview").value("Meet at the entrance"));
        mvc.perform(get(path(group) + "/events").header("Authorization", alice).param("afterEventSequence", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].message.replyTo.messageId").value(parentId.toString()));
        JsonNode payload = json.readTree(jdbc.queryForObject("SELECT payload FROM trip_group_chat_outbox WHERE message_id = ?",
                String.class, UUID.fromString(reply.get("id").asString())));
        assertQuote(payload, parentId, "TEXT", "Meet at the entrance");
        assertThat(payload.get("conversationId").asString()).isEqualTo(group.toString());
    }

    @Test
    void sameIdRetryIsIdempotentButChangingTheReplyTargetConflicts() throws Exception {
        UUID first = UUID.fromString(send(group, UUID.randomUUID(), "First", "TEXT", null, null).get("id").asString());
        UUID second = UUID.fromString(send(group, UUID.randomUUID(), "Second", "TEXT", null, null).get("id").asString());
        UUID id = UUID.randomUUID();
        send(group, id, "Answer", "TEXT", null, first);
        send(group, id, "Answer", "TEXT", null, first);
        mvc.perform(post(path(group) + "/messages").header("Authorization", alice).contentType("application/json")
                .content(request(id, "Answer", "TEXT", null, second))).andExpect(status().isConflict());
        mvc.perform(post(path(group) + "/messages").header("Authorization", alice).contentType("application/json")
                .content(request(id, "Answer", "TEXT", null, null))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_group_chat_outbox WHERE message_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void unknownCrossGroupAndSelfTargetsFailWithoutLeakingMessages() throws Exception {
        UUID foreign = UUID.fromString(send(otherGroup, UUID.randomUUID(), "Private to the other group", "TEXT", null, null).get("id").asString());
        UUID self = UUID.randomUUID();
        for (UUID target : List.of(foreign, UUID.randomUUID(), self)) {
            mvc.perform(post(path(group) + "/messages").header("Authorization", alice).contentType("application/json")
                    .content(request(self, "Answer", "TEXT", null, target))).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path(group) + "/messages").header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(post(path(group) + "/messages").header("Authorization", bob).contentType("application/json")
                .content(request(UUID.randomUUID(), "Answer", "TEXT", null, foreign))).andExpect(status().isNotFound());
        mvc.perform(post(path(group) + "/messages").contentType("application/json")
                .content(request(UUID.randomUUID(), "Answer", "TEXT", null, foreign))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_group_chat_messages WHERE conversation_id = ?", Integer.class, group)).isZero();
    }

    @Test
    void pollRepliesAndVotesPreserveTheQuoteInEveryEvent() throws Exception {
        UUID parent = UUID.fromString(send(group, UUID.randomUUID(), "Which day?", "TEXT", null, null).get("id").asString());
        JsonNode poll = send(group, UUID.randomUUID(), "Choose a day", "POLL", List.of("Monday", "Tuesday"), parent);
        UUID pollId = UUID.fromString(poll.get("id").asString());
        assertQuote(poll, parent, "TEXT", "Which day?");
        mvc.perform(post(path(group) + "/messages/" + pollId + "/votes").header("Authorization", alice)
                .contentType("application/json").content(json.writeValueAsString(Map.of("optionId", poll.get("poll").get("options").get(0).get("id").asString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replyTo.messageId").value(parent.toString()));
        for (String payload : jdbc.queryForList("SELECT payload FROM trip_group_chat_outbox WHERE message_id = ?", String.class, pollId)) {
            assertQuote(json.readTree(payload), parent, "TEXT", "Which day?");
        }
        assertQuote(send(group, UUID.randomUUID(), "Monday works", "TEXT", null, pollId), pollId, "POLL", "Poll: Choose a day");
    }

    @Test
    void imageRepliesAreIdempotentAndQuotesDoNotExposeSignedUrls() throws Exception {
        UUID parent = UUID.fromString(send(group, UUID.randomUUID(), "Send a photo", "TEXT", null, null).get("id").asString());
        UUID id = UUID.randomUUID();
        JsonNode image = json.readTree(mvc.perform(image(id, parent).header("Authorization", alice))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.replyTo.messageId").value(parent.toString()))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(image(id, parent).header("Authorization", alice)).andExpect(status().isCreated());
        mvc.perform(image(id, null).header("Authorization", alice)).andExpect(status().isConflict());
        verify(storage, times(1)).upload(any(), eq(id), any());
        assertQuote(send(group, UUID.randomUUID(), "Nice photo", "TEXT", null, id), id, "IMAGE", "Photo");
        JsonNode payload = json.readTree(jdbc.queryForObject("SELECT payload FROM trip_group_chat_outbox WHERE message_id = ?", String.class, id));
        assertQuote(payload, parent, "TEXT", "Send a photo");
        assertThat(image.get("replyTo").has("image")).isFalse();
    }

    @Test
    void databaseConstraintRejectsCrossConversationLinksEvenWithoutTheService() throws Exception {
        UUID parent = UUID.fromString(send(otherGroup, UUID.randomUUID(), "Other group", "TEXT", null, null).get("id").asString());
        jdbc.update("INSERT INTO trip_group_chat_conversations(group_id) VALUES (?) ON CONFLICT DO NOTHING", group);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO trip_group_chat_messages(id, conversation_id, sequence, body, message_type, created_at, reply_to_message_id)
                VALUES (?, ?, 999, 'Invalid cross-group link', 'TEXT', now(), ?)
                """, UUID.randomUUID(), group, parent)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private JsonNode send(UUID groupId, UUID id, String body, String type, List<String> options, UUID parent) throws Exception {
        return json.readTree(mvc.perform(post(path(groupId) + "/messages").header("Authorization", alice)
                .contentType("application/json").content(request(id, body, type, options, parent)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String request(UUID id, String body, String type, List<String> options, UUID parent) {
        var values = new java.util.HashMap<String, Object>();
        values.put("messageId", id); values.put("body", body); values.put("messageType", type);
        values.put("options", options); values.put("replyToMessageId", parent);
        return json.writeValueAsString(values);
    }

    private MockMultipartHttpServletRequestBuilder image(UUID id, UUID parent) {
        var request = multipart(path(group) + "/messages/images")
                .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                .param("messageId", id.toString()).param("caption", "A photo");
        if (parent != null) request.param("replyToMessageId", parent.toString());
        return request;
    }

    private void assertQuote(JsonNode message, UUID parent, String type, String preview) {
        assertThat(message.get("replyTo").get("messageId").asString()).isEqualTo(parent.toString());
        assertThat(message.get("replyTo").get("messageType").asString()).isEqualTo(type);
        assertThat(message.get("replyTo").get("preview").asString()).isEqualTo(preview);
        assertThat(message.get("replyTo").get("senderName").asString()).isEqualTo("Alice");
    }

    private UUID createGroup(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO trip_plan_groups(id, name, created_by_user_id, created_at) VALUES (?, 'Reply test', ?, now())", id, owner);
        jdbc.update("INSERT INTO trip_plan_group_members(id, group_id, user_id, role, joined_at) VALUES (?, ?, ?, 'ADMIN', now())", UUID.randomUUID(), id, owner);
        return id;
    }

    private String token(UUID user) {
        var claims = JwtClaimsSet.builder().issuer("walkaround").subject(user.toString()).audience(List.of("walkaround-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).claim("token_use", "access").build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String path(UUID groupId) { return "/api/trip-plan-groups/" + groupId + "/chat"; }
}