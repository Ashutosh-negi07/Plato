package com.miniproject.plato.modules.order.dto;

import com.miniproject.plato.modules.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "Order status is required")
        OrderStatus status
) {}
