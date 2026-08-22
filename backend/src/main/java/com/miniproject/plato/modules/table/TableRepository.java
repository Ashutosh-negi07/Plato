package com.miniproject.plato.modules.table;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableRepository extends JpaRepository<RestaurantTable, UUID> {
    List<RestaurantTable> findByRestaurantId(UUID restaurantId);
    boolean existsByRestaurantIdAndTableNumber(UUID restaurantId, String tableNumber);
    Optional<RestaurantTable> findByQrToken(String qrToken);
}
