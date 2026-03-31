package com.picmeup.payment.dto;

import com.picmeup.payment.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID photoId,
        BigDecimal price,
        String downloadUrl
) {
    public static OrderItemResponse from(OrderItem item, String downloadUrl) {
        return new OrderItemResponse(
                item.getId(),
                item.getPhotoId(),
                item.getPrice(),
                downloadUrl
        );
    }
}
