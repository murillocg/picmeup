package com.picmeup.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findByEventIdAndStatus(UUID eventId, Photo.Status status);

    List<Photo> findByEventId(UUID eventId);

    List<Photo> findByIdInAndStatus(List<UUID> ids, Photo.Status status);

    void deleteByEventId(UUID eventId);

    List<Photo> findByEventIdAndOriginalFilename(UUID eventId, String originalFilename);

    long countByStatusIn(List<Photo.Status> statuses);

    @Query("SELECT COUNT(p) FROM Photo p WHERE p.rekognitionFaceIds IS NOT NULL")
    long countWithIndexedFaces();

    @Query("SELECT COUNT(p) FROM Photo p WHERE p.rekognitionFaceIds IS NOT NULL AND p.uploadedAt >= :since")
    long countWithIndexedFacesSince(LocalDateTime since);
}
