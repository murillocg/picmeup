package com.picmeup.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Revenue per event, as (eventId, orderCount, revenue).
     *
     * <p>Sums the order total rather than its items: item prices are derived by dividing
     * the total across the photos, so they round and no longer add up to what was charged.
     * Orders with no event are historical rows that could not be backfilled, and are left
     * out rather than collapsed under a null key.
     */
    @Query("""
            SELECT o.eventId, COUNT(o), COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.status = :status AND o.eventId IS NOT NULL
            GROUP BY o.eventId
            """)
    List<Object[]> getRevenueByEvent(@Param("status") Order.Status status);
}
