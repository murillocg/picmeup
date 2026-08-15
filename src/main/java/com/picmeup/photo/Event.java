package com.picmeup.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private String coverImageKey;

    @Column(nullable = false)
    private boolean hidden = true;

    @Column(nullable = false)
    private BigDecimal photoPrice = new BigDecimal("20.00");

    @Column(nullable = false)
    private BigDecimal packPrice = new BigDecimal("65.00");

    @Column(nullable = false)
    private boolean free = false;

    @Column(nullable = false)
    private boolean comingSoon = false;

    @Column
    private LocalDateTime deletedAt;

    protected Event() {
    }

    public Event(String name, LocalDate date, String location) {
        this(name, date, location, new BigDecimal("20.00"), new BigDecimal("65.00"), false);
    }

    public Event(String name, LocalDate date, String location, BigDecimal photoPrice, BigDecimal packPrice, boolean free) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.date = date;
        this.location = location;
        this.slug = generateSlug(name, date);
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.expiresAt = this.createdAt.plusMonths(2);
        this.photoPrice = photoPrice;
        this.packPrice = packPrice;
        this.free = free;
    }

    private static String generateSlug(String name, LocalDate date) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return base + "-" + date.toString();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLocation() {
        return location;
    }

    public String getSlug() {
        return slug;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getCoverImageKey() {
        return coverImageKey;
    }

    public void setCoverImageKey(String coverImageKey) {
        this.coverImageKey = coverImageKey;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public BigDecimal getPhotoPrice() { return photoPrice; }
    public void setPhotoPrice(BigDecimal photoPrice) { this.photoPrice = photoPrice; }

    public BigDecimal getPackPrice() { return packPrice; }
    public void setPackPrice(BigDecimal packPrice) { this.packPrice = packPrice; }

    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }

    public boolean isComingSoon() { return comingSoon; }
    public void setComingSoon(boolean comingSoon) { this.comingSoon = comingSoon; }

    public boolean isExpired() {
        return LocalDateTime.now(ZoneOffset.UTC).isAfter(expiresAt);
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void markDeleted() {
        this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
