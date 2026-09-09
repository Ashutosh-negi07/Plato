package com.miniproject.plato.modules.billing.dto;

import com.miniproject.plato.modules.billing.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestBillRequest(
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @Size(max = 255, message = "Notes cannot exceed 255 characters")
        String notes
) {}
