package com.miniproject.plato.modules.menu.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MenuItemResponse(

        UUID id,
UUID categoryId,
        UUID restaurantId,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean isAvailable,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
}
