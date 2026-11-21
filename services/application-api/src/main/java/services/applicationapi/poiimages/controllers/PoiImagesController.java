package services.applicationapi.poiimages.controllers;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.poiimages.dto.PoiImageResponse;
import services.applicationapi.poiimages.service.PoiImageService;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageException;
import services.applicationapi.poiimages.service.exceptions.PoiImagesAuthenticationException;

/**
 * Photos attached to POIs. All endpoints require sign-in. The POI id is a query parameter because
 * OpenStreetMap ids such as {@code osm:n/123} contain a slash. The images themselves are served by ImageKit
 * through the signed URLs in each response, not by this API.
 */
@RestController
@RequestMapping("/api/poi-images")
public class PoiImagesController {

    private final PoiImageService service;

    public PoiImagesController(PoiImageService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PoiImageResponse>> list(@AuthenticationPrincipal Jwt jwt, @RequestParam String poiId) {
        UUID userId = currentUserId(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(poiId).stream().map(photo -> PoiImageResponse.of(photo, userId)).toList());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PoiImageResponse> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String poiId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "caption", required = false) String caption
    ) {
        UUID userId = currentUserId(jwt);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw InvalidPoiImageException.invalidFile();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(PoiImageResponse.of(service.add(userId, poiId, caption, bytes), userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(currentUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new PoiImagesAuthenticationException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new PoiImagesAuthenticationException();
        }
    }
}
