package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public EventService(EventRepository eventRepository,
                        PhotoRepository photoRepository,
                        S3StorageService s3StorageService,
                        FaceRecognitionService faceRecognitionService) {
        this.eventRepository = eventRepository;
        this.photoRepository = photoRepository;
        this.s3StorageService = s3StorageService;
        this.faceRecognitionService = faceRecognitionService;
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
    public void deleteEvent(String slug) {
        var event = getBySlug(slug);
        var eventId = event.getId();

        log.info("Deleting event {} ({})", slug, eventId);

        faceRecognitionService.deleteCollection(eventId);

        s3StorageService.deleteByPrefix("originals/" + eventId + "/");
        s3StorageService.deleteByPrefix("thumbnails/" + eventId + "/");

        photoRepository.deleteByEventId(eventId);
        eventRepository.delete(event);

        log.info("Event {} deleted successfully", slug);
    }
}
