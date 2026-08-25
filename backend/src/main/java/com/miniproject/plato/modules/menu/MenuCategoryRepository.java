package com.miniproject.plato.modules.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface  MenuCategoryRepository extends JpaRepository<MenuCategory,UUID> {
    List<MenuCategory> findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID restaurantId);
// Used by: getCategories() (owner view) + public menu
// SQL: WHERE restaurant_id = ? AND is_active = true ORDER BY display_order ASC

    List<MenuCategory> findByRestaurantIdOrderByDisplayOrderAsc(UUID restaurantId);
// Used by: owner/admin view (shows inactive categories too)

    boolean existsByRestaurantIdAndName(UUID restaurantId, String name);
// Used by: createCategory() duplicate name check
// SQL: SELECT COUNT(*) > 0 WHERE restaurant_id = ? AND name = ?

    Optional<MenuCategory> findByIdAndRestaurantId(UUID id, UUID restaurantId);
// Used by: update/delete — cross-restaurant isolation guard

}
