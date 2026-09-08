package com.picmeup.payment.dto;

import com.picmeup.payment.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID id,
        String buyerEmail,
        String status,
        BigDecimal totalAmount,
        String currency,
        boolean free,
        /**
         * Serialised as an instant, so it carries a UTC marker and the browser can convert
         * it to the reader's own zone. The stored value is a naive LocalDateTime that is
         * already UTC by convention; without this the client has to assume that.
         */
        Instant createdAt
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getBuyerEmail(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.isFree(),
                order.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
