package services.realtimeservice.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer
{
    private final DangerousZoneSocketHandler socketHandler;
    public WebSocketConfig(DangerousZoneSocketHandler socketHandler)
    {
        this.socketHandler = socketHandler;
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry)
    {
        registry.addHandler(socketHandler, "/ws/dangerous-zones").setAllowedOrigins("*");
    }
}