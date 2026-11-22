package services.applicationapi.poicomments.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.poicomments.service.PoiCommentService;

@RestController
@RequestMapping("/api/poi-comments")
public class PoiCommentController {
    public record CreateRequest(@NotBlank @Size(max = 160) String poiId, @NotBlank @Size(max = 2000) String body) {}
    public record EditRequest(@NotBlank @Size(max = 2000) String body) {}
    public record ReportRequest(@NotBlank @Size(max = 500) String reason) {}

    private final PoiCommentService comments;

    public PoiCommentController(PoiCommentService comments) {
        this.comments = comments;
    }

    @GetMapping
    public ResponseEntity<PoiCommentService.Page> list(@RequestParam String poiId,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comments.list(poiId, cursor));
    }

    @PostMapping
    public ResponseEntity<PoiCommentService.Comment> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(comments.create(request.poiId(), UUID.fromString(jwt.getSubject()), request.body()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PoiCommentService.Comment> edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody EditRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(comments.edit(id, UUID.fromString(jwt.getSubject()), request.body()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        comments.delete(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reports")
    public ResponseEntity<Void> report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ReportRequest request) {
        comments.report(id, UUID.fromString(jwt.getSubject()), request.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<PoiCommentService.ReportPage> reports(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(comments.reports(UUID.fromString(jwt.getSubject()), cursor));
    }
}