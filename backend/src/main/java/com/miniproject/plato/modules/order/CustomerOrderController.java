package com.miniproject.plato.modules.order;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.order.dto.OrderResponse;
import com.miniproject.plato.modules.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final OrderService orderService;

    /**
     * Customer places an order using their active session token.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestHeader("X-Session-Token") String sessionToken,
            @Valid @RequestBody PlaceOrderRequest request) {

        OrderResponse response = orderService.placeOrder(sessionToken, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Order placed successfully", response));
    }

    /**
     * Customer views all orders placed during their active dining session.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getSessionOrders(
            @RequestHeader("X-Session-Token") String sessionToken) {

        List<OrderResponse> response = orderService.getSessionOrders(sessionToken);
        return ResponseEntity.ok(ApiResponse.ok("Session orders retrieved", response));
    }

    /**
     * Customer retrieves a specific order by ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @RequestHeader("X-Session-Token") String sessionToken,
            @PathVariable UUID orderId) {

        OrderResponse response = orderService.getSessionOrderById(sessionToken, orderId);
        return ResponseEntity.ok(ApiResponse.ok("Order retrieved", response));
    }

    /**
     * Customer cancels an entire order while it is still PENDING.
     */
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @RequestHeader("X-Session-Token") String sessionToken,
            @PathVariable UUID orderId) {

        OrderResponse response = orderService.cancelOrder(sessionToken, orderId);
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled successfully", response));
    }

    /**
     * Customer removes a specific line item from an order while it is still PENDING.
     */
    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<ApiResponse<OrderResponse>> removeOrderItem(
            @RequestHeader("X-Session-Token") String sessionToken,
            @PathVariable UUID orderId,
            @PathVariable UUID itemId) {

        OrderResponse response = orderService.removeOrderItem(sessionToken, orderId, itemId);
        return ResponseEntity.ok(ApiResponse.ok("Item removed from order", response));
    }
}
