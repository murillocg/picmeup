package com.picmeup.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "photos")
public class Photo {

    public enum Status {
        PROCESSING, ACTIVE, FAILED, EXPIRED, ARCHIVED
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photographer_id", nullable = false)
    private Photographer photographer;

    @Column(name = "original_s3_key")
    private String originalS3Key;

    @Column(name = "thumbnail_s3_key")
    private String thumbnailS3Key;

    @Column(name = "rekognition_face_ids")
    private String[] rekognitionFaceIds;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "original_filename")
    private String originalFilename;

    protected Photo() {
    }

    public Photo(Event event, Photographer photographer) {
        this(UUID.randomUUID(), event, photographer, null);
    }

    public Photo(UUID id, Event event, Photographer photographer) {
        this(id, event, photographer, null);
    }

    public Photo(UUID id, Event event, Photographer photographer, String originalFilename) {
        this.id = id;
        this.event = event;
        this.photographer = photographer;
        this.uploadedAt = LocalDateTime.now(ZoneOffset.UTC);
        this.status = Status.PROCESSING;
        this.originalFilename = originalFilename;
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Photographer getPhotographer() {
        return photographer;
    }

    public String getOriginalS3Key() {
        return originalS3Key;
    }

    public String getThumbnailS3Key() {
        return thumbnailS3Key;
    }

    public String[] getRekognitionFaceIds() {
        return rekognitionFaceIds;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public Status getStatus() {
        return status;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void markActive(String originalS3Key, String thumbnailS3Key, String[] faceIds) {
        this.originalS3Key = originalS3Key;
        this.thumbnailS3Key = thumbnailS3Key;
        this.rekognitionFaceIds = faceIds;
        this.status = Status.ACTIVE;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public void markExpired() {
        this.status = Status.EXPIRED;
    }

    public void markArchived() {
        this.originalS3Key = null;
        this.thumbnailS3Key = null;
        this.status = Status.ARCHIVED;
    }
}
