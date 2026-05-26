package com.picmeup.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "face_searches")
public class FaceSearch {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private int resultsCount;

    @Column(nullable = false)
    private LocalDateTime searchedAt;

    protected FaceSearch() {
    }

    public FaceSearch(UUID eventId, int resultsCount) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.resultsCount = resultsCount;
        this.searchedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public int getResultsCount() { return resultsCount; }
    public LocalDateTime getSearchedAt() { return searchedAt; }
}
