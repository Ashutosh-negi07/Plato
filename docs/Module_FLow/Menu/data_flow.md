# Menu Module — Complete Data Flow

> Every HTTP request through the Menu module traced step by step.

---

## Architecture overview

```
HTTP Request
     |
     v
MenuController.java         — receives HTTP, extracts caller from SecurityContextHolder
     |
     v
MenuService.java            — interface (the contract)
     |
     v
MenuServiceImpl.java        — business logic, validates ownership, calls repos + mappers
     |           |
     v           v
MenuCategoryRepository   MenuItemRepository   RestaurantRepository
     |
     v
menu_categories / menu_items tables (PostgreSQL)
```

### Two entity tables

```
restaurants
     |
     └── menu_categories  (restaurant_id FK)
              |
              └── menu_items  (category_id FK + restaurant_id FK)
```

`restaurant_id` is stored on `menu_items` directly (denormalized) so cross-restaurant
isolation checks don't need a JOIN through `menu_categories`.

---

## Flow 1 — POST /api/v1/restaurants/{restaurantId}/menu/categories (Create Category)

### Request example
```
POST /api/v1/restaurants/3fa85f64-.../menu/categories
Authorization: Bearer eyJhbGci...   ← OWNER token
Content-Type: application/json

{
  "name": "Starters",
  "description": "Light bites to begin",
  "displayOrder": 1
}
```

### Step-by-step trace

**STEP 1 — JWT filter + @PreAuthorize**
```
JwtAuthenticationFilter → validates token → sets SecurityContextHolder
@PreAuthorize("hasRole('OWNER')") → checks ROLE_OWNER authority → proceed or 403
```

**STEP 2 — Controller**
```java
UUID ownerId = getCurrentUserId();   // from SecurityContextHolder principal
menuService.createCategory(restaurantId, request, ownerId);
```

**STEP 3 — verifyOwnership() helper**
```java
Restaurant restaurant = restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
// SQL: SELECT * FROM restaurants WHERE id = '3fa85f64...'

if (!restaurant.getOwnerId().equals(ownerId)) {
    throw new UnauthorizedAccessException("You do not own this restaurant");
}
// Cross-tenant guard — Owner A cannot add categories to Owner B's restaurant
```

**STEP 4 — Duplicate name check**
```java
if (menuCategoryRepository.existsByRestaurantIdAndName(restaurantId, request.name())) {
    throw new ConflictException("A category named 'Starters' already exists in this restaurant");
}
// SQL: SELECT COUNT(*) > 0 FROM menu_categories
//      WHERE restaurant_id = '3fa85f64...' AND name = 'Starters'
```

**STEP 5 — Map to entity and save**
```java
MenuCategory category = menuCategoryMapper.toEntity(restaurantId, request);
// MenuCategory.builder()
//   .restaurantId(restaurantId)
//   .name("Starters")
//   .description("Light bites to begin")
//   .displayOrder(1)
//   .build()
// isActive = true  ← @Builder.Default

return menuCategoryMapper.toResponseWithoutItems(menuCategoryRepository.save(category));
// INSERT INTO menu_categories (id, restaurant_id, name, description, display_order,
//                             is_active, created_at, updated_at)
// VALUES (gen_random_uuid(), '3fa85f64...', 'Starters', 'Light bites...', 1, true, now(), now())
```

**STEP 6 — Response**
```
HTTP 201 Created
{
  "success": true,
  "message": "Category created successfully",
  "data": {
    "id": "a1b2...",
    "restaurantId": "3fa85f64...",
    "name": "Starters",
    "description": "Light bites to begin",
    "displayOrder": 1,
    "isActive": true,
    "items": [],
    "createdAt": "2026-08-25T09:00:00",
    "updatedAt": "2026-08-25T09:00:00"
  }
}
```

---

## Flow 2 — POST /api/v1/restaurants/{restaurantId}/menu/items (Create Item)

### Request example
```
POST /api/v1/restaurants/3fa85f64-.../menu/items
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "categoryId": "a1b2c3...",
  "name": "Paneer Tikka",
  "description": "Grilled cottage cheese with spices",
  "price": 280.00,
  "imageUrl": "https://cdn.example.com/paneer.jpg",
  "displayOrder": 1
}
```

### Step-by-step trace

**STEP 1 — JWT filter + @PreAuthorize('OWNER')**

**STEP 2 — verifyOwnership()** — same as Flow 1

**STEP 3 — Category belongs to this restaurant check**
```java
menuCategoryRepository.findByIdAndRestaurantId(request.categoryId(), restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
// SQL: SELECT * FROM menu_categories
//      WHERE id = 'a1b2c3...' AND restaurant_id = '3fa85f64...'
//
// Why this check?
// Without it: Owner A could pass categoryId from Owner B's restaurant.
// The item would be saved under Owner B's category but in Owner A's restaurant.
// findByIdAndRestaurantId prevents this — category must belong to THIS restaurant.
```

**STEP 4 — Duplicate item name in category**
```java
if (menuItemRepository.existsByCategoryIdAndName(request.categoryId(), request.name())) {
    throw new ConflictException("An item named 'Paneer Tikka' already exists in this category");
}
```

**STEP 5 — Map and save**
```java
MenuItem item = menuItemMapper.toEntity(restaurantId, request);
// MenuItem.builder()
//   .restaurantId(restaurantId)    ← denormalized
//   .categoryId(request.categoryId())
//   .name("Paneer Tikka")
//   .price(BigDecimal("280.00"))
//   .imageUrl("https://...")
//   .displayOrder(1)
//   .build()
// isAvailable = true ← @Builder.Default

return menuItemMapper.toResponse(menuItemRepository.save(item));
// INSERT INTO menu_items (id, category_id, restaurant_id, name, description, price,
//                        image_url, is_available, display_order, created_at, updated_at)
// VALUES (gen_random_uuid(), 'a1b2c3...', '3fa85f64...', 'Paneer Tikka',
//         'Grilled cottage cheese...', 280.00, 'https://...', true, 1, now(), now())
```

**STEP 6 — Response**
```
HTTP 201 Created
{
  "success": true,
  "message": "Item created successfully",
  "data": {
    "id": "d4e5f6...",
    "categoryId": "a1b2c3...",
    "restaurantId": "3fa85f64...",
    "name": "Paneer Tikka",
    "price": 280.00,
    "isAvailable": true,
    ...
  }
}
```

---

## Flow 3 — PATCH /api/v1/restaurants/{restaurantId}/menu/items/{itemId}/availability

### Request example
```
PATCH /api/v1/restaurants/3fa85f64-.../menu/items/d4e5f6-.../availability
Authorization: Bearer eyJhbGci...
(no request body)
```

### Step-by-step trace

**STEP 1 — verifyOwnership() + find item**
```java
MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
// Cross-restaurant isolation: item must belong to THIS restaurant
```

**STEP 2 — Toggle**
```java
item.setAvailable(!item.isAvailable());
// If isAvailable was true  → becomes false (item goes off menu)
// If isAvailable was false → becomes true  (item is back)
// Hibernate dirty check → UPDATE menu_items SET is_available = false WHERE id = 'd4e5f6...'
```

**STEP 3 — Response**
```
HTTP 200 OK
{
  "success": true,
  "message": "Item availability toggled",
  "data": { ..., "isAvailable": false, ... }
}
```

---

## Flow 4 — GET /api/v1/restaurants/{restaurantId}/menu (PUBLIC — no token)

### Request example
```
GET /api/v1/restaurants/3fa85f64-.../menu
(no Authorization header required)
```

### Step-by-step trace

**STEP 1 — SecurityConfig allows this without a token**
```java
// SecurityConfig.java
.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/*/menu").permitAll()
// Spring Security skips JWT validation entirely for this path
// JwtAuthenticationFilter still runs but finds no token → sets anonymous authentication
// The controller has NO @PreAuthorize → method executes for everyone
```

**STEP 2 — Controller (no getCurrentUserId call)**
```java
@GetMapping               // maps GET /api/v1/restaurants/{restaurantId}/menu
public ApiResponse<List<CategoryWithItemsResponse>> getPublicMenu(
        @PathVariable UUID restaurantId) {
    return ApiResponse.ok("Menu fetched successfully",
            menuService.getPublicMenu(restaurantId));
}
// No ownerId, no callerId, no role — this endpoint is truly public
```

**STEP 3 — getPublicMenu() in service**
```java
// 1. Verify restaurant exists
restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

// 2. Fetch only ACTIVE categories, sorted by display_order
List<MenuCategory> categories = menuCategoryRepository
        .findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId);
// SQL: SELECT * FROM menu_categories
//      WHERE restaurant_id = '3fa85f64...' AND is_active = true
//      ORDER BY display_order ASC
```

**STEP 4 — For each category, fetch only AVAILABLE items**
```java
return categories.stream()
        .map(cat -> {
            List<MenuItemResponse> items = menuItemRepository
                    .findByCategoryIdAndIsAvailableTrueOrderByDisplayOrderAsc(cat.getId())
                    // SQL: SELECT * FROM menu_items
                    //      WHERE category_id = 'a1b2c3...' AND is_available = true
                    //      ORDER BY display_order ASC
                    .stream()
                    .map(menuItemMapper::toResponse)
                    .toList();
            return menuCategoryMapper.toResponse(cat, items);
            // Builds CategoryWithItemsResponse with nested List<MenuItemResponse>
        })
        .toList();
// Result: full menu tree — categories with their items nested inside
```

**STEP 5 — Response (no auth header needed by caller)**
```json
{
  "success": true,
  "message": "Menu fetched successfully",
  "data": [
    {
      "id": "a1b2c3...",
      "name": "Starters",
      "displayOrder": 1,
      "isActive": true,
      "items": [
        {
          "id": "d4e5f6...",
          "name": "Paneer Tikka",
          "price": 280.00,
          "isAvailable": true,
          "displayOrder": 1
        }
      ]
    },
    {
      "id": "b2c3d4...",
      "name": "Mains",
      "displayOrder": 2,
      "items": [ ... ]
    }
  ]
}
```

---

## Flow 5 — PUT /api/v1/restaurants/{restaurantId}/menu/categories/{categoryId} (Update Category)

### Request example
```
PUT /api/v1/restaurants/3fa85f64-.../menu/categories/a1b2c3-...
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{ "isActive": false }
```
*(Only isActive sent — all other fields null → unchanged)*

### Step-by-step trace

**STEP 1 — verifyOwnership() + find category**
```java
MenuCategory category = menuCategoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
```

**STEP 2 — Null-check each field (partial update)**
```java
if (request.name() != null)         category.setName(request.name());        // null → skip
if (request.description() != null)  category.setDescription(...);             // null → skip
if (request.displayOrder() != null) category.setDisplayOrder(...);            // null → skip
if (request.isActive() != null)     category.setActive(request.isActive());  // false → SET
```

**STEP 3 — Dirty checking**
```
Only isActive changed → Hibernate issues:
UPDATE menu_categories SET is_active = false, updated_at = now()
WHERE id = 'a1b2c3...'

The entire "Starters" category is now hidden from the public menu.
All items under it are also hidden (public menu only fetches active categories).
```

---

## Flow 6 — DELETE /api/v1/restaurants/{restaurantId}/menu/categories/{categoryId}

```java
menuCategoryRepository.delete(category);
// Hard delete — the row is physically removed
// CASCADE: all menu_items with category_id = this id are also deleted
//          (PostgreSQL FK cascade or Hibernate orphan removal)
```

> ⚠️ Unlike Employee (soft deactivate), categories are hard deleted.
> The isActive flag provides the "hide without delete" pattern via updateCategory.

---

## Data objects summary

| Object | Type | Direction | Notes |
|--------|------|-----------|-------|
| `CreateCategoryRequest` | record DTO | IN | name, description, displayOrder |
| `UpdateCategoryRequest` | record DTO | IN | all fields nullable (partial) |
| `CreateItemRequest` | record DTO | IN | categoryId, name, price required |
| `UpdateItemRequest` | record DTO | IN | all fields nullable (partial) |
| `MenuItemResponse` | record DTO | OUT | single item shape |
| `CategoryWithItemsResponse` | record DTO | OUT | category + nested items list |
| `MenuCategory` | JPA Entity | Internal | maps to menu_categories |
| `MenuItem` | JPA Entity | Internal | maps to menu_items |

---

## Query count for public menu

For a restaurant with 5 categories:
```
1 query  → SELECT * FROM restaurants WHERE id = ?
1 query  → SELECT * FROM menu_categories WHERE restaurant_id = ? AND is_active = true
5 queries → SELECT * FROM menu_items WHERE category_id = ? AND is_available = true  (one per category)
─────────
7 queries total
```

This is the N+1 pattern. For the current scope it is acceptable.
In production, this would be optimized with a single JOIN query or a batch fetch.
