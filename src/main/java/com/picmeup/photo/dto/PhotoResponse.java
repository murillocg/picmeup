package com.picmeup.photo.dto;

import com.picmeup.photo.Photo;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoResponse(
        UUID id,
        String status,
        String thumbnailUrl,
        String originalUrl,
        LocalDateTime uploadedAt
) {
    public static PhotoResponse from(Photo photo, String thumbnailUrl) {
        return new PhotoResponse(
                photo.getId(),
                photo.getStatus().name(),
                thumbnailUrl,
                null,
                photo.getUploadedAt()
        );
    }

    public static PhotoResponse from(Photo photo, String thumbnailUrl, String originalUrl) {
        return new PhotoResponse(
                photo.getId(),
                photo.getStatus().name(),
                thumbnailUrl,
                originalUrl,
                photo.getUploadedAt()
        );
    }
}
