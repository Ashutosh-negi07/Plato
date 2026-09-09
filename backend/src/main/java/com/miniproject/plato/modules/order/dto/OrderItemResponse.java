package com.miniproject.plato.modules.order.dto;

import com.miniproject.plato.modules.order.OrderItemStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record OrderItemResponse(
        UUID id,
        UUID menuItemId,
        String menuItemName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String specialRequest,
        OrderItemStatus status
) {}
