package com.picmeup.payment.dto;

import com.picmeup.payment.EventPass;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventPassResponse(
        UUID id,
        UUID eventId,
        String buyerEmail,
        String status,
        BigDecimal price,
        String currency,
        LocalDateTime createdAt,
        LocalDateTime redeemedAt,
        String paypalOrderId
) {
    public static EventPassResponse from(EventPass pass) {
        return new EventPassResponse(
                pass.getId(),
                pass.getEventId(),
                pass.getBuyerEmail(),
                pass.getStatus().name(),
                pass.getPrice(),
                pass.getCurrency(),
                pass.getCreatedAt(),
                pass.getRedeemedAt(),
                pass.getPaypalOrderId()
        );
    }
}
