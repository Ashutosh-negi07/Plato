package com.miniproject.plato.modules.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findBySessionId(UUID sessionId);

    Optional<Payment> findBySessionIdAndStatus(UUID sessionId, PaymentStatus status);

    List<Payment> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);

    List<Payment> findByRestaurantIdAndStatusOrderByCreatedAtDesc(UUID restaurantId, PaymentStatus status);
}
