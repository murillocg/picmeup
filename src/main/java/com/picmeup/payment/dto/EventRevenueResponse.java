package com.picmeup.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What one event earned, alongside the search traffic that produced it. The search
 * columns are what make the revenue readable: $0 against 2,000 searches is a very
 * different problem from $0 against none.
 */
public record EventRevenueResponse(
        UUID eventId,
        String eventName,
        String eventSlug,
        long orderCount,
        BigDecimal orderRevenue,
        long photosSold,
        long passCount,
        BigDecimal passRevenue,
        BigDecimal totalRevenue,
        long totalSearches,
        long searchesWithResults
) {
}
