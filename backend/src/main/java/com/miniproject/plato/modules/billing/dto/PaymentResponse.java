package com.miniproject.plato.modules.billing.dto;

import com.miniproject.plato.modules.billing.PaymentMethod;
import com.miniproject.plato.modules.billing.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID id,
        UUID sessionId,
        UUID restaurantId,
        String tableNumber,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal serviceCharge,
        BigDecimal discount,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String transactionReference,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
