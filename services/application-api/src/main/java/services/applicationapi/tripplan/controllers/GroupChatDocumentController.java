package services.applicationapi.tripplan.controllers;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import services.applicationapi.tripplan.service.groupchat.GroupChatDocumentService;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;

@RestController
@RequestMapping("/api/trip-plan-groups/{groupId}/chat/messages")
public class GroupChatDocumentController {
    private final GroupChatDocumentService documents;
    public GroupChatDocumentController(GroupChatDocumentService documents) { this.documents = documents; }

    @PostMapping(path = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<GroupChatMessageResponse> upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupId,
            @RequestParam UUID messageId, @RequestParam(required = false) String caption,
            @RequestParam(required = false) UUID replyToMessageId, @RequestPart("document") MultipartFile document) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(documents.send(groupId, userId(jwt), messageId, caption, document, replyToMessageId));
    }
    @GetMapping("/{messageId}/document-url")
    public ResponseEntity<GroupChatDocumentService.DownloadLink> download(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID groupId, @PathVariable UUID messageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(documents.downloadLink(groupId, userId(jwt), messageId));
    }
    private UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new TripPlanAuthenticationException();
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) { throw new TripPlanAuthenticationException(); }
    }
}
