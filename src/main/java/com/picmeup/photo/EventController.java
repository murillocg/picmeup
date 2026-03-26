package com.picmeup.photo;

import com.picmeup.photo.dto.CreateEventRequest;
import com.picmeup.photo.dto.EventResponse;
import com.picmeup.photo.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String slug) {
        return ResponseEntity.ok(EventResponse.from(eventService.getBySlug(slug)));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listActiveEvents() {
        var events = eventService.listActiveEvents().stream()
                .map(EventResponse::from)
                .toList();
        return ResponseEntity.ok(events);
    }

    @PutMapping("/{slug}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable String slug,
                                                     @Valid @RequestBody UpdateEventRequest request) {
        var event = eventService.updateEvent(slug, request.name(), request.date(), request.location());
        return ResponseEntity.ok(EventResponse.from(event));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String slug) {
        eventService.deleteEvent(slug);
        return ResponseEntity.noContent().build();
    }
}
