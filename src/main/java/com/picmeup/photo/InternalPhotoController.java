package com.picmeup.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/photos")
public class InternalPhotoController {

    private static final Logger log = LoggerFactory.getLogger(InternalPhotoController.class);

    private final PhotoRepository photoRepository;
    private final String callbackSecret;

    public InternalPhotoController(PhotoRepository photoRepository,
                                   @Value("${lambda.callback-secret:}") String callbackSecret) {
        this.photoRepository = photoRepository;
        this.callbackSecret = callbackSecret;
    }

    @PostMapping("/{photoId}/processed")
    @Transactional
    public ResponseEntity<Void> photoProcessed(
            @PathVariable UUID photoId,
            @RequestHeader("X-Callback-Secret") String secret,
            @RequestBody ProcessedPayload payload) {

        if (!callbackSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            return ResponseEntity.notFound().build();
        }

        photo.markActive(payload.originalS3Key(), payload.thumbnailS3Key(),
                payload.faceIds() != null ? payload.faceIds().toArray(String[]::new) : new String[0]);
        photoRepository.save(photo);
        log.info("Photo {} processed by Lambda", photoId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{photoId}/failed")
    @Transactional
    public ResponseEntity<Void> photoFailed(
            @PathVariable UUID photoId,
            @RequestHeader("X-Callback-Secret") String secret) {

        if (!callbackSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            return ResponseEntity.notFound().build();
        }

        photo.markFailed();
        photoRepository.save(photo);
        log.info("Photo {} marked as failed by Lambda", photoId);

        return ResponseEntity.ok().build();
    }

    record ProcessedPayload(String originalS3Key, String thumbnailS3Key, List<String> faceIds) {}
}
