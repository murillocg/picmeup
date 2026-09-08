package com.picmeup.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status {
        PENDING, PAID, FAILED, REFUNDED
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String buyerEmail;

    @Column
    private String paypalOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Order() {
    }

    public Order(String buyerEmail, BigDecimal totalAmount) {
        this.id = UUID.randomUUID();
        this.buyerEmail = buyerEmail;
        this.totalAmount = totalAmount;
        this.currency = "AUD";
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * A free-event order: created already PAID, with no PayPal order behind it. Lets the
     * admin list explain a $0.00 total rather than leaving it looking like a failed charge.
     */
    public boolean isFree() {
        return paypalOrderId == null && totalAmount.compareTo(BigDecimal.ZERO) == 0;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
