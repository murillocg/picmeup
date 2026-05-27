package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.photo.dto.PhotoResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{slug}")
public class SearchController {

    private final PhotoService photoService;
    private final EventRepository eventRepository;

    public SearchController(PhotoService photoService, EventRepository eventRepository) {
        this.photoService = photoService;
        this.eventRepository = eventRepository;
    }

    @PostMapping(value = "/search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PhotoResponse>> searchByFace(
            @PathVariable String slug,
            @RequestParam("selfie") MultipartFile selfie) throws IOException {

        if (selfie.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var matchedPhotos = photoService.searchByFace(slug, selfie.getBytes());
        var response = matchedPhotos.stream()
                .map(photo -> PhotoResponse.from(photo, photoService.getThumbnailUrl(photo)))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/free-downloads")
    public ResponseEntity<List<String>> getFreeDownloads(
            @PathVariable String slug,
            @RequestBody Map<String, List<String>> body) {

        var event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", slug));

        if (!event.isFree()) {
            return ResponseEntity.badRequest().build();
        }

        List<UUID> photoIds = body.get("photoIds").stream()
                .map(UUID::fromString)
                .toList();

        return ResponseEntity.ok(photoService.getFreeDownloadUrls(slug, photoIds));
    }
}
