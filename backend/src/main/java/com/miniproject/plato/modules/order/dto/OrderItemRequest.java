package com.miniproject.plato.modules.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "Menu item ID is required")
        UUID menuItemId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @Size(max = 255, message = "Special request cannot exceed 255 characters")
        String specialRequest
) {}
