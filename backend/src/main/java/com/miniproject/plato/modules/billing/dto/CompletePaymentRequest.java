package com.miniproject.plato.modules.billing.dto;

import jakarta.validation.constraints.Size;

public record CompletePaymentRequest(
        @Size(max = 255, message = "Transaction reference cannot exceed 255 characters")
        String transactionReference
) {}
