package services.realtimeservice.notifications;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers {@code /ws/notifications}; the existing dangerous-zone endpoint is configured separately. */
@Configuration
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationSocketHandler handler;
    private final NotificationHandshakeInterceptor interceptor;

    public NotificationWebSocketConfig(NotificationSocketHandler handler, NotificationHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }
}
