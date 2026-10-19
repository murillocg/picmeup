package com.picmeup.payment;

import com.picmeup.payment.dto.EventRevenueResponse;
import com.picmeup.photo.EventRepository;
import com.picmeup.photo.FaceSearchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Revenue per event. Sits under the same /api/admin/stats path as the search stats so
 * the two read as one reporting surface; the whole prefix is ADMIN-only in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/stats")
public class RevenueStatsController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventPassRepository eventPassRepository;
    private final FaceSearchRepository faceSearchRepository;
    private final EventRepository eventRepository;

    public RevenueStatsController(OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  EventPassRepository eventPassRepository,
                                  FaceSearchRepository faceSearchRepository,
                                  EventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPassRepository = eventPassRepository;
        this.faceSearchRepository = faceSearchRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<EventRevenueResponse>> getRevenue() {
        // Only PAID counts. PENDING orders are abandoned checkouts, and FAILED ones never
        // took money. REFUNDED is not subtracted here because nothing in the application
        // ever sets it -- refunds are issued in PayPal and are invisible to this report.
        var orders = countAndAmountByEvent(orderRepository.getRevenueByEvent(Order.Status.PAID));

        // A redeemed pass was paid for before it was redeemed, so both states are revenue.
        var passes = countAndAmountByEvent(eventPassRepository.getRevenueByEvent(
                List.of(EventPass.Status.PAID, EventPass.Status.REDEEMED)));

        Map<UUID, Long> photosSold = new HashMap<>();
        for (Object[] row : orderItemRepository.countSoldPhotosByEvent(Order.Status.PAID)) {
            photosSold.put((UUID) row[0], (Long) row[1]);
        }

        Map<UUID, long[]> searches = new HashMap<>();
        for (Object[] row : faceSearchRepository.getSearchStatsByEvent()) {
            searches.put((UUID) row[0], new long[]{(Long) row[1], (Long) row[2]});
        }

        // An event belongs in the report if it earned anything or drew any searches, so
        // an event with traffic and no sales still shows up -- that is the useful row.
        var eventIds = new LinkedHashSet<UUID>();
        eventIds.addAll(orders.keySet());
        eventIds.addAll(passes.keySet());
        eventIds.addAll(searches.keySet());

        var rows = new ArrayList<EventRevenueResponse>();
        for (UUID eventId : eventIds) {
            var orderTotals = orders.getOrDefault(eventId, CountAndAmount.EMPTY);
            var passTotals = passes.getOrDefault(eventId, CountAndAmount.EMPTY);
            var searchTotals = searches.getOrDefault(eventId, new long[]{0L, 0L});
            var event = eventRepository.findById(eventId).orElse(null);

            rows.add(new EventRevenueResponse(
                    eventId,
                    event != null ? event.getName() : "Deleted event",
                    event != null ? event.getSlug() : null,
                    orderTotals.count(),
                    orderTotals.amount(),
                    photosSold.getOrDefault(eventId, 0L),
                    passTotals.count(),
                    passTotals.amount(),
                    orderTotals.amount().add(passTotals.amount()),
                    searchTotals[0],
                    searchTotals[1]
            ));
        }

        rows.sort(Comparator.comparing(EventRevenueResponse::totalRevenue).reversed());
        return ResponseEntity.ok(rows);
    }

    private static Map<UUID, CountAndAmount> countAndAmountByEvent(List<Object[]> rows) {
        Map<UUID, CountAndAmount> byEvent = new HashMap<>();
        for (Object[] row : rows) {
            byEvent.put((UUID) row[0], new CountAndAmount((Long) row[1], (BigDecimal) row[2]));
        }
        return byEvent;
    }

    private record CountAndAmount(long count, BigDecimal amount) {
        static final CountAndAmount EMPTY = new CountAndAmount(0L, BigDecimal.ZERO);
    }
}
