package com.miniproject.plato.modules.order;

import com.miniproject.plato.modules.order.dto.OrderResponse;
import com.miniproject.plato.modules.order.dto.PlaceOrderRequest;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    // ── Customer Operations (Session Token Auth) ──────────────────────────────
    OrderResponse placeOrder(String sessionToken, PlaceOrderRequest request);

    List<OrderResponse> getSessionOrders(String sessionToken);

    OrderResponse getSessionOrderById(String sessionToken, UUID orderId);

    OrderResponse cancelOrder(String sessionToken, UUID orderId);

    OrderResponse removeOrderItem(String sessionToken, UUID orderId, UUID orderItemId);

    // ── Staff Operations (JWT Auth) ───────────────────────────────────────────
    List<OrderResponse> getRestaurantOrders(UUID restaurantId, OrderStatus statusFilter, UUID callerId, String callerRole);

    OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID callerId, String callerRole);
}
