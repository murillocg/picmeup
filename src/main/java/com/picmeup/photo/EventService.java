package com.picmeup.photo;

import com.picmeup.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
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
}
