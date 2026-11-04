package services.realtimeservice.configuration;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DangerousZoneSocketHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
    {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status)
    {
        sessions.remove(session);
    }

    @KafkaListener(topics = "dangerous-zones-topic", groupId = "${CONSUMER_GROUP_ID}")
    public void consumeDangerousZoneEvent(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try
                {
                    session.sendMessage(new TextMessage(message));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}