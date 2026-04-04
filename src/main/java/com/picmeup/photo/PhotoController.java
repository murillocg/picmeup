package com.picmeup.photo;

import com.picmeup.photo.dto.PhotoResponse;
import com.picmeup.photo.dto.PhotoUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import java.util.List;

@RestController
@RequestMapping("/api/events/{slug}/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PhotoUploadResponse>> uploadPhotos(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "") String photographerEmail,
            @RequestParam(required = false, defaultValue = "") String photographerName,
            @RequestParam("files") List<MultipartFile> files) {

        if (files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (files.size() > 50) {
            return ResponseEntity.badRequest().build();
        }

        var photos = photoService.uploadPhotos(slug, photographerEmail, photographerName, files);
        var response = photos.stream()
                .map(PhotoUploadResponse::from)
                .toList();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PhotoResponse>> listPhotos(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "false") boolean includeOriginal) {
        var photos = photoService.getActivePhotos(slug);
        var response = photos.stream()
                .map(photo -> {
                    String thumbnailUrl = photoService.getThumbnailUrl(photo);
                    String originalUrl = includeOriginal ? photoService.getOriginalUrl(photo) : null;
                    return PhotoResponse.from(photo, thumbnailUrl, originalUrl);
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable String slug, @PathVariable UUID photoId) {
        photoService.deletePhoto(photoId);
        return ResponseEntity.noContent().build();
    }
}
