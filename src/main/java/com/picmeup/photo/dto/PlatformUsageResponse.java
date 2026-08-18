package com.picmeup.photo.dto;

public record PlatformUsageResponse(
        long totalEvents,
        long totalPhotos,
        StorageUsage storage,
        FacialRecognitionUsage facialRecognition
) {
    public record StorageUsage(
            long totalBytes,
            long originalsBytes,
            long thumbnailsBytes
    ) {}

    public record FacialRecognitionUsage(
            long facesIndexedThisMonth,
            long searchesThisMonth,
            long facesIndexedAllTime,
            long searchesAllTime
    ) {}
}
