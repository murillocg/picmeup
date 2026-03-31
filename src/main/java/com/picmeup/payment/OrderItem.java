package com.picmeup.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "photo_id", nullable = false)
    private UUID photoId;

    @Column(nullable = false)
    private BigDecimal price;

    protected OrderItem() {
    }

    public OrderItem(Order order, UUID photoId, BigDecimal price) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.photoId = photoId;
        this.price = price;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public UUID getPhotoId() {
        return photoId;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
