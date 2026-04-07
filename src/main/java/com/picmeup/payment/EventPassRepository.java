package com.picmeup.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPassRepository extends JpaRepository<EventPass, UUID> {
    Optional<EventPass> findByEventIdAndBuyerEmailAndStatus(UUID eventId, String buyerEmail, EventPass.Status status);
    List<EventPass> findAllByOrderByCreatedAtDesc();
}
