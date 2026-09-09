package com.miniproject.plato.modules.session;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.session.dto.CustomerSessionResponse;
import com.miniproject.plato.modules.session.dto.StartSessionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CustomerSessionController {

    private final CustomerSessionService sessionService;

    // ── Customer Endpoints (Public / Session Token Auth) ───────────────────────

    /**
     * Customer scans QR code -> starts or rejoins dining session.
     * Accessible publicly without staff JWT.
     */
    @PostMapping("/customer/sessions/start")
    public ResponseEntity<ApiResponse<CustomerSessionResponse>> startSession(
            @Valid @RequestBody StartSessionRequest request) {

        CustomerSessionResponse response = sessionService.startSession(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Dining session started", response));
    }

    /**
     * Customer retrieves their current session details using X-Session-Token.
     * Automatically slides expiration window forward by 30 minutes.
     */
    @GetMapping("/customer/sessions/current")
    public ResponseEntity<ApiResponse<CustomerSessionResponse>> getCurrentSession(
            @RequestHeader("X-Session-Token") String sessionToken) {

        CustomerSessionResponse response = sessionService.getCurrentSession(sessionToken);
        return ResponseEntity.ok(ApiResponse.ok("Session retrieved", response));
    }

    // ── Staff Endpoints (JWT Auth Required) ───────────────────────────────────

    /**
     * Staff manually closes a table session (e.g. after customer pays at the cash counter).
     */
    @PostMapping("/restaurants/{restaurantId}/sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> closeSession(
            @PathVariable UUID restaurantId,
            @PathVariable UUID sessionId,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        sessionService.closeSession(sessionId, callerId, role);
        return ResponseEntity.ok(ApiResponse.ok("Session closed and table released", null));
    }
}
