package com.miniproject.plato.restaurant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

// =========================================================================
// RestaurantRepository — Spring Data JPA repository for Restaurant.
// -------------------------------------------------------------------------
// findByOwnerId: Owner fetches only their own restaurants.
// findAll(Pageable): Super Admin fetches all restaurants, paginated.
// Both are derived queries — Spring Data generates the SQL automatically
// from the method name. No @Query needed for simple lookups.
// =========================================================================
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    // Returns all restaurants owned by a specific user (paginated)
    Page<Restaurant> findByOwnerId(UUID ownerId, Pageable pageable);

    // Ownership check — does this restaurant belong to this owner?
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
