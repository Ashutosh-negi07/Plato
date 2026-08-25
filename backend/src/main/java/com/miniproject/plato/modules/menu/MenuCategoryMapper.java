package com.miniproject.plato.modules.menu;

import com.miniproject.plato.modules.menu.dto.CategoryWithItemsResponse;
import com.miniproject.plato.modules.menu.dto.CreateCategoryRequest;
import com.miniproject.plato.modules.menu.dto.MenuItemResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MenuCategoryMapper {

    public MenuCategory toEntity(UUID restaurantId, CreateCategoryRequest request) {
        return MenuCategory.builder()
                .restaurantId(restaurantId)
                .name(request.name())
                .description(request.description())
                .displayOrder(request.displayOrder())
                .build();
    }

    public CategoryWithItemsResponse toResponse(MenuCategory category, List<MenuItemResponse> items) {
        return new CategoryWithItemsResponse(
                category.getId(),
                category.getRestaurantId(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder(),
                category.isActive(),
                items,
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public CategoryWithItemsResponse toResponseWithoutItems(MenuCategory category) {
        return toResponse(category, List.of());
    }
}
