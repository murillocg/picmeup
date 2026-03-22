package com.picmeup.photo.dto;

import com.picmeup.photo.Photo;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoResponse(
        UUID id,
        String status,
        String thumbnailUrl,
        LocalDateTime uploadedAt
) {
    public static PhotoResponse from(Photo photo, String thumbnailUrl) {
        return new PhotoResponse(
                photo.getId(),
                photo.getStatus().name(),
                thumbnailUrl,
                photo.getUploadedAt()
        );
    }
}
