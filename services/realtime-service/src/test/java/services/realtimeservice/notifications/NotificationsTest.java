package services.realtimeservice.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class NotificationsTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes();
    private static final NotificationProperties PROPERTIES = new NotificationProperties(
            Base64.getEncoder().encodeToString(SECRET), "walkaround", "walkaround-api", "event-reminders-topic");

    private final NotificationHandshakeInterceptor interceptor =
            new NotificationHandshakeInterceptor(new NotificationConfiguration().notificationJwtDecoder(PROPERTIES));

    private static String token(String subject, String audience, String tokenUse) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET, "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("walkaround")
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("token_use", tokenUse)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private boolean handshake(String query, Map<String, Object> attributes, ServerHttpResponse response) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8003/ws/notifications" + query));
        return interceptor.beforeHandshake(request, response, null, attributes);
    }

    @Test
    void acceptsAValidAccessTokenAndRemembersTheUser() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshake("?token=" + token(userId, "walkaround-api", "access"), attributes,
                mock(ServerHttpResponse.class));

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
    }

    @Test
    void rejectsMissingWrongAudienceAndRefreshTokens() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        String userId = UUID.randomUUID().toString();

        assertThat(handshake("", new HashMap<>(), response)).isFalse();
        assertThat(handshake("?token=garbage", new HashMap<>(), response)).isFalse();
        assertThat(handshake("?token=" + token(userId, "other-api", "access"), new HashMap<>(), response)).isFalse();
        assertThat(handshake("?token=" + token(userId, "walkaround-api", "refresh"), new HashMap<>(), response)).isFalse();
        verify(response, org.mockito.Mockito.atLeastOnce()).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private static WebSocketSession session(String id, String userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        if (userId != null) {
            attributes.put(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
        }
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void deliversRemindersOnlyToTheNamedUser() throws Exception {
        NotificationSocketHandler handler = new NotificationSocketHandler(JsonMapper.builder().build());
        WebSocketSession alicePhone = session("1", "alice");
        WebSocketSession aliceLaptop = session("2", "alice");
        WebSocketSession bob = session("3", "bob");
        handler.afterConnectionEstablished(alicePhone);
        handler.afterConnectionEstablished(aliceLaptop);
        handler.afterConnectionEstablished(bob);

        handler.consumeReminder("{\"type\":\"EVENT_REMINDER\",\"userId\":\"alice\",\"title\":\"Concert\"}");

        verify(alicePhone).sendMessage(any(TextMessage.class));
        verify(aliceLaptop).sendMessage(any(TextMessage.class));
        verify(bob, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void stopsDeliveringAfterTheConnectionCloses() {
        NotificationSocketHandler handler = new NotificationSocketHandler(JsonMapper.builder().build());
        WebSocketSession alice = session("1", "alice");
        handler.afterConnectionEstablished(alice);

        handler.afterConnectionClosed(alice, CloseStatus.NORMAL);

        assertThat(handler.deliver("alice", "{}")).isZero();
    }

    @Test
    void ignoresMalformedMessagesAndConnectionsWithoutAUser() throws Exception {
        NotificationSocketHandler handler = new NotificationSocketHandler(JsonMapper.builder().build());
        WebSocketSession anonymous = session("9", null);

        handler.afterConnectionEstablished(anonymous);
        handler.consumeReminder("not json");

        verify(anonymous).close(CloseStatus.POLICY_VIOLATION);
    }
}
