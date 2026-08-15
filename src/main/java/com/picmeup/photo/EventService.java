package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.payment.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.picmeup.photo.dto.CreateEventRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;
    private final FaceRecognitionService faceRecognitionService;
    private final ImageProcessingService imageProcessingService;
    private final OrderItemRepository orderItemRepository;

    public EventService(EventRepository eventRepository,
                        PhotoRepository photoRepository,
                        S3StorageService s3StorageService,
                        FaceRecognitionService faceRecognitionService,
                        ImageProcessingService imageProcessingService,
                        OrderItemRepository orderItemRepository) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.s3StorageService = s3StorageService;
        this.faceRecognitionService = faceRecognitionService;
        this.imageProcessingService = imageProcessingService;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional
    public Event createEvent(CreateEventRequest request) {
        boolean free = request.free();
        boolean comingSoon = Boolean.TRUE.equals(request.comingSoon());

        if (!free && !comingSoon && request.packPrice().compareTo(request.photoPrice()) < 0) {
            throw new IllegalArgumentException("Pack price must be greater than or equal to the price per photo");
        }

        var event = new Event(request.name(), request.date(), request.location(),
                request.photoPrice(), request.packPrice(), free);
        event.setComingSoon(comingSoon);

        if (eventRepository.existsBySlug(event.getSlug())) {
            throw new IllegalArgumentException("An event with this name and date already exists");
        }

        eventRepository.save(event);
        log.info("Event created: {} ({})", event.getSlug(), event.getId());
        return event;
    }

    public Event getBySlug(String slug) {
        return eventRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", slug));
    }

    public List<Event> listActiveEvents() {
        return eventRepository.findByExpiresAtAfterAndHiddenFalseAndDeletedAtIsNullOrderByDateDesc(LocalDateTime.now(ZoneOffset.UTC));
    }

    public List<Event> listAllActiveEvents() {
        return eventRepository.findByExpiresAtAfterAndDeletedAtIsNullOrderByDateDesc(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public Event toggleHidden(String slug) {
        var event = getBySlug(slug);
        event.setHidden(!event.isHidden());
        return eventRepository.save(event);
    }

    @Transactional
    public Event toggleComingSoon(String slug) {
        var event = getBySlug(slug);
        event.setComingSoon(!event.isComingSoon());
        return eventRepository.save(event);
    }

    @Transactional
    public Event uploadCoverImage(String slug, MultipartFile file) throws IOException {
        var event = getBySlug(slug);

        byte[] resized = imageProcessingService.generateThumbnail(file.getBytes());
        String key = "covers/" + event.getId() + ".jpg";

        if (event.getCoverImageKey() != null) {
            s3StorageService.deleteFile(event.getCoverImageKey());
        }

        s3StorageService.uploadFile(key, resized, "image/jpeg");
        event.setCoverImageKey(key);
        eventRepository.save(event);

        log.info("Cover image uploaded for event {}", slug);
        return event;
    }

    public String getCoverImageUrl(Event event) {
        if (event.getCoverImageKey() == null) {
            return null;
        }
        return s3StorageService.generatePresignedUrl(event.getCoverImageKey(), Duration.ofHours(1));
    }

    @Transactional
    public void deleteEvent(String slug) {
        var event = getBySlug(slug);
        var eventId = event.getId();

        log.info("Soft-deleting event {} ({})", slug, eventId);

        faceRecognitionService.deleteCollection(eventId);

        s3StorageService.deleteByPrefix("originals/" + eventId + "/");
        s3StorageService.deleteByPrefix("thumbnails/" + eventId + "/");

        if (event.getCoverImageKey() != null) {
            s3StorageService.deleteFile(event.getCoverImageKey());
            event.setCoverImageKey(null);
        }

        var photos = photoRepository.findByEventId(eventId);
        var photoIds = photos.stream().map(Photo::getId).toList();
        var purchasedPhotoIds = photoIds.isEmpty()
                ? List.<UUID>of()
                : orderItemRepository.findPhotoIdsByPhotoIdIn(photoIds);

        for (Photo photo : photos) {
            if (purchasedPhotoIds.contains(photo.getId())) {
                photo.markArchived();
            } else {
                photoRepository.delete(photo);
            }
        }

        event.markDeleted();
        eventRepository.save(event);

        log.info("Event {} soft-deleted successfully", slug);
    }
}
