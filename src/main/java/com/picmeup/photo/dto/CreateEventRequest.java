package com.picmeup.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEventRequest(
        @NotBlank(message = "Event name is required")
        String name,

        @NotNull(message = "Event date is required")
        LocalDate date,

        @NotBlank(message = "Event location is required")
        String location,

        @NotNull(message = "Photo price is required")
        @PositiveOrZero(message = "Photo price must be zero or positive")
        BigDecimal photoPrice,

        @NotNull(message = "Pack price is required")
        @PositiveOrZero(message = "Pack price must be zero or positive")
        BigDecimal packPrice,

        @NotNull(message = "Free flag is required")
        Boolean free
) {
}
