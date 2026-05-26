package com.picmeup.photo.dto;

import java.util.UUID;

public record SearchStatsResponse(
        UUID eventId,
        String eventName,
        String eventSlug,
        long totalSearches,
        long searchesWithResults
) {
}
