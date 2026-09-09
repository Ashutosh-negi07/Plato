package com.miniproject.plato.modules.billing.dto;

import com.miniproject.plato.modules.order.dto.OrderResponse;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record BillSummaryResponse(
        UUID sessionId,
        UUID restaurantId,
        String restaurantName,
        UUID tableId,
        String tableNumber,
        List<OrderResponse> orders,
        BigDecimal ordersSubtotal,
        BigDecimal taxTotal,
        BigDecimal serviceChargePercentage,
        BigDecimal serviceChargeAmount,
        BigDecimal discount,
        BigDecimal grandTotal,
        boolean hasPendingOrders
) {}
