package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Event> findByExpiresAtAfterAndDeletedAtIsNullOrderByDateDesc(LocalDateTime now);

    List<Event> findByExpiresAtAfterAndHiddenFalseAndDeletedAtIsNullOrderByDateDesc(LocalDateTime now);

    Optional<Event> findBySlugAndDeletedAtIsNull(String slug);

    long countByDeletedAtIsNull();

    /**
     * The events a photographer may upload to. Joined rather than fetching assignment ids
     * and then the events, so soft-deleted events are excluded and the ordering is done by
     * the database — findAllById would return deleted events in arbitrary order.
     */
    @Query("""
            SELECT e FROM Event e
            JOIN EventPhotographer ep ON ep.id.eventId = e.id
            WHERE ep.id.userId = :userId
              AND e.deletedAt IS NULL
            ORDER BY e.date DESC
            """)
    List<Event> findAssignedTo(@Param("userId") UUID userId);
}
