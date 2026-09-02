package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventPhotographerRepository
        extends JpaRepository<EventPhotographer, EventPhotographer.Key> {

    /**
     * The authorisation check on every upload — a bare existence test, no entity loaded.
     */
    boolean existsByIdEventIdAndIdUserId(UUID eventId, UUID userId);

    /** Who is assigned to this event, for the staffing control on the event page. */
    List<EventPhotographer> findByIdEventId(UUID eventId);

    /** Used for the assignment count on the admin users page. */
    long countByIdUserId(UUID userId);

    void deleteByIdEventIdAndIdUserId(UUID eventId, UUID userId);
}
