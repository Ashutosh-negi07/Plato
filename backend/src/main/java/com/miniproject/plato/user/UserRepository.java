package com.miniproject.plato.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Used by DataInitializer to check if a SUPER_ADMIN already exists
     * before attempting to seed one — avoids a duplicate insert.
     */
    boolean existsByRole(UserRole role);
}
