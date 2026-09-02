package com.picmeup.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Grants one photographer permission to upload to one event. The absence of a row is the
 * denial — there is no enabled flag that could drift out of step with reality.
 *
 * <p>Both halves of the key are UUIDs, so nothing stops a caller passing them the wrong
 * way round. Construct these only through {@code UserManagementService}, which resolves
 * the event and the user before assigning.
 */
@Entity
@Table(name = "event_photographers")
public class EventPhotographer {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        protected Key() {
        }

        public Key(UUID eventId, UUID userId) {
            this.eventId = eventId;
            this.userId = userId;
        }

        public UUID getEventId() {
            return eventId;
        }

        public UUID getUserId() {
            return userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(eventId, key.eventId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, userId);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    protected EventPhotographer() {
    }

    public EventPhotographer(UUID eventId, UUID userId) {
        this.id = new Key(eventId, userId);
        this.assignedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Key getId() {
        return id;
    }

    public UUID getEventId() {
        return id.getEventId();
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }
}
