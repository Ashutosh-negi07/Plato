package com.miniproject.plato.modules.menu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateItemRequest(
        @NotNull
        UUID categoryId,

        @NotBlank
        String name,

        String description,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal price,

        String imageUrl,

        int displayOrder

) {
}
