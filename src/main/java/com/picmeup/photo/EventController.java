package com.picmeup.photo;

import com.picmeup.photo.dto.CreateEventRequest;
import com.picmeup.photo.dto.EventResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        var event = eventService.createEvent(request.name(), request.date(), request.location());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(event));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String slug) {
        return ResponseEntity.ok(toResponse(eventService.getBySlug(slug)));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listActiveEvents() {
        var events = eventService.listActiveEvents().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(events);
    }

    @PostMapping(value = "/{slug}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> uploadCoverImage(
            @PathVariable String slug,
            @RequestParam("cover") MultipartFile file) throws IOException {
        var event = eventService.uploadCoverImage(slug, file);
        return ResponseEntity.ok(toResponse(event));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String slug) {
        eventService.deleteEvent(slug);
        return ResponseEntity.noContent().build();
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.from(event, eventService.getCoverImageUrl(event));
    }
}
