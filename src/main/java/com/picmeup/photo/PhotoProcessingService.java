package com.picmeup.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.StorageClass;

import java.util.UUID;

@Service
public class PhotoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingService.class);

    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;
    private final ImageProcessingService imageProcessingService;
    private final FaceRecognitionService faceRecognitionService;

    public PhotoProcessingService(PhotoRepository photoRepository,
                                  S3StorageService s3StorageService,
                                  ImageProcessingService imageProcessingService,
                                  FaceRecognitionService faceRecognitionService) {
        this.photoRepository = photoRepository;
        this.s3StorageService = s3StorageService;
        this.imageProcessingService = imageProcessingService;
        this.faceRecognitionService = faceRecognitionService;
    }

    @Async
    public void processPhotoAsync(UUID photoId, UUID eventId, MultipartFile file) {
        try {
            byte[] originalBytes = file.getBytes();
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            String originalKey = "originals/%s/%s.jpg".formatted(eventId, photoId);
            s3StorageService.uploadFile(originalKey, originalBytes, contentType, StorageClass.STANDARD_IA);

            byte[] watermarkedThumbnail = imageProcessingService.processPhoto(originalBytes);
            String thumbnailKey = "thumbnails/%s/%s.jpg".formatted(eventId, photoId);
            s3StorageService.uploadFile(thumbnailKey, watermarkedThumbnail, "image/jpeg");

            String[] faceIds = faceRecognitionService.indexFaces(eventId, photoId, originalKey);

            var photo = photoRepository.findById(photoId).orElse(null);
            if (photo != null) {
                photo.markActive(originalKey, thumbnailKey, faceIds);
                photoRepository.save(photo);
                log.info("Photo {} processed successfully", photoId);
            }
        } catch (Exception e) {
            log.error("Failed to process photo {}", photoId, e);
            var photo = photoRepository.findById(photoId).orElse(null);
            if (photo != null) {
                photo.markFailed();
                photoRepository.save(photo);
            }
        }
    }
}
