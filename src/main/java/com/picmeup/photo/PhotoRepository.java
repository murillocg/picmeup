package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findByEventIdAndStatus(UUID eventId, Photo.Status status);

    List<Photo> findByEventId(UUID eventId);

    List<Photo> findByIdInAndStatus(List<UUID> ids, Photo.Status status);

    void deleteByEventId(UUID eventId);
}
