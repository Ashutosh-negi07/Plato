package com.miniproject.plato.modules.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {

    // Fast lookup for incoming X-Session-Token requests
    Optional<CustomerSession> findBySessionToken(String sessionToken);

    // Find current active session at a specific table (prevents duplicate sessions)
    Optional<CustomerSession> findByTableIdAndStatus(UUID tableId, SessionStatus status);

    // List all active sessions in a restaurant (for live staff dashboard)
    List<CustomerSession> findByRestaurantIdAndStatus(UUID restaurantId, SessionStatus status);
}
