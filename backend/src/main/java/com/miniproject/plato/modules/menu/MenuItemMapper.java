package com.miniproject.plato.modules.menu;

import com.miniproject.plato.modules.menu.dto.CreateItemRequest;
import com.miniproject.plato.modules.menu.dto.MenuItemResponse;
import com.miniproject.plato.modules.menu.dto.UpdateItemRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MenuItemMapper {

    public MenuItem toEntity(UUID restaurantId, CreateItemRequest request) {
        return MenuItem.builder()
                .restaurantId(restaurantId)
                .categoryId(request.categoryId())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .displayOrder(request.displayOrder())
                .build();
    }

    public MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(
                item.getId(),
                item.getCategoryId(),
                item.getRestaurantId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getImageUrl(),
                item.isAvailable(),
                item.getDisplayOrder(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    public void applyUpdate(UpdateItemRequest request, MenuItem item) {
        if (request.name() != null)        item.setName(request.name());
        if (request.description() != null) item.setDescription(request.description());
        if (request.price() != null)       item.setPrice(request.price());
        if (request.imageUrl() != null)    item.setImageUrl(request.imageUrl());
        if (request.displayOrder() != null) item.setDisplayOrder(request.displayOrder());
    }
}
