package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.dto.TripGroupInvitePreview;
import services.applicationapi.tripplan.dto.TripGroupInviteResponse;
import services.applicationapi.tripplan.dto.TripGroupInviteSummary;
import services.applicationapi.tripplan.dto.TripGroupInviteTokenRequest;
import services.applicationapi.tripplan.dto.TripGroupResponse;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.tripplan.service.tripgroup.TripGroupInviteService;
import services.applicationapi.tripplan.service.tripgroup.TripGroupService;

@RestController
@RequestMapping("/api/trip-plan-groups")
public class TripGroupController {

    private final TripGroupService groups;
    private final TripGroupInviteService invitations;

    public TripGroupController(TripGroupService groups, TripGroupInviteService invitations) {
        this.groups = groups;
        this.invitations = invitations;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TripGroupResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestPart(required = false) MultipartFile picture
    ) {
        TripGroupResponse created = groups.create(currentUserId(jwt), name, description, picture);
        return ResponseEntity.created(URI.create("/api/trip-plan-groups/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping(value = "/{id}/picture", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> picture(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_JPEG)
                .body(groups.picture(id, currentUserId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripGroupResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(groups.get(id, currentUserId(jwt)));
    }

    @GetMapping
    public ResponseEntity<List<TripGroupResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(groups.list(currentUserId(jwt)));
    }

    @PostMapping("/{id}/invitations")
    public ResponseEntity<TripGroupInviteResponse> createInvitation(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(invitations.create(id, currentUserId(jwt)));
    }

    @GetMapping("/{id}/invitations")
    public ResponseEntity<List<TripGroupInviteSummary>> invitations(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(invitations.list(id, currentUserId(jwt)));
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID id,
                                                  @PathVariable UUID invitationId) {
        invitations.revoke(id, invitationId, currentUserId(jwt));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/invitations/preview")
    public ResponseEntity<TripGroupInvitePreview> previewInvitation(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TripGroupInviteTokenRequest request) {
        currentUserId(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(invitations.preview(request.token()));
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<TripGroupResponse> acceptInvitation(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TripGroupInviteTokenRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(invitations.accept(request.token(), currentUserId(jwt)));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new TripPlanAuthenticationException();
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new TripPlanAuthenticationException();
        }
    }
}
