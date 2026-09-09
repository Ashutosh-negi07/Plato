# Order Module — Complete Data Flow

> Every HTTP request through the Order module traced step-by-step from HTTP wire to PostgreSQL and back.

---

## Architecture Overview

```
Customer Mobile Browser (X-Session-Token)       Kitchen / Waitstaff App (Bearer JWT)
              │                                                │
              │                                                │
              ▼                                                ▼
CustomerOrderController.java                         StaffOrderController.java
(/api/v1/customer/orders)                            (/api/v1/restaurants/{id}/orders, /api/v1/orders/{id}/status)
              │                                                │
              └───────────────────────┬────────────────────────┘
                                      │
                                      ▼
                             OrderService.java
                                      │
                                      ▼
                            OrderServiceImpl.java
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
      OrderRepository        OrderItemRepository     CustomerSessionService
              │                       │                       │
              ▼                       ▼                       ▼
      orders table (V8)     order_items table (V8)   customer_sessions table (V7)
```

### Table Relationships

```
restaurants
     │
     ├── restaurant_tables
     │        │
     │        └── customer_sessions
     │                 │
     │                 └── orders (session_id FK, table_id FK, restaurant_id FK)
     │                          │
     │                          └── order_items (order_id FK, menu_item_id FK)
     │                                     ▲
     └── menu_items ───────────────────────┘ (price snapshot captured)
```

---

## Core Business Rules

1. **Direct Ordering (Cart-less Architecture)**: An order placed in `PENDING` status effectively acts as the submitted cart. Customers can add notes, select items, and place orders directly under their active dining session.
2. **Price Snapshotting**: The `unit_price` of each dish is copied from `menu_items.price` at the moment of order placement. If an owner increases menu prices later, previously placed orders retain their historical price.
3. **Multi-Order Support**: A dining session can contain multiple separate orders (e.g., starters first, then drinks, then desserts). All orders link to the same `session_id`.
4. **In-Flight Cancellation Rules**:
   - Customers can cancel an order or remove specific items **only while status is `PENDING`**.
   - Once the kitchen marks an order `ACCEPTED` or `PREPARING`, customer modification is blocked (`400 Bad Request`).

---

## Flow 1 — POST /api/v1/customer/orders (Place Order)

### Request Example
```http
POST /api/v1/customer/orders
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
Content-Type: application/json

{
  "items": [
    {
      "menuItemId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "quantity": 2,
      "specialRequest": "Less spicy please"
    },
    {
      "menuItemId": "4c2a1188-1234-5678-90ab-cdef12345678",
      "quantity": 1,
      "specialRequest": null
    }
  ],
  "notes": "Please bring water first"
}
```

### Step-by-Step Trace

#### STEP 1 — Controller Dispatch & Session Validation
- `CustomerOrderController` receives request and extracts `X-Session-Token` header.
- Calls `sessionService.validateAndRefreshSession(sessionToken)`.
  - Verifies token exists in `customer_sessions`.
  - Verifies session is not expired (`isExpired() == false`).
  - Slides inactivity window forward: `expires_at = now() + 30 minutes`.

#### STEP 2 — Restaurant Status Verification
- Retrieves `Restaurant` via `restaurantRepository.findById(session.getRestaurantId())`.
- Validates:
  - `restaurant.getStatus() == RestaurantStatus.ACTIVE`
  - `restaurant.getAcceptingOrders() == true`
- If either condition fails $\rightarrow$ throws `ValidationException` (`400 Bad Request`).

#### STEP 3 — Menu Items Bulk Lookup & Validation
- Extracts all `menuItemId`s from request.
- Bulk queries items: `menuItemRepository.findAllById(requestedItemIds)`.
- For each requested item:
  - Validates item exists.
  - Verifies item belongs to this restaurant: `menuItem.getRestaurantId().equals(restaurant.getId())`.
  - Verifies availability: `menuItem.isAvailable() == true`.
- If an item is out of stock $\rightarrow$ throws `ValidationException("Menu item 'Paneer Tikka' is currently unavailable")`.

#### STEP 4 — Build Line Items & Price Snapshot
- For each item, captures `unit_price` from `menuItem.getPrice()`:
  ```java
  OrderItem orderItem = OrderItem.builder()
          .menuItemId(menuItem.getId())
          .quantity(itemReq.quantity())
          .unitPrice(menuItem.getPrice()) // snapshot!
          .specialRequest(itemReq.specialRequest())
          .status(OrderItemStatus.PENDING)
          .build();
  ```

#### STEP 5 — Totals Calculation & Order Numbering
- Generates human-readable order number: `generateOrderNumber()` $\rightarrow$ e.g., `ORD-20260909-4821`.
- Determines initial status:
  - If `restaurant.getAutoAcceptOrders() == true` $\rightarrow$ initial status is `ACCEPTED`.
  - Else $\rightarrow$ initial status is `PENDING`.
- Computes financials on `Order`:
  - $\text{subtotal} = \sum (\text{unit\_price} \times \text{quantity})$
  - $\text{tax} = \text{subtotal} \times (\text{restaurant.taxPercentage} / 100)$
  - $\text{grandTotal} = \text{subtotal} + \text{tax} - \text{discount}$

#### STEP 6 — Cascade Persistence
- Persists `Order` and its cascading `OrderItem` children via `orderRepository.save(order)`:
  ```sql
  INSERT INTO orders (id, restaurant_id, table_id, session_id, order_number, status, subtotal, tax, grand_total, notes, placed_at, created_at, updated_at)
  VALUES (gen_random_uuid(), ?, ?, ?, 'ORD-20260909-4821', 'PENDING', 450.00, 22.50, 472.50, 'Please bring water first', now(), now(), now());

  INSERT INTO order_items (id, order_id, menu_item_id, quantity, unit_price, special_request, status, created_at, updated_at)
  VALUES (gen_random_uuid(), ?, ?, 2, 175.00, 'Less spicy please', 'PENDING', now(), now()),
         (gen_random_uuid(), ?, ?, 1, 100.00, null, 'PENDING', now(), now());
  ```

#### STEP 7 — Return 201 Response
```json
{
  "success": true,
  "message": "Order placed successfully",
  "data": {
    "id": "1a2b3c4d-...",
    "restaurantId": "e3b0c442-...",
    "tableId": "c9a0d8e7-...",
    "tableNumber": "T-12",
    "sessionId": "7b8e1a22-...",
    "orderNumber": "ORD-20260909-4821",
    "status": "PENDING",
    "subtotal": 450.00,
    "tax": 22.50,
    "discount": 0.00,
    "grandTotal": 472.50,
    "notes": "Please bring water first",
    "placedAt": "2026-09-09T16:00:00",
    "completedAt": null,
    "items": [
      {
        "id": "3f4a5b6c-...",
        "menuItemId": "9b1deb4d-...",
        "menuItemName": "Paneer Butter Masala",
        "quantity": 2,
        "unitPrice": 175.00,
        "subtotal": 350.00,
        "specialRequest": "Less spicy please",
        "status": "PENDING"
      },
      {
        "id": "7d8e9f0a-...",
        "menuItemId": "4c2a1188-...",
        "menuItemName": "Garlic Naan",
        "quantity": 1,
        "unitPrice": 100.00,
        "subtotal": 100.00,
        "specialRequest": null,
        "status": "PENDING"
      }
    ]
  },
  "timestamp": "2026-09-09T16:00:00.150"
}
```

---

## Flow 2 — GET /api/v1/customer/orders (View Session Orders)

### Request Example
```http
GET /api/v1/customer/orders
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
```

### Step-by-Step Trace
1. Validates session token via `sessionService.validateAndRefreshSession(sessionToken)`.
2. Queries orders for this session:
   ```sql
   SELECT * FROM orders WHERE session_id = ? ORDER BY placed_at DESC;
   ```
3. Resolves table number and dish names in bulk for response mapping.
4. Returns list of all orders placed during this dining visit (`200 OK`).

---

## Flow 3 — DELETE /api/v1/customer/orders/{orderId}/items/{itemId} (Remove Item from Pending Order)

### Request Example
```http
DELETE /api/v1/customer/orders/1a2b3c4d-.../items/7d8e9f0a-...
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
```

### Step-by-Step Trace
1. Validates session token.
2. Checks order ownership: `orderRepository.findByIdAndSessionId(orderId, session.getId())`.
3. Verifies order is still `PENDING`:
   - If status is `ACCEPTED`, `PREPARING`, `READY`, or `SERVED` $\rightarrow$ throws `ValidationException("Items can only be removed while the order is pending confirmation")`.
4. Finds target `OrderItem` and removes it from `order.getItems()`.
5. Recalculates order financials:
   - If items list becomes empty $\rightarrow$ order status transitions to `CANCELLED`.
   - Else $\rightarrow$ recalculates `subtotal`, `tax`, and `grandTotal`.
6. Saves and returns updated `OrderResponse` (`200 OK`).

---

## Flow 4 — PATCH /api/v1/customer/orders/{orderId}/cancel (Cancel Entire Order)

### Request Example
```http
PATCH /api/v1/customer/orders/1a2b3c4d-.../cancel
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
```

### Step-by-Step Trace
1. Validates session token and order ownership (`order.sessionId == session.id`).
2. Checks status is `PENDING`. If kitchen has already accepted $\rightarrow$ `400 Bad Request`.
3. Transitions `order.status = CANCELLED`.
4. Cascades `status = CANCELLED` to all child line items.
5. Saves updated order and returns `200 OK`.

---

## Flow 5 — GET /api/v1/restaurants/{restaurantId}/orders (Staff / Kitchen Live Dashboard)

### Request Example
```http
GET /api/v1/restaurants/e3b0c442-.../orders?status=PENDING
Authorization: Bearer eyJhbGciOi... (Staff / Owner JWT)
```

### Step-by-Step Trace
1. `JwtAuthenticationFilter` validates caller token and sets `SecurityContextHolder`.
2. `@PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")` verifies caller authority.
3. `verifyStaffAccess()` checks tenant authorization:
   - `SUPER_ADMIN` $\rightarrow$ allowed.
   - `OWNER` $\rightarrow$ must own this `restaurantId`.
   - `EMPLOYEE` $\rightarrow$ must be an assigned staff member of this restaurant (`employeeRepository.existsByUserIdAndRestaurantId`).
4. Queries orders:
   - If `status` query param provided $\rightarrow$ `findByRestaurantIdAndStatusOrderByPlacedAtAsc(restaurantId, status)` (oldest orders first for kitchen priority).
   - If omitted $\rightarrow$ `findByRestaurantIdOrderByPlacedAtDesc(restaurantId)`.
5. Resolves table numbers and dish names.
6. Returns list of orders (`200 OK`).

---

## Flow 6 — PATCH /api/v1/orders/{orderId}/status (Staff Status Transition)

### Request Example
```http
PATCH /api/v1/orders/1a2b3c4d-.../status
Authorization: Bearer eyJhbGciOi... (Staff / Chef JWT)
Content-Type: application/json

{
  "status": "PREPARING"
}
```

### Step-by-Step Trace
1. `StaffOrderController` validates JWT and authority.
2. Checks caller staff permission for `order.getRestaurantId()`.
3. Validates state transition:
   - Cannot modify a `CANCELLED` order.
   - Cannot modify a `SERVED` order.
4. Sets `order.setStatus(newStatus)`.
5. If `newStatus == SERVED`:
   - Sets `order.setCompletedAt(now())`.
   - Updates all non-cancelled child items to `SERVED`.
6. If `newStatus == CANCELLED`:
   - Cascades `CANCELLED` to all child items.
7. Saves and returns updated `OrderResponse` (`200 OK`).

---

## Error Handling Matrix

| Scenario | Exception | HTTP Code | Response Message |
|---|---|:---:|---|
| Missing / invalid session token | `SessionExpiredException` | `401` | `"Invalid session token"` |
| Session has expired (inactivity) | `SessionExpiredException` | `401` | `"Your session has expired. Please scan the QR code again."` |
| Restaurant suspended / not active | `ValidationException` | `400` | `"This restaurant is not currently active"` |
| Restaurant not accepting orders | `ValidationException` | `400` | `"This restaurant is currently not accepting orders"` |
| Menu item does not belong to restaurant | `ValidationException` | `400` | `"Menu item '...' does not belong to this restaurant"` |
| Menu item is out of stock | `ValidationException` | `400` | `"Menu item '...' is currently unavailable"` |
| Customer tries to cancel after kitchen accepted | `ValidationException` | `400` | `"Order cannot be cancelled because it is already ACCEPTED"` |
| Customer tries to remove item from preparing order | `ValidationException` | `400` | `"Items can only be removed while the order is pending confirmation"` |
| Staff tries to update cancelled order | `ValidationException` | `400` | `"Cannot update status of a cancelled order"` |
| Staff not assigned to restaurant | `UnauthorizedAccessException` | `403` | `"You do not have permission to access orders for this restaurant"` |
| Order not found | `ResourceNotFoundException` | `404` | `"Order not found with id: ..."` |
| Line item not found | `ResourceNotFoundException` | `404` | `"OrderItem not found with id: ..."` |
