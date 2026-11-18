package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.GroupChatMessageRequest;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import services.applicationapi.tripplan.dto.GroupChatSocketTicket;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.tripplan.service.groupchat.GroupChatService;

@RestController
@RequestMapping("/api/trip-plan-groups/{groupId}/chat")
public class GroupChatController {

    private final GroupChatService chat;

    public GroupChatController(GroupChatService chat) {
        this.chat = chat;
    }

    @PostMapping("/messages")
    public ResponseEntity<GroupChatMessageResponse> send(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID groupId,
                                                         @Valid @RequestBody GroupChatMessageRequest request) {
        GroupChatMessageResponse saved = chat.send(groupId, userId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(saved);
    }

    @GetMapping("/messages")
    public ResponseEntity<List<GroupChatMessageResponse>> messages(@AuthenticationPrincipal Jwt jwt,
                                                                   @PathVariable UUID groupId,
                                                                   @RequestParam(required = false) Long afterSequence,
                                                                   @RequestParam(required = false) Long beforeSequence,
                                                                   @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(chat.messages(groupId, userId(jwt), afterSequence, beforeSequence, limit));
    }

    @GetMapping("/socket-ticket")
    public ResponseEntity<GroupChatSocketTicket> socketTicket(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable UUID groupId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(chat.socketTicket(groupId, userId(jwt)));
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new TripPlanAuthenticationException();
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new TripPlanAuthenticationException();
        }
    }
}
