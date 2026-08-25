# Menu Module — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### MenuCategory.java — The Entity

**What it is**: A JPA entity mapping to `menu_categories`.
Represents a section of a restaurant's menu (e.g., "Starters", "Mains", "Desserts").

**Key design decisions**:

```java
@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;
// UUID field, NOT @ManyToOne Restaurant — avoids loading the full restaurant
// object every time a category is fetched. Same rationale as Employee.

@Column(name = "display_order", nullable = false)
private int displayOrder;
// Controls the sort order of categories on the public menu.
// Lower number = appears first.
// The owner sets this manually to arrange the menu sections.

@Builder.Default
@Column(name = "is_active", nullable = false)
private boolean isActive = true;
// Allows owners to hide an entire category (and all its items)
// without deleting anything.
// Public menu filters: WHERE is_active = true
```

---

### MenuItem.java — The Entity

**What it is**: A JPA entity mapping to `menu_items`.
Represents one dish or drink within a category.

**Key design decisions**:

```java
@Column(name = "category_id", nullable = false)
private UUID categoryId;

@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;
// restaurantId stored directly on menu_items (denormalized).
// Without this: to check "does this item belong to this restaurant?"
// you'd have to join menu_items → menu_categories → restaurants.
// With this: one WHERE clause: restaurant_id = ? — fast, no join.

@Column(name = "price", nullable = false, precision = 10, scale = 2)
private BigDecimal price;
// BigDecimal (not double, not float) for monetary values.
// double/float have binary floating-point precision issues:
// 0.1 + 0.2 = 0.30000000000000004 in floating point.
// BigDecimal stores exact decimal values → correct to the paisa.

@Builder.Default
@Column(name = "is_available", nullable = false)
private boolean isAvailable = true;
// Per-item availability toggle.
// Owner marks item unavailable when it's out of stock.
// Public menu filters: WHERE is_available = true
// The item stays in the DB — no delete needed.
```

---

### V6__create_menu.sql — Flyway Migration

**Two tables created**:
- `menu_categories` — restaurant's menu sections
- `menu_items` — individual dishes under each section

**`NUMERIC(10, 2)` for price**:
- `NUMERIC(10, 2)` = up to 10 total digits, 2 after decimal point
- Max price: 99,999,999.99 (₹9.9 crore) — more than enough
- PostgreSQL's NUMERIC is arbitrary-precision — no floating-point rounding
- Mapped to `BigDecimal` in Java — exact decimal math guaranteed

**Why `restaurant_id` is on `menu_items` (denormalization)**:
```
Normalized design:
  menu_items → category_id → menu_categories → restaurant_id
  To check restaurant: SELECT mi.* FROM menu_items mi
                       JOIN menu_categories mc ON mi.category_id = mc.id
                       WHERE mc.restaurant_id = ?

Denormalized design:
  menu_items → restaurant_id (direct)
  To check restaurant: SELECT * FROM menu_items WHERE restaurant_id = ?
  Simpler, faster, no join needed.
```

The tradeoff: `restaurant_id` must be kept consistent — when moving an item to a
category in a different restaurant, both `category_id` and `restaurant_id` must update.
In this system, items are never moved between restaurants, so this is safe.

---

### MenuCategoryRepository.java & MenuItemRepository.java

**Why multiple `findBy...` methods with different filters?**

Owner view needs everything (including inactive/unavailable) to manage the menu:
```java
findByRestaurantIdOrderByDisplayOrderAsc(restaurantId)
// WHERE restaurant_id = ? ORDER BY display_order ASC
// Returns ALL categories — active and inactive
```

Public menu needs only what customers should see:
```java
findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId)
// WHERE restaurant_id = ? AND is_active = true ORDER BY display_order ASC
// Returns ONLY active categories
```

Same pattern for items:
```java
findByCategoryIdOrderByDisplayOrderAsc(categoryId)           // owner view
findByCategoryIdAndIsAvailableTrueOrderByDisplayOrderAsc(categoryId)  // public
```

**`findByIdAndRestaurantId` — the cross-restaurant guard**:
```java
Optional<MenuCategory> findByIdAndRestaurantId(UUID id, UUID restaurantId);
Optional<MenuItem> findByIdAndRestaurantId(UUID id, UUID restaurantId);
// Used for every update/delete operation.
// Ensures the resource belongs to the restaurant in the URL path.
// If mismatch → Optional.empty() → .orElseThrow() → 404
```

---

### MenuCategoryMapper.java

**3 methods, key decision: `toResponse` vs `toResponseWithoutItems`**

```java
toResponse(MenuCategory, List<MenuItemResponse>)   // builds full tree
toResponseWithoutItems(MenuCategory)               // items = List.of()
```

Why two methods? The owner's category list (`GET /menu/categories`) returns all categories
without their items — the owner doesn't need item details when just viewing category names.
Loading all items for all categories every time would be wasteful.

The public menu (`GET /menu`) explicitly fetches items for each category and passes them
into `toResponse(category, items)` to build the nested tree.

---

### MenuServiceImpl.java — Business Logic

**`verifyOwnership()` private helper**:
```java
private Restaurant verifyOwnership(UUID restaurantId, UUID ownerId) {
    Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
    if (!restaurant.getOwnerId().equals(ownerId)) {
        throw new UnauthorizedAccessException("You do not own this restaurant");
    }
    return restaurant;
}
```
Called at the start of every write method (create, update, delete).
Extracted into a helper to avoid copy-pasting the same 5 lines 8 times.
This is the DRY principle (Don't Repeat Yourself) applied to security checks.

**Category validation on item creation**:
```java
menuCategoryRepository.findByIdAndRestaurantId(request.categoryId(), restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
```
Why check if the category belongs to this restaurant when creating an item?
Without this: `POST /restaurants/ownerA-restaurant/menu/items` with `categoryId` from
Owner B's restaurant would succeed — the item would be saved under Owner B's category.
With this check: the category must belong to the same restaurant as the URL path.

**`toggleAvailability` — flip without request body**:
```java
item.setAvailable(!item.isAvailable());
```
No request body needed — the service always toggles the opposite of the current value.
This is simpler and more reliable than sending `{"isAvailable": false}` —
you can't accidentally send the wrong value.

**`getPublicMenu` — no auth, no ownerId**:
```java
public List<CategoryWithItemsResponse> getPublicMenu(UUID restaurantId) {
    // Only takes restaurantId — no caller context needed
    // Returns: active categories → available items (nested)
}
```
This is called by unauthenticated customers scanning a QR code.
The method signature explicitly has no `UUID callerId` or `String callerRole` —
making it obvious that this is a public endpoint.

---

### MenuController.java — The HTTP Layer

**`GET /menu` — the only public endpoint**:
```java
@GetMapping                          // maps GET /api/v1/restaurants/{id}/menu
public ApiResponse<...> getPublicMenu(@PathVariable UUID restaurantId) {
    // NO @PreAuthorize
    // NO getCurrentUserId()
    // No SecurityContextHolder access
    return ApiResponse.ok(..., menuService.getPublicMenu(restaurantId));
}
```

**Why no `@PreAuthorize` here?**
Spring Security checks for `@PreAuthorize` only if the request reaches the method.
But the public menu URL is in `SecurityConfig` as `.permitAll()` — meaning Spring skips
authentication entirely for this URL. Even if there's no JWT, the method runs.
Not adding `@PreAuthorize` is correct and intentional — it signals "this is public."

**Base mapping: `/api/v1/restaurants/{restaurantId}/menu`**:
- Categories live at: `/menu/categories` and `/menu/categories/{id}`
- Items live at: `/menu/items` and `/menu/items/{id}`
- Public menu: `/menu` (the base path itself)

All under the restaurant's path — enforces that menu management is always
scoped to a specific restaurant.

---

### SecurityConfig — the `permitAll` line

```java
.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/*/menu").permitAll()
```

`*` is a path wildcard — matches any single path segment (any UUID).
`/api/v1/restaurants/3fa85f64-.../menu` → matched → permitted without auth.
`/api/v1/restaurants/3fa85f64-.../menu/categories` → NOT matched → requires auth.

Only the exact `/menu` endpoint is public. All sub-paths (`/categories`, `/items`) still
require a valid JWT.

---

## Key Concepts Summary

### Why `BigDecimal` for price and not `double`?

`double` uses binary floating-point (IEEE 754). Binary can't represent all decimal fractions exactly:
```java
double a = 0.1 + 0.2;   // → 0.30000000000000004
```
For money this is unacceptable. A ₹280.50 item could be stored as ₹280.4999999...

`BigDecimal` uses decimal arithmetic — exact representation:
```java
BigDecimal a = new BigDecimal("0.1").add(new BigDecimal("0.2"));
// → 0.3 exactly
```
PostgreSQL `NUMERIC(10, 2)` similarly stores exact decimal values.
Always use `BigDecimal` + `NUMERIC` for monetary values in financial systems.

---

### `is_active` vs `is_available` — two different toggles

| Field | On | Entity | What it hides |
|---|---|---|---|
| `is_active` | `menu_categories` | `MenuCategory` | The entire category + all its items from public menu |
| `is_available` | `menu_items` | `MenuItem` | A single dish from public menu |

Owner use cases:
- "We're not serving desserts today" → set category `is_active = false` → entire Desserts section vanishes
- "Paneer Tikka is sold out" → toggle item `is_available = false` → just that one dish vanishes

Neither deletes data. Both are reversible with a single PATCH/PUT call.

---

### Hard delete vs soft delete (Menu module choice)

**Categories**: Hard delete (`menuCategoryRepository.delete(category)`)
- Cascades to all items in that category (FK cascade or DB constraint)
- Rationale: if a category is permanently deleted, its items have no parent — they should go too
- The `is_active = false` flag provides the "temporary hide" use case

**Items**: Hard delete (`menuItemRepository.delete(item)`)
- Rationale: a menu item leaving the menu permanently has no other references in this scope
- No orders or sessions reference items yet (those modules are upcoming)

> **Note**: Once Orders module is built, items should become soft-deleted instead.
> An order contains `menu_item_id` — hard deleting an item would orphan historical order data.
> This is a known design debt to address in the Orders module.

---

### The N+1 pattern in `getPublicMenu`

```java
categories.stream().map(cat -> {
    List<MenuItemResponse> items = menuItemRepository
            .findByCategoryIdAndIsAvailableTrueOrderByDisplayOrderAsc(cat.getId())
            ...
})
```

For a restaurant with 5 categories, this issues:
- 1 query for categories
- 5 queries for items (1 per category)
= **6 queries total** (N+1 where N=5)

This is acceptable for a learning project. In production, you would use:
```java
// Option 1: JOIN FETCH (JPQL)
@Query("SELECT c FROM MenuCategory c LEFT JOIN FETCH c.items WHERE ...")

// Option 2: Two queries + group in Java
List<MenuItem> allItems = menuItemRepository.findByRestaurantIdAndIsAvailableTrue(restaurantId);
Map<UUID, List<MenuItem>> byCategory = allItems.stream()
        .collect(Collectors.groupingBy(MenuItem::getCategoryId));
// Then map each category using byCategory.get(cat.getId())
```

---

## Interview Questions & Answers

---

**Q: Why is `restaurant_id` stored on `menu_items` if you can derive it via `menu_categories`?**

A: Denormalization for performance and simplicity. To verify "does this item belong to this restaurant?"
using the normalized path, you'd need a JOIN:
`SELECT * FROM menu_items mi JOIN menu_categories mc ON mi.category_id = mc.id WHERE mc.restaurant_id = ?`
With `restaurant_id` directly on `menu_items`, it's:
`SELECT * FROM menu_items WHERE restaurant_id = ?`
Simpler query, no join, faster index lookup.
The tradeoff is data duplication — `restaurant_id` appears on both tables — but since items
are never moved between restaurants, this stays consistent.

---

**Q: Why use `BigDecimal` for price instead of `double`?**

A: `double` uses binary floating-point (IEEE 754) which cannot represent all decimal fractions
exactly. `0.1 + 0.2` in floating point is `0.30000000000000004`, not `0.3`.
For monetary values, even small rounding errors compound across thousands of transactions
and lead to accounting discrepancies.
`BigDecimal` uses exact decimal arithmetic — `0.1 + 0.2 = 0.3` always.
PostgreSQL's `NUMERIC(10, 2)` is also exact (arbitrary precision).
Rule: always use `BigDecimal` + `NUMERIC` for money in Java/PostgreSQL.

---

**Q: The public menu endpoint has no `@PreAuthorize`. How does Spring Security handle it?**

A: Two things work together:
1. `SecurityConfig` has `.requestMatchers(HttpMethod.GET, "/api/v1/restaurants/*/menu").permitAll()`
   — Spring skips authentication for this URL pattern entirely.
2. The controller method has no `@PreAuthorize` annotation.
   — Even if a request somehow reached this method with no authentication,
   there's nothing to block it.

The JWT filter still runs (it runs on every request), but if it finds no token, it sets
anonymous authentication and proceeds. The `permitAll()` rule lets anonymous authentication pass.

---

**Q: What is the difference between `is_active` (category) and `is_available` (item)?**

A: Different granularity of control:
- `is_active` on a category: hides the entire section (Starters, Mains, etc.) and all dishes within it from the public menu
- `is_available` on an item: hides a single dish while leaving the rest of the category visible

An owner might use `is_active = false` when a category is seasonal ("Summer Specials" in winter).
They'd use `is_available = false` when a specific dish is out of stock ("Dal is sold out today").
Both are reversible with a single API call. Neither deletes data.

---

**Q: Why are categories hard-deleted but employees soft-deleted?**

A: Different referential integrity requirements.
Employees may eventually be referenced by orders, sessions, or audit logs.
Soft-deleting keeps the `employee_id` valid in those references.

Menu categories and items (in the current scope) have no such downstream references.
An inactive category being permanently removed with its items is a clean operation.
However, this is noted as design debt — once the Orders module is built, items
should be soft-deleted because `order_items` will reference `menu_item_id`.
Hard-deleting an item that appears in a historical order would break that reference.

---

**Q: How does `displayOrder` work and why not sort alphabetically?**

A: `displayOrder` is an integer the owner sets manually to control the sequence of
categories and items on the menu.
Sort query: `ORDER BY display_order ASC` — lower number appears first.

Why not alphabetical? A restaurant might want "Starters" first, then "Mains",
then "Desserts", then "Drinks" — alphabetical would give "Desserts, Drinks, Mains, Starters"
which is neither natural nor consistent with how restaurants present their menus.
Manual ordering gives the owner full control over the customer's menu reading experience.

---

**Q: What is `verifyOwnership()` and why is it a private helper instead of being inlined?**

A: `verifyOwnership` performs two operations that repeat in every write method:
1. `findById(restaurantId).orElseThrow(...)` — verify the restaurant exists
2. `restaurant.getOwnerId().equals(ownerId)` — verify the caller owns it

In `MenuServiceImpl`, there are 6 write methods. Without the helper, those 5 lines would
be copy-pasted 6 times — 30 lines of identical code.
If the error message or logic ever changes, you'd need to update 6 places.
Extracted into `verifyOwnership()`: logic in one place, called 6 times — DRY principle.
It also returns the `Restaurant` object in case the caller needs it (like `getCategories`).

---

**Q: What would happen if you didn't check that `categoryId` belongs to the restaurant when creating an item?**

A: Cross-restaurant data corruption.
Scenario: Owner A's restaurant has restaurant_id = `AAA`. Owner B's category has id = `BBB`.
Without the check: Owner A sends `POST /restaurants/AAA/menu/items` with `categoryId = BBB`.
The item is saved with `restaurant_id = AAA` and `category_id = BBB` (Owner B's category).
The item now appears in Owner B's category on the public menu — Owner A contaminated Owner B's data.

With `findByIdAndRestaurantId(categoryId, restaurantId)`:
- The query checks `WHERE id = BBB AND restaurant_id = AAA`
- Owner B's category has `restaurant_id = BBB` → no row found → 404
- The contamination is blocked silently.
