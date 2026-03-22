package com.picmeup.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateEventRequest(
        @NotBlank(message = "Event name is required")
        String name,

        @NotNull(message = "Event date is required")
        LocalDate date,

        @NotBlank(message = "Event location is required")
        String location
) {
}
