package com.picmeup.photo.dto;

import com.picmeup.photo.Event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        LocalDate date,
        String location,
        String slug,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String coverImageUrl,
        boolean hidden,
        BigDecimal photoPrice,
        BigDecimal packPrice,
        boolean free
) {
    public static EventResponse from(Event event, String coverImageUrl) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getLocation(),
                event.getSlug(),
                event.getCreatedAt(),
                event.getExpiresAt(),
                coverImageUrl,
                event.isHidden(),
                event.getPhotoPrice(),
                event.getPackPrice(),
                event.isFree()
        );
    }
}
