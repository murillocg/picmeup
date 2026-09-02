package com.picmeup.photo;

import com.picmeup.common.user.AppUserRepository;
import com.picmeup.photo.dto.EventResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The photographer's whole application: the events they may upload to.
 *
 * <p>An admin sees everything through the normal event endpoints, so this is scoped
 * strictly to the caller — there is no way to ask for someone else's assignments.
 */
@RestController
@RequestMapping("/api/photographer")
public class PhotographerController {

    private final EventRepository events;
    private final AppUserRepository users;
    private final EventService eventService;

    public PhotographerController(EventRepository events,
                                  AppUserRepository users,
                                  EventService eventService) {
        this.events = events;
        this.users = users;
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventResponse>> myEvents(Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName());
        if (user.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        var assigned = events.findAssignedTo(user.get().getId()).stream()
                .map(event -> EventResponse.from(event, eventService.getCoverImageUrl(event)))
                .toList();

        return ResponseEntity.ok(assigned);
    }
}
