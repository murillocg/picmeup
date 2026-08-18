package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
