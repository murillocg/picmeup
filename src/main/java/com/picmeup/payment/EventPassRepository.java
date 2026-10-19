package com.picmeup.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPassRepository extends JpaRepository<EventPass, UUID> {
    Optional<EventPass> findByEventIdAndBuyerEmailAndStatus(UUID eventId, String buyerEmail, EventPass.Status status);
    List<EventPass> findAllByOrderByCreatedAtDesc();

    /**
     * Pass revenue per event, as (eventId, passCount, revenue). A REDEEMED pass was paid
     * for before it was redeemed, so callers pass both states.
     */
    @Query("""
            SELECT p.eventId, COUNT(p), COALESCE(SUM(p.price), 0)
            FROM EventPass p
            WHERE p.status IN :statuses
            GROUP BY p.eventId
            """)
    List<Object[]> getRevenueByEvent(@Param("statuses") Collection<EventPass.Status> statuses);
}
