package com.miniproject.plato.modules.menu;

import com.miniproject.plato.exception.ConflictException;
import com.miniproject.plato.exception.ResourceNotFoundException;
import com.miniproject.plato.exception.UnauthorizedAccessException;
import com.miniproject.plato.modules.menu.dto.CategoryWithItemsResponse;
import com.miniproject.plato.modules.menu.dto.CreateCategoryRequest;
import com.miniproject.plato.modules.menu.dto.CreateItemRequest;
import com.miniproject.plato.modules.menu.dto.MenuItemResponse;
import com.miniproject.plato.modules.menu.dto.UpdateCategoryRequest;
import com.miniproject.plato.modules.menu.dto.UpdateItemRequest;
import com.miniproject.plato.modules.restaurant.Restaurant;
import com.miniproject.plato.modules.restaurant.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MenuServiceImpl implements MenuService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryMapper menuCategoryMapper;
    private final MenuItemMapper menuItemMapper;

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Verifies the restaurant exists AND the caller is its owner. Returns the restaurant. */
    private Restaurant verifyOwnership(UUID restaurantId, UUID ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!restaurant.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        return restaurant;
    }

    /** Verifies restaurant exists and caller is either OWNER of it or SUPER_ADMIN. */
    private Restaurant verifyReadAccess(UUID restaurantId, UUID callerId, String callerRole) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
        if (!"SUPER_ADMIN".equals(callerRole) && !restaurant.getOwnerId().equals(callerId)) {
            throw new UnauthorizedAccessException("You do not own this restaurant");
        }
        return restaurant;
    }

    // ── Categories ────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public CategoryWithItemsResponse createCategory(UUID restaurantId, CreateCategoryRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        if (menuCategoryRepository.existsByRestaurantIdAndName(restaurantId, request.name())) {
            throw new ConflictException("A category named '" + request.name() + "' already exists in this restaurant");
        }

        MenuCategory category = menuCategoryMapper.toEntity(restaurantId, request);
        return menuCategoryMapper.toResponseWithoutItems(menuCategoryRepository.save(category));
    }

    @Override
    public List<CategoryWithItemsResponse> getCategories(UUID restaurantId, UUID callerId, String callerRole) {
        verifyReadAccess(restaurantId, callerId, callerRole);

        return menuCategoryRepository.findByRestaurantIdOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(menuCategoryMapper::toResponseWithoutItems)
                .toList();
    }

    @Transactional
    @Override
    public CategoryWithItemsResponse updateCategory(UUID restaurantId, UUID categoryId,
                                                    UpdateCategoryRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        MenuCategory category = menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        if (request.name() != null)        category.setName(request.name());
        if (request.description() != null) category.setDescription(request.description());
        if (request.displayOrder() != null) category.setDisplayOrder(request.displayOrder());
        if (request.isActive() != null)    category.setActive(request.isActive());

        return menuCategoryMapper.toResponseWithoutItems(category);
    }

    @Transactional
    @Override
    public void deleteCategory(UUID restaurantId, UUID categoryId, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        MenuCategory category = menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

        menuCategoryRepository.delete(category);
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    @Transactional
    @Override
    public MenuItemResponse createItem(UUID restaurantId, CreateItemRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        // Verify category belongs to this restaurant
        menuCategoryRepository.findByIdAndRestaurantId(request.categoryId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));

        if (menuItemRepository.existsByCategoryIdAndName(request.categoryId(), request.name())) {
            throw new ConflictException("An item named '" + request.name() + "' already exists in this category");
        }

        MenuItem item = menuItemMapper.toEntity(restaurantId, request);
        return menuItemMapper.toResponse(menuItemRepository.save(item));
    }

    @Transactional
    @Override
    public MenuItemResponse updateItem(UUID restaurantId, UUID itemId, UpdateItemRequest request, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        menuItemMapper.applyUpdate(request, item);
        return menuItemMapper.toResponse(item);
    }

    @Transactional
    @Override
    public MenuItemResponse toggleAvailability(UUID restaurantId, UUID itemId, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        item.setAvailable(!item.isAvailable());
        return menuItemMapper.toResponse(item);
    }

    @Transactional
    @Override
    public void deleteItem(UUID restaurantId, UUID itemId, UUID ownerId) {
        verifyOwnership(restaurantId, ownerId);

        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        menuItemRepository.delete(item);
    }

    // ── Public (no auth) ──────────────────────────────────────────────────────

    @Override
    public List<CategoryWithItemsResponse> getPublicMenu(UUID restaurantId) {
        restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

        return menuCategoryRepository
                .findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(cat -> {
                    List<MenuItemResponse> items = menuItemRepository
                            .findByCategoryIdAndIsAvailableTrueOrderByDisplayOrderAsc(cat.getId())
                            .stream()
                            .map(menuItemMapper::toResponse)
                            .toList();
                    return menuCategoryMapper.toResponse(cat, items);
                })
                .toList();
    }
}
