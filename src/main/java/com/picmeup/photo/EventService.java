package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import com.picmeup.payment.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    public Event createEvent(String name, LocalDate date, String location) {
        var event = new Event(name, date, location);

        if (eventRepository.existsBySlug(event.getSlug())) {
            throw new IllegalArgumentException("An event with this name and date already exists");
        }

        return eventRepository.save(event);
    }

    public Event getBySlug(String slug) {
        return eventRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Event", slug));
    }

    public List<Event> listActiveEvents() {
        return eventRepository.findByExpiresAtAfterOrderByDateDesc(LocalDateTime.now());
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

        var photoIds = photoRepository.findByEventIdAndStatus(eventId, Photo.Status.ACTIVE)
                .stream().map(Photo::getId).toList();
        if (!photoIds.isEmpty() && orderItemRepository.existsByPhotoIdIn(photoIds)) {
            throw new IllegalArgumentException("Cannot delete event with purchased photos");
        }

        log.info("Deleting event {} ({})", slug, eventId);

        faceRecognitionService.deleteCollection(eventId);

        s3StorageService.deleteByPrefix("originals/" + eventId + "/");
        s3StorageService.deleteByPrefix("thumbnails/" + eventId + "/");

        if (event.getCoverImageKey() != null) {
            s3StorageService.deleteFile(event.getCoverImageKey());
        }

        photoRepository.deleteByEventId(eventId);
        eventRepository.delete(event);

        log.info("Event {} deleted successfully", slug);
    }
}
