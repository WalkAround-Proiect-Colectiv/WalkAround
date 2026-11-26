package services.applicationapi.notifications.controllers;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.notifications.model.NotificationPage;
import services.applicationapi.notifications.service.NotificationAuthenticationException;
import services.applicationapi.notifications.service.NotificationInboxReader;

@RestController
@RequestMapping("/api/notifications")
public class NotificationInboxController {

    private final NotificationInboxReader inbox;

    public NotificationInboxController(NotificationInboxReader inbox) {
        this.inbox = inbox;
    }

    @GetMapping
    public ResponseEntity<NotificationPage> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(inbox.list(userId(jwt), before, limit));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("count", inbox.unreadCount(userId(jwt))));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        inbox.markRead(userId(jwt), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private UUID userId(Jwt jwt) {
        try {
            if (jwt != null && jwt.getSubject() != null) {
                return UUID.fromString(jwt.getSubject());
            }
        } catch (IllegalArgumentException ignored) {
            // An invalid principal is never trusted for an inbox query.
        }
        throw new NotificationAuthenticationException();
    }
}
