package com.miniproject.plato.modules.billing;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.billing.dto.CompletePaymentRequest;
import com.miniproject.plato.modules.billing.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StaffBillingController {

    private final BillingService billingService;

    /**
     * Cashier / Staff lists payments for a restaurant (optionally filtered by status).
     */
    @GetMapping("/restaurants/{restaurantId}/payments")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getRestaurantPayments(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) PaymentStatus status,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        List<PaymentResponse> response = billingService.getRestaurantPayments(restaurantId, status, callerId, role);
        return ResponseEntity.ok(ApiResponse.ok("Payments retrieved successfully", response));
    }

    /**
     * Staff views specific payment details by ID.
     */
    @GetMapping("/restaurants/{restaurantId}/payments/{paymentId}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @PathVariable UUID restaurantId,
            @PathVariable UUID paymentId,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        PaymentResponse response = billingService.getPaymentById(paymentId, callerId, role);
        return ResponseEntity.ok(ApiResponse.ok("Payment retrieved successfully", response));
    }

    /**
     * Cashier confirms payment completion (e.g. cash received, card swiped).
     * Automatically completes the payment, closes the dining session, and releases the table to AVAILABLE.
     */
    @PostMapping("/restaurants/{restaurantId}/payments/{paymentId}/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> completePayment(
            @PathVariable UUID restaurantId,
            @PathVariable UUID paymentId,
            @Valid @RequestBody(required = false) CompletePaymentRequest request,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        PaymentResponse response = billingService.completePayment(paymentId, request, callerId, role);
        return ResponseEntity.ok(ApiResponse.ok("Payment completed and table released successfully", response));
    }
}
