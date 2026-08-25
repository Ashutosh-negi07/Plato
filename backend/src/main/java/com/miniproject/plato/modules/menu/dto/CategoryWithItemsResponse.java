package com.miniproject.plato.modules.menu.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CategoryWithItemsResponse(

        UUID id,
UUID restaurantId,
        String name,
        String description,
        int displayOrder,
        boolean isActive,
        List<MenuItemResponse>items ,   // ← nested list of items under this category
        LocalDateTime createdAt,
        LocalDateTime updatedAt

) {
}
