package com.miniproject.plato.modules.session;


import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.SessionExpiredException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.exception.ValidationException;
import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.restaurant.RestaurantRepository;
import com.miniproject.plato.modules.restaurant.RestaurantStatus;
import com.miniproject.plato.modules.session.dto.CustomerSessionResponse;
import com.miniproject.plato.modules.session.dto.StartSessionRequest;
import com.miniproject.plato.modules.table.RestaurantTable;
import com.miniproject.plato.modules.table.TableRepository;
import com.miniproject.plato.modules.table.TableStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerSessionServiceImpl implements CustomerSessionService {

    private final CustomerSessionRepository sessionRepository;
    private final TableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerSessionMapper sessionMapper;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically secure 64-character hex session token.
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Override
    @Transactional
    public CustomerSessionResponse startSession(StartSessionRequest request) {
        // 1. Look up table by QR token
        RestaurantTable table = tableRepository.findByQrToken(request.qrToken())
                .orElseThrow(() -> new ResourceNotFoundException("Table QR code is invalid"));

        // 2. Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(table.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", table.getRestaurantId()));

        if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new ValidationException("This restaurant is not currently active");
        }
        if (Boolean.FALSE.equals(restaurant.getAcceptingOrders())) {
            throw new ValidationException("This restaurant is currently not accepting orders");
        }

        // 3. Check if table already has an active session (Multi-guest dining scenario)
        Optional<CustomerSession> existingSessionOpt = sessionRepository
                .findByTableIdAndStatus(table.getId(), SessionStatus.ACTIVE);

        if (existingSessionOpt.isPresent()) {
            CustomerSession existing = existingSessionOpt.get();
            if (!existing.isExpired()) {
                log.info("Table {} already has an active session; rejoining existing session", table.getTableNumber());
                existing.refreshActivity();
                return sessionMapper.toResponse(existing, restaurant, table);
            }
            // If expired, mark it as EXPIRED
            existing.setStatus(SessionStatus.EXPIRED);
            existing.setEndedAt(LocalDateTime.now());
        }

        // 4. Create new dining session
        int guests = (request.guestCount() != null && request.guestCount() > 0) ? request.guestCount() : 1;
        CustomerSession newSession = CustomerSession.builder()
                .restaurantId(restaurant.getId())
                .tableId(table.getId())
                .sessionToken(generateSecureToken())
                .status(SessionStatus.ACTIVE)
                .guestCount(guests)
                .startedAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        CustomerSession savedSession = sessionRepository.save(newSession);

        // 5. Mark table as OCCUPIED
        table.setStatus(TableStatus.OCCUPIED);
        tableRepository.save(table);

        log.info("New dining session started for table {} with {} guest(s)", table.getTableNumber(), guests);
        return sessionMapper.toResponse(savedSession, restaurant, table);
    }

    @Override
    @Transactional
    public CustomerSessionResponse getCurrentSession(String sessionToken) {
        CustomerSession session = validateAndRefreshSession(sessionToken);

        Restaurant restaurant = restaurantRepository.findById(session.getRestaurantId()).orElse(null);
        RestaurantTable table = tableRepository.findById(session.getTableId()).orElse(null);

        return sessionMapper.toResponse(session, restaurant, table);
    }

    @Override
    @Transactional
    public CustomerSession validateAndRefreshSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new SessionExpiredException("Session token is missing");
        }

        CustomerSession session = sessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new SessionExpiredException("Invalid session token"));

        if (session.isExpired()) {
            session.setStatus(SessionStatus.EXPIRED);
            session.setEndedAt(LocalDateTime.now());
            throw new SessionExpiredException("Your session has expired. Please scan the QR code again.");
        }

        // Slide window by 30 minutes
        session.refreshActivity();
        return session;
    }

    @Override
    @Transactional
    public void closeSession(UUID sessionId, UUID callerId, String callerRole) {
        CustomerSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerSession", sessionId));

        // Security check: Only SUPER_ADMIN, OWNER, or restaurant employees can close sessions
        if (!"SUPER_ADMIN".equals(callerRole)) {
            Restaurant restaurant = restaurantRepository.findById(session.getRestaurantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Restaurant", session.getRestaurantId()));
            if (!restaurant.getOwnerId().equals(callerId) && !"EMPLOYEE".equals(callerRole)) {
                throw new UnauthorizedAccessException("You do not have permission to close this session");
            }
        }

        session.setStatus(SessionStatus.CLOSED);
        session.setEndedAt(LocalDateTime.now());

        // Release table back to AVAILABLE
        tableRepository.findById(session.getTableId()).ifPresent(table -> {
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        });

        log.info("Session {} closed successfully. Table released to AVAILABLE.", sessionId);
    }
}

