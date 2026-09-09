package com.miniproject.plato.modules.order;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.order.dto.OrderResponse;
import com.miniproject.plato.modules.order.dto.UpdateOrderStatusRequest;
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
public class StaffOrderController {

    private final OrderService orderService;

    /**
     * Kitchen / Staff views orders for a restaurant (optionally filtered by OrderStatus).
     */
    @GetMapping("/restaurants/{restaurantId}/orders")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getRestaurantOrders(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) OrderStatus status,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        List<OrderResponse> response = orderService.getRestaurantOrders(restaurantId, status, callerId, role);
        return ResponseEntity.ok(ApiResponse.ok("Orders retrieved successfully", response));
    }

    /**
     * Staff / Chef updates the status of an order (e.g. PENDING -> ACCEPTED -> PREPARING -> READY -> SERVED).
     */
    @PatchMapping("/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            Authentication authentication) {

        UUID callerId = UUID.fromString(authentication.getName());
        String role = authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        OrderResponse response = orderService.updateOrderStatus(orderId, request.status(), callerId, role);
        return ResponseEntity.ok(ApiResponse.ok("Order status updated successfully", response));
    }
}
