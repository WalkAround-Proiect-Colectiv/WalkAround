package services.realtimeservice.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
public class GroupChatWebSocketConfig implements WebSocketConfigurer {

    private final GroupChatSocketHandler handler;
    private final GroupChatHandshakeInterceptor interceptor;

    public GroupChatWebSocketConfig(GroupChatSocketHandler handler, GroupChatHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/group-chats")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }
}
