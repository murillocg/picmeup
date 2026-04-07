package com.picmeup.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_passes")
public class EventPass {

    public enum Status {
        PENDING, PAID, REDEEMED
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String buyerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column
    private String paypalOrderId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime redeemedAt;

    protected EventPass() {
    }

    public EventPass(UUID eventId, String buyerEmail, BigDecimal price) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.buyerEmail = buyerEmail;
        this.price = price;
        this.currency = "AUD";
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRedeemedAt() {
        return redeemedAt;
    }

    public void markRedeemed() {
        this.status = Status.REDEEMED;
        this.redeemedAt = LocalDateTime.now();
    }
}
