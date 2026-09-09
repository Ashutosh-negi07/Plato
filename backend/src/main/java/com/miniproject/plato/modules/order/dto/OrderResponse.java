package com.miniproject.plato.modules.order.dto;

import com.miniproject.plato.modules.order.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record OrderResponse(
        UUID id,
        UUID restaurantId,
        UUID tableId,
        String tableNumber,
        UUID sessionId,
        String orderNumber,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal grandTotal,
        String notes,
        LocalDateTime placedAt,
        LocalDateTime completedAt,
        List<OrderItemResponse> items
) {}
