package com.miniproject.plato.modules.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    // List only active employees for a restaurant
    List<Employee> findByRestaurantIdAndIsActiveTrue(UUID restaurantId);

    // Duplicate assignment guard — returns true if user already assigned
    boolean existsByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    // Fetch employee only if it belongs to this restaurant (cross-tenant safe)
    Optional<Employee> findByIdAndRestaurantId(UUID id, UUID restaurantId);
}
