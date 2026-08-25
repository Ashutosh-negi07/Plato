package com.miniproject.plato.modules.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findByCategoryIdAndIsAvailableTrueOrderByDisplayOrderAsc(UUID categoryId);
// Used by: public menu — only available items, sorted

    List<MenuItem> findByCategoryIdOrderByDisplayOrderAsc(UUID categoryId);
// Used by: owner view — all items including unavailable

    Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId);
// Cross-restaurant isolation guard for update/delete/toggle

    boolean existsByCategoryIdAndName(UUID categoryId, String name);
// Duplicate item name check within a category

}
