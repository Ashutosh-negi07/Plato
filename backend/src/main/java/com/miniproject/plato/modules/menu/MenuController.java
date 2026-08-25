package com.miniproject.plato.modules.menu;

import com.miniproject.plato.common.ApiResponse;
import com.miniproject.plato.modules.menu.dto.CategoryWithItemsResponse;
import com.miniproject.plato.modules.menu.dto.CreateCategoryRequest;
import com.miniproject.plato.modules.menu.dto.CreateItemRequest;
import com.miniproject.plato.modules.menu.dto.MenuItemResponse;
import com.miniproject.plato.modules.menu.dto.UpdateCategoryRequest;
import com.miniproject.plato.modules.menu.dto.UpdateItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/menu")
@RequiredArgsConstructor
@Slf4j
public class MenuController {

    private final MenuService menuService;

    // ── Private helpers ───────────────────────────────────────────────────────

    private UUID getCurrentUserId() {
        String principal = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return UUID.fromString(principal);
    }

    private String getCurrentRole() {
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .iterator().next()
                .getAuthority()
                .replace("ROLE_", "");
    }

    // ── PUBLIC endpoint (no token required) ───────────────────────────────────

    @GetMapping
    public ApiResponse<List<CategoryWithItemsResponse>> getPublicMenu(
            @PathVariable UUID restaurantId) {
        return ApiResponse.ok("Menu fetched successfully",
                menuService.getPublicMenu(restaurantId));
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @PostMapping("/categories")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryWithItemsResponse> createCategory(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateCategoryRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Category created successfully",
                menuService.createCategory(restaurantId, request, ownerId));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public ApiResponse<List<CategoryWithItemsResponse>> getCategories(
            @PathVariable UUID restaurantId) {
        UUID callerId = getCurrentUserId();
        String role = getCurrentRole();
        return ApiResponse.ok("Categories fetched successfully",
                menuService.getCategories(restaurantId, callerId, role));
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<CategoryWithItemsResponse> updateCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Category updated successfully",
                menuService.updateCategory(restaurantId, categoryId, request, ownerId));
    }

    @DeleteMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID categoryId) {
        UUID ownerId = getCurrentUserId();
        menuService.deleteCategory(restaurantId, categoryId, ownerId);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    @PostMapping("/items")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MenuItemResponse> createItem(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateItemRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Item created successfully",
                menuService.createItem(restaurantId, request, ownerId));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<MenuItemResponse> updateItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest request) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Item updated successfully",
                menuService.updateItem(restaurantId, itemId, request, ownerId));
    }

    @PatchMapping("/items/{itemId}/availability")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<MenuItemResponse> toggleAvailability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId) {
        UUID ownerId = getCurrentUserId();
        return ApiResponse.ok("Item availability toggled",
                menuService.toggleAvailability(restaurantId, itemId, ownerId));
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId) {
        UUID ownerId = getCurrentUserId();
        menuService.deleteItem(restaurantId, itemId, ownerId);
    }
}

