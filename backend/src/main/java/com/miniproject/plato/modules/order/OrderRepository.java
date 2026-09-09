package com.miniproject.plato.modules.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findBySessionIdOrderByPlacedAtDesc(UUID sessionId);

    List<Order> findByRestaurantIdOrderByPlacedAtDesc(UUID restaurantId);

    List<Order> findByRestaurantIdAndStatusOrderByPlacedAtAsc(UUID restaurantId, OrderStatus status);

    Optional<Order> findByIdAndSessionId(UUID id, UUID sessionId);

    boolean existsByOrderNumber(String orderNumber);
}
