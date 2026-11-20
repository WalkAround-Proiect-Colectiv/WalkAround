package services.realtimeservice.chat;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GroupChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String CONVERSATION_ID = "conversationId";
    static final String USER_ID = "userId";
    static final String EXPIRES_AT = "expiresAt";

    private final JwtDecoder decoder;

    public GroupChatHandshakeInterceptor(@Qualifier("groupChatJwtDecoder") JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Jwt jwt = decoder.decode(token);
            UUID conversationId = UUID.fromString(jwt.getClaimAsString("conversation_id"));
            UUID userId = UUID.fromString(jwt.getSubject());
            attributes.put(CONVERSATION_ID, conversationId);
            attributes.put(USER_ID, userId);
            attributes.put(EXPIRES_AT, jwt.getExpiresAt());
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
