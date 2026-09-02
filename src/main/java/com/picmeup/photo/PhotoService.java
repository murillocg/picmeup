package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.payment.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.services.s3.model.StorageClass;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    private final PhotoRepository photoRepository;
    private final PhotographerRepository photographerRepository;
    private final EventRepository eventRepository;
    private final S3StorageService s3StorageService;
    private final PhotoProcessingService photoProcessingService;
    private final FaceRecognitionService faceRecognitionService;
    private final OrderItemRepository orderItemRepository;
    private final FaceSearchRepository faceSearchRepository;
    private final UploaderResolver uploaderResolver;
    private final boolean lambdaProcessingEnabled;

    public PhotoService(PhotoRepository photoRepository,
                        PhotographerRepository photographerRepository,
                        EventRepository eventRepository,
                        S3StorageService s3StorageService,
                        PhotoProcessingService photoProcessingService,
                        FaceRecognitionService faceRecognitionService,
                        OrderItemRepository orderItemRepository,
                        FaceSearchRepository faceSearchRepository,
                        UploaderResolver uploaderResolver,
                        @Value("${lambda.callback-secret:}") String lambdaCallbackSecret) {
        this.photoRepository = photoRepository;
        this.photographerRepository = photographerRepository;
        this.eventRepository = eventRepository;
        this.s3StorageService = s3StorageService;
        this.photoProcessingService = photoProcessingService;
        this.faceRecognitionService = faceRecognitionService;
        this.orderItemRepository = orderItemRepository;
        this.faceSearchRepository = faceSearchRepository;
        this.uploaderResolver = uploaderResolver;
        this.lambdaProcessingEnabled = lambdaCallbackSecret != null && !lambdaCallbackSecret.isBlank();
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
            var photo = new Photo(UUID.randomUUID(), event, photographer, file.getOriginalFilename());
            photoRepository.save(photo);
            photos.add(photo);

            photoProcessingService.processPhotoAsync(photo.getId(), event.getId(), file);
        }

        return photos;
    }

    @Transactional
    public Map<String, String> presignUpload(String eventSlug, String filename) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        if (event.isExpired()) {
            throw new IllegalArgumentException("Cannot upload photos to an expired event");
        }

        uploaderResolver.requireCanUploadTo(event.getId());

        if (filename != null && !filename.isBlank()) {
            var existing = photoRepository.findByEventIdAndOriginalFilename(event.getId(), filename);

            // A FAILED photo has no usable image, so re-uploading the same file replaces it
            // instead of being rejected as a duplicate.
            if (existing.stream().anyMatch(photo -> photo.getStatus() != Photo.Status.FAILED)) {
                throw new IllegalArgumentException("A photo with this filename already exists in this event");
            }

            existing.forEach(photo -> {
                deletePhotoFiles(photo);
                photoRepository.delete(photo);
                log.info("Replacing failed photo {} ({}) in event {}", photo.getId(), filename, event.getId());
            });
        }

        var photoId = UUID.randomUUID();
        String s3Key = "originals/%s/%s.jpg".formatted(event.getId(), photoId);
        String uploadUrl = s3StorageService.generatePresignedUploadUrl(
                s3Key, Duration.ofMinutes(15), "image/jpeg", null);

        return Map.of(
                "uploadUrl", uploadUrl,
                "s3Key", s3Key,
                "photoId", photoId.toString(),
                "eventId", event.getId().toString()
        );
    }

    @Transactional
    public Photo confirmUpload(String eventSlug, String photoId, String s3Key, String filename) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        uploaderResolver.requireCanUploadTo(event.getId());

        var photographer = uploaderResolver.resolvePhotographer(photographerRepository);

        var photo = new Photo(UUID.fromString(photoId), event, photographer, filename);
        photoRepository.save(photo);

        if (!lambdaProcessingEnabled) {
            photoProcessingService.processUploadedPhotoAsync(photo.getId(), event.getId(), s3Key);
        }

        return photo;
    }

    @Transactional(readOnly = true)
    public List<Photo> getActivePhotos(String eventSlug) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        return photoRepository.findByEventIdAndStatus(event.getId(), Photo.Status.ACTIVE);
    }

    @Transactional
    public List<Photo> searchByFace(String eventSlug, byte[] selfieBytes) {
        var event = eventRepository.findBySlug(eventSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventSlug));

        List<UUID> matchedPhotoIds = faceRecognitionService.searchByFace(event.getId(), selfieBytes);

        List<Photo> results = matchedPhotoIds.isEmpty()
                ? List.of()
                : photoRepository.findByIdInAndStatus(matchedPhotoIds, Photo.Status.ACTIVE);

        faceSearchRepository.save(new FaceSearch(event.getId(), results.size()));

        return results;
    }

    @Transactional
    public void deletePhoto(UUID photoId) {
        var photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo", photoId.toString()));

        if (orderItemRepository.existsByPhotoId(photoId)) {
            throw new IllegalArgumentException("This photo has been purchased and cannot be deleted");
        }

        deletePhotoFiles(photo);

        photoRepository.delete(photo);
        log.info("Photo {} deleted", photoId);
    }

    private void deletePhotoFiles(Photo photo) {
        if (photo.getOriginalS3Key() != null) {
            s3StorageService.deleteFile(photo.getOriginalS3Key());
        }
        if (photo.getThumbnailS3Key() != null) {
            s3StorageService.deleteFile(photo.getThumbnailS3Key());
        }
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
