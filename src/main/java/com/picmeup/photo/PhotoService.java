package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    private final PhotoRepository photoRepository;
    private final PhotographerRepository photographerRepository;
    private final EventRepository eventRepository;
    private final S3StorageService s3StorageService;
    private final ImageProcessingService imageProcessingService;
    private final FaceRecognitionService faceRecognitionService;

    public PhotoService(PhotoRepository photoRepository,
                        PhotographerRepository photographerRepository,
                        EventRepository eventRepository,
                        S3StorageService s3StorageService,
                        ImageProcessingService imageProcessingService,
                        FaceRecognitionService faceRecognitionService) {
        this.photoRepository = photoRepository;
        this.photographerRepository = photographerRepository;
        this.eventRepository = eventRepository;
        this.s3StorageService = s3StorageService;
        this.imageProcessingService = imageProcessingService;
        this.faceRecognitionService = faceRecognitionService;
    }

    @Transactional
    public List<Photo> uploadPhotos(String eventSlug, String photographerEmail, String photographerName,
                                    List<MultipartFile> files) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        if (event.isExpired()) {
            throw new IllegalArgumentException("Cannot upload photos to an expired event");
        }

        var photographer = photographerRepository.findByEmail(photographerEmail)
                .orElseGet(() -> photographerRepository.save(new Photographer(photographerName, photographerEmail)));

        var photos = new ArrayList<Photo>();
        for (MultipartFile file : files) {
            var photo = new Photo(event, photographer);
            photoRepository.save(photo);
            photos.add(photo);

            processPhotoAsync(photo.getId(), event.getId(), file);
        }

        return photos;
    }

    @Async
    public void processPhotoAsync(UUID photoId, UUID eventId, MultipartFile file) {
        try {
            byte[] originalBytes = file.getBytes();
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            String originalKey = "originals/%s/%s.jpg".formatted(eventId, photoId);
            s3StorageService.uploadFile(originalKey, originalBytes, contentType);

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

    @Transactional(readOnly = true)
    public List<Photo> getActivePhotos(String eventSlug) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        return photoRepository.findByEventIdAndStatus(event.getId(), Photo.Status.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Photo> searchByFace(String eventSlug, byte[] selfieBytes) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        List<UUID> matchedPhotoIds = faceRecognitionService.searchByFace(event.getId(), selfieBytes);

        if (matchedPhotoIds.isEmpty()) {
            return List.of();
        }

        return photoRepository.findByIdInAndStatus(matchedPhotoIds, Photo.Status.ACTIVE);
    }

    public String getThumbnailUrl(Photo photo) {
        if (photo.getThumbnailS3Key() == null) {
            return null;
        }
        return s3StorageService.generatePresignedUrl(photo.getThumbnailS3Key(), Duration.ofHours(1));
    }

    public String getOriginalUrl(Photo photo) {
        if (photo.getOriginalS3Key() == null) {
            return null;
        }
        return s3StorageService.generatePresignedUrl(photo.getOriginalS3Key(), Duration.ofHours(24));
    }
}
