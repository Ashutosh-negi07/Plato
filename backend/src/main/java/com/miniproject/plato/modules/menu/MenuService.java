package com.miniproject.plato.modules.menu;

import com.miniproject.plato.modules.menu.dto.*;

import java.util.List;
import java.util.UUID;

public interface MenuService {
    // ── Categories ────────────────────────────────────────────────────────────────

    CategoryWithItemsResponse createCategory(UUID restaurantId,
                                             CreateCategoryRequest request, UUID ownerId);

    List<CategoryWithItemsResponse> getCategories(UUID restaurantId,
                                                  UUID callerId, String callerRole);

    CategoryWithItemsResponse updateCategory(UUID restaurantId, UUID categoryId,
                                             UpdateCategoryRequest request, UUID ownerId);

    void deleteCategory(UUID restaurantId, UUID categoryId, UUID ownerId);


// ── Items ─────────────────────────────────────────────────────────────────────

    MenuItemResponse createItem(UUID restaurantId,
                                CreateItemRequest request, UUID ownerId);

    MenuItemResponse updateItem(UUID restaurantId, UUID itemId,
                                UpdateItemRequest request, UUID ownerId);

    MenuItemResponse toggleAvailability(UUID restaurantId,
                                        UUID itemId, UUID ownerId);

    void deleteItem(UUID restaurantId, UUID itemId, UUID ownerId);


// ── Public ────────────────────────────────────────────────────────────────────

    List<CategoryWithItemsResponse> getPublicMenu(UUID restaurantId);
// No ownerId/callerId — this is unauthenticated
// Returns only active categories with only available items

}
