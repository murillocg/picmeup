package com.picmeup.photo.dto;

import com.picmeup.photo.Photo;

import java.util.UUID;

public record PhotoUploadResponse(
        UUID id,
        String status
) {
    public static PhotoUploadResponse from(Photo photo) {
        return new PhotoUploadResponse(photo.getId(), photo.getStatus().name());
    }
}
