package com.picmeup.admin.dto;

import com.picmeup.common.user.AppUser;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        long assignedEvents
) {
    public static UserResponse from(AppUser user, long assignedEvents) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                assignedEvents
        );
    }
}
