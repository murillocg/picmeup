package com.picmeup.photo;

import com.picmeup.photo.dto.PhotoResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/events/{slug}/search")
public class SearchController {

    private final PhotoService photoService;

    public SearchController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
}
