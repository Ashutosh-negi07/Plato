# Plato — Updated Master Plan (2-Week Backend Completion)

> **Last updated**: 2026-08-21  
> **Total Java files**: ~56 (growing)  
> **Total SQL migrations**: 4 (V1–V4 applied) — V5 in progress  
> **Backend completeness**: ~50% by files · ~64% by endpoints · Staff side ~80% done

---

## Honest Completion Status

### What "Done" actually means here
A module is only **truly done** when it has: Entity + Migration + Repository + Service Interface + ServiceImpl + DTOs + Mapper + Controller + endpoints tested.

---

### Layer 1 — Foundation (100% Done ✅)

These files exist and work. You never touch these again.

| File | Status |
|------|--------|
| `PlatoApplication.java` | ✅ |
| `common/BaseEntity.java` | ✅ |
| `common/ApiResponse.java` | ✅ |
| `common/PagedResponse.java` | ✅ |
| `config/AppConfig.java` | ✅ |
| `config/SecurityConfig.java` | ✅ |
| `exception/` (all 7 files) | ✅ |
| `security/` (all 4 files) | ✅ |
| `application.yml` + `application-local.yml` | ✅ |

---

### Layer 2 — Database (45% Done ⚠️)

| Migration | Status | Table |
|-----------|--------|-------|
| V1__create_enums.sql | ✅ Done | All 9 enum types |
| V2__create_users.sql | ✅ Done | `users` |
| V3__create_restaurants.sql | ✅ Done | `restaurants` |
| V4__create_restaurant_tables.sql | ✅ Done | `restaurant_tables` |
| V5__create_employees.sql | 🔄 In progress | `employees` |
| V6 through V11 | 🔲 6 missing | menu, sessions, cart, orders, payments, feedback |

---

### Layer 3 — Auth Module (100% Done ✅)

| File | Status |
|------|--------|
| `auth/AuthController.java` | ✅ |
| `auth/AuthService.java` | ✅ |
| `auth/AuthServiceImpl.java` | ✅ |
| `auth/dto/LoginRequest.java` | ✅ |
| `auth/dto/LoginResponse.java` | ✅ |

**One working endpoint**: `POST /api/v1/auth/login`

---

### Layer 4 — User Module (100% Done ✅)

| File | Status |
|------|--------|
| `user/User.java` | ✅ Done |
| `user/UserRole.java` | ✅ Done |
| `user/UserStatus.java` | ✅ Done |
| `user/UserRepository.java` | ✅ Done |
| `user/UserService.java` | ✅ Done |
| `user/UserServiceImpl.java` | ✅ Done |
| `user/DataInitializer.java` | ✅ Done |
| `user/dto/UserResponse.java` | ✅ Done |
| `user/dto/CreateUserRequest.java` | ✅ Done |
| `user/dto/UpdateUserRequest.java` | ✅ Done |
| `user/UserMapper.java` | ✅ Done |
| `user/UserController.java` | ✅ Done |

**Working endpoints**: 6 ✅

---

### Layer 5 — Restaurant Module (100% Done ✅)

| File | Status |
|------|--------|
| `restaurant/Restaurant.java` | ✅ Done |
| `restaurant/RestaurantStatus.java` | ✅ Done |
| `restaurant/RestaurantRepository.java` | ✅ Done |
| `restaurant/dto/CreateRestaurantRequest.java` | ✅ Done |
| `restaurant/dto/UpdateRestaurantRequest.java` | ✅ Done |
| `restaurant/dto/RestaurantSettingsRequest.java` | ✅ Done |
| `restaurant/dto/RestaurantResponse.java` | ✅ Done |
| `restaurant/RestaurantMapper.java` | ✅ Done |
| `restaurant/RestaurantService.java` | ✅ Done |
| `restaurant/RestaurantServiceImpl.java` | ✅ Done |
| `restaurant/RestaurantController.java` | ✅ Done |

**Working endpoints**: 7 ✅

---

### Layer 6 — Table Module (100% Done ✅)

| File | Status |
|------|--------|
| `table/RestaurantTable.java` | ✅ Done |
| `table/TableStatus.java` | ✅ Done |
| `table/TableRepository.java` | ✅ Done |
| `table/QrTokenService.java` | ✅ Done |
| `table/dto/CreateTableRequest.java` | ✅ Done |
| `table/dto/UpdateTableRequest.java` | ✅ Done |
| `table/dto/TableResponse.java` | ✅ Done |
| `table/TableMapper.java` | ✅ Done |
| `table/TableService.java` | ✅ Done |
| `table/TableServiceImpl.java` | ✅ Done |
| `table/TableController.java` | ✅ Done |

**Working endpoints**: 6 ✅ (all tested via curl)

---

### Layer 7 — Employee Module (10% Done 🔄)

| File | Status |
|------|--------|
| `V5__create_employees.sql` | 🔄 In progress |
| `employee/EmployeeRole.java` | 🔲 |
| `employee/Employee.java` | 🔲 |
| `employee/EmployeeRepository.java` | 🔲 |
| `employee/dto/AssignEmployeeRequest.java` | 🔲 |
| `employee/dto/UpdateEmployeeRoleRequest.java` | 🔲 |
| `employee/dto/EmployeeResponse.java` | 🔲 |
| `employee/EmployeeMapper.java` | 🔲 |
| `employee/EmployeeService.java` | 🔲 |
| `employee/EmployeeServiceImpl.java` | 🔲 |
| `employee/EmployeeController.java` | 🔲 |

**Working endpoints**: 0 (in progress)

---

### Everything Else — 0% Done 🔲

Menu, Customer Sessions, Cart, Orders, Payments, Feedback, WebSocket — not started.

---

## Summary Score

| Area | Done | Total | % |
|------|------|-------|---|
| Foundation files | 18 | 18 | 100% |
| Migrations applied | 4 | 11 | 36% |
| Auth endpoints | 1 | 1 | 100% |
| User module files | 12 | 12 | 100% |
| User endpoints | 6 | 6 | 100% |
| Restaurant module files | 11 | 11 | 100% |
| Restaurant endpoints | 7 | 7 | 100% |
| Table module files | 11 | 11 | 100% |
| Table endpoints | 6 | 6 | 100% |
| Employee module files | 1 | 11 | 9% |
| Employee endpoints | 0 | 4 | 0% |
| Menu + Customer Flow | 0 | ~57 | 0% |
| **Overall (files)** | **~56** | **~121** | **~46%** |
| **Overall (endpoints)** | **20** | **~45** | **~44%** |

---

---

# 2-WEEK BUILD ORDER

**Rule**: Each day builds on the previous. Do not skip. Each day has one clear goal and a working test at the end.

---

## WEEK 1 — Staff-Side Features (What restaurant owners/staff use)

---

### Day 1 — Finish the User Module

**Why first**: Every other module references users. Get the CRUD done before anything else.

**Files to create** (in order):

1. `user/dto/UserResponse.java`
2. `user/dto/CreateUserRequest.java`
3. `user/dto/UpdateUserRequest.java`
4. `user/UserMapper.java`
5. Update `user/UserService.java` — add 6 API method signatures
6. Update `user/UserServiceImpl.java` — implement those 6 methods
7. `user/UserController.java`

**Endpoints unlocked**:
- `GET /api/v1/users` — list all users (SUPER_ADMIN)
- `GET /api/v1/users/{id}` — get one (SUPER_ADMIN or self)
- `POST /api/v1/users` — create user (SUPER_ADMIN)
- `PATCH /api/v1/users/{id}` — update user
- `PATCH /api/v1/users/{id}/status` — activate/deactivate (SUPER_ADMIN)
- `DELETE /api/v1/users/{id}` — soft delete (SUPER_ADMIN)

**End-of-day test**: Login as Super Admin → create an Owner user via `POST /api/v1/users` → get it back via `GET /api/v1/users/{id}`.

---

### Day 2 — Finish the Restaurant Module

**Why second**: Owners need to create restaurants before anything else (tables, employees, menu all live under a restaurant).

**Files to create**:

1. `restaurant/dto/CreateRestaurantRequest.java`
2. `restaurant/dto/UpdateRestaurantRequest.java`
3. `restaurant/dto/RestaurantSettingsRequest.java`
4. `restaurant/dto/RestaurantResponse.java`
5. `restaurant/RestaurantMapper.java`
6. `restaurant/RestaurantService.java`
7. `restaurant/RestaurantServiceImpl.java`
8. `restaurant/RestaurantController.java`

**Endpoints unlocked**:
- `POST /api/v1/restaurants` — Owner creates restaurant
- `GET /api/v1/restaurants` — Owner sees own, Super Admin sees all
- `GET /api/v1/restaurants/{id}`
- `PUT /api/v1/restaurants/{id}` — update info
- `PATCH /api/v1/restaurants/{id}/status` — SUPER_ADMIN only
- `GET /api/v1/restaurants/{id}/settings`
- `PUT /api/v1/restaurants/{id}/settings`

**End-of-day test**: Login as Owner → create a restaurant → update its settings → verify tax % is saved.

---

### Day 3 — Tables & QR Code Module

**Why third**: Tables must exist before customer sessions (Day 6). QR tokens live on tables.

**Migration first**: Write `V4__create_restaurant_tables.sql`, restart Spring Boot, verify Flyway runs it.

```sql
CREATE TABLE restaurant_tables (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
  table_number    VARCHAR(20) NOT NULL,
  capacity        INT,
  qr_token        VARCHAR(64) NOT NULL UNIQUE,
  status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(restaurant_id, table_number)
);
CREATE INDEX idx_tables_restaurant_id ON restaurant_tables(restaurant_id);
CREATE INDEX idx_tables_qr_token ON restaurant_tables(qr_token);
```

**Files to create**: (package: `com.miniproject.plato.table`)

1. `table/RestaurantTable.java` — entity
2. `table/TableRepository.java`
3. `table/QrTokenService.java` — generates `SecureRandom` UUID token
4. `table/TableService.java` — interface
5. `table/TableServiceImpl.java`
6. `table/dto/CreateTableRequest.java`
7. `table/dto/UpdateTableRequest.java`
8. `table/dto/TableResponse.java`
9. `table/TableMapper.java`
10. `table/TableController.java`

**Endpoints unlocked** (all under `/api/v1/restaurants/{restaurantId}/tables`):
- `POST /` — add table
- `GET /` — list tables
- `GET /{id}` — get one
- `PUT /{id}` — update
- `DELETE /{id}` — soft delete
- `POST /{id}/qr/regenerate` — new QR token

**End-of-day test**: Create 3 tables for a restaurant → regenerate QR on table 2 → verify old token no longer matches.

---

### Day 4 — Employee Module

**Why fourth**: Employees need to exist so they can manage orders and sessions later.

**Migration first**: `V5__create_employees.sql`

```sql
CREATE TABLE employees (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID          NOT NULL REFERENCES users(id),
  restaurant_id   UUID          NOT NULL REFERENCES restaurants(id),
  role            employee_role NOT NULL,
  is_active       BOOLEAN       NOT NULL DEFAULT true,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE(user_id, restaurant_id)
);
CREATE INDEX idx_employees_restaurant_id ON employees(restaurant_id);
CREATE INDEX idx_employees_user_id ON employees(user_id);
```

**Files to create**: (package: `com.miniproject.plato.employee`)

1. `employee/Employee.java` — entity
2. `employee/EmployeeRole.java` — enum (MANAGER, CHEF, WAITER, CASHIER)
3. `employee/EmployeeRepository.java`
4. `employee/EmployeeService.java`
5. `employee/EmployeeServiceImpl.java`
6. `employee/dto/AssignEmployeeRequest.java`
7. `employee/dto/UpdateEmployeeRoleRequest.java`
8. `employee/dto/EmployeeResponse.java`
9. `employee/EmployeeMapper.java`
10. `employee/EmployeeController.java`

**Endpoints unlocked** (all under `/api/v1/restaurants/{restaurantId}/employees`):
- `POST /` — assign employee to restaurant
- `GET /` — list active employees
- `PATCH /{id}/role` — change role
- `DELETE /{id}` — deactivate

**End-of-day test**: Assign an EMPLOYEE user as CHEF to a restaurant → change their role to MANAGER → deactivate them.

---

### Day 5 — Menu Module

**Why fifth**: Menu must exist before customers can browse or add to cart.

**Migration first**: `V6__create_menu.sql`

```sql
CREATE TABLE menu_categories (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
  name            VARCHAR(100) NOT NULL,
  description     TEXT,
  display_order   INT         NOT NULL DEFAULT 0,
  is_active       BOOLEAN     NOT NULL DEFAULT true,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE menu_items (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id   UUID          NOT NULL REFERENCES restaurants(id),
  category_id     UUID          REFERENCES menu_categories(id),
  name            VARCHAR(255)  NOT NULL,
  description     TEXT,
  price           NUMERIC(10,2) NOT NULL,
  image_url       TEXT,
  is_available    BOOLEAN       NOT NULL DEFAULT true,
  is_vegetarian   BOOLEAN       NOT NULL DEFAULT false,
  display_order   INT           NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_menu_items_restaurant ON menu_items(restaurant_id);
CREATE INDEX idx_menu_items_category ON menu_items(category_id);
CREATE INDEX idx_menu_categories_restaurant ON menu_categories(restaurant_id);
```

**Files to create**: (package: `com.miniproject.plato.menu`)

1. `menu/MenuCategory.java`
2. `menu/MenuItem.java`
3. `menu/MenuCategoryRepository.java`
4. `menu/MenuItemRepository.java`
5. `menu/MenuService.java`
6. `menu/MenuServiceImpl.java`
7. `menu/dto/CreateCategoryRequest.java`
8. `menu/dto/CreateMenuItemRequest.java`
9. `menu/dto/UpdateMenuItemRequest.java`
10. `menu/dto/MenuCategoryResponse.java`
11. `menu/dto/MenuItemResponse.java`
12. `menu/dto/FullMenuResponse.java`
13. `menu/MenuMapper.java`
14. `menu/MenuController.java`

**Endpoints unlocked** (all under `/api/v1/restaurants/{restaurantId}/menu`):
- `POST /categories` — create category (OWNER)
- `GET /` — full menu (PUBLIC — no auth needed)
- `POST /items` — add item (OWNER)
- `PATCH /items/{id}` — update item (OWNER)
- `PATCH /items/{id}/availability` — toggle available (OWNER / MANAGER)
- `DELETE /items/{id}` — soft delete (OWNER)

**End-of-day test**: Create 2 categories + 5 items → hit the public menu endpoint without any auth header → verify all items returned.

---

### Day 6 — Week 1 Integration Test

**Don't write new code. Spend this day testing everything built.**

Checklist:
- [ ] Login as SUPER_ADMIN → create OWNER user
- [ ] Login as OWNER → create restaurant → set tax 10%, service charge 5%
- [ ] Add 3 tables to restaurant → note QR tokens
- [ ] Assign an EMPLOYEE user as CHEF
- [ ] Create 2 menu categories + 4 menu items
- [ ] Hit public menu endpoint without auth — confirm it returns
- [ ] Try all error cases: wrong owner accessing another's restaurant (expect 403), invalid IDs (expect 404), duplicate table number (expect 409)
- [ ] Ensure Spring Boot starts clean with all 6 Flyway migrations applied

---

## WEEK 2 — Customer Flow (What customers use when they scan QR)

---

### Day 7 — Customer Sessions

**Why this order**: Sessions are the gateway for the entire customer flow. Cart, Orders, Payments all require an active session.

**Migration first**: `V7__create_customer_sessions.sql`

```sql
CREATE TABLE customer_sessions (
  id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
  table_id        UUID           NOT NULL REFERENCES restaurant_tables(id),
  session_token   VARCHAR(64)    NOT NULL UNIQUE,
  customer_name   VARCHAR(100),
  status          session_status NOT NULL DEFAULT 'ACTIVE',
  expires_at      TIMESTAMPTZ    NOT NULL,
  created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_token ON customer_sessions(session_token);
CREATE INDEX idx_sessions_table ON customer_sessions(table_id);
CREATE INDEX idx_sessions_restaurant ON customer_sessions(restaurant_id);
```

**Files to create**: (package: `com.miniproject.plato.session`)

1. `session/CustomerSession.java`
2. `session/CustomerSessionRepository.java`
3. `session/CustomerSessionService.java`
4. `session/CustomerSessionServiceImpl.java`
5. `session/CustomerSessionFilter.java` ← most important file this day
6. `session/dto/StartSessionRequest.java`
7. `session/dto/SessionResponse.java`
8. `session/SessionController.java`
9. Update `config/SecurityConfig.java` — add `CustomerSessionFilter` to chain

**Endpoints unlocked**:
- `POST /api/v1/sessions/start` — PUBLIC, takes `{ qrToken, customerName }`
- `GET /api/v1/restaurants/{rId}/sessions` — OWNER/MANAGER, see active sessions
- `POST /api/v1/sessions/{id}/close` — STAFF closes session

**End-of-day test**: Using a QR token from a table created on Day 3 → POST to start session → copy the `sessionToken` → use it as `X-Session-Token` header to call `GET /api/v1/restaurants/{rId}/menu` → confirm it works.

---

### Day 8 — Cart

**Why now**: Sessions exist. Customers can now add items to cart before placing an order.

**Migration first**: `V8__create_cart_items.sql`

```sql
CREATE TABLE cart_items (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID          NOT NULL REFERENCES customer_sessions(id),
  menu_item_id    UUID          NOT NULL REFERENCES menu_items(id),
  quantity        INT           NOT NULL DEFAULT 1,
  unit_price      NUMERIC(10,2) NOT NULL,
  notes           TEXT,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE(session_id, menu_item_id)
);
CREATE INDEX idx_cart_session ON cart_items(session_id);
```

**Files to create**: (package: `com.miniproject.plato.cart`)

1. `cart/CartItem.java`
2. `cart/CartItemRepository.java`
3. `cart/CartService.java`
4. `cart/CartServiceImpl.java`
5. `cart/dto/AddToCartRequest.java`
6. `cart/dto/UpdateCartItemRequest.java`
7. `cart/dto/CartResponse.java`
8. `cart/CartController.java`

**Endpoints unlocked** (all require `X-Session-Token`):
- `GET /api/v1/cart`
- `POST /api/v1/cart/items`
- `PATCH /api/v1/cart/items/{id}`
- `DELETE /api/v1/cart/items/{id}`
- `DELETE /api/v1/cart`

**End-of-day test**: Start session → add 2 different items → add same item again (quantity should increase) → update quantity of one → check cart total matches (quantity × unit_price).

---

### Day 9 — Orders

**Why now**: Cart exists. Customer is ready to place an order. Kitchen needs to see it.

**Migration first**: `V9__create_orders.sql`

```sql
CREATE TABLE orders (
  id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID          NOT NULL REFERENCES customer_sessions(id),
  restaurant_id   UUID          NOT NULL REFERENCES restaurants(id),
  table_id        UUID          NOT NULL REFERENCES restaurant_tables(id),
  status          order_status  NOT NULL DEFAULT 'PENDING',
  total_amount    NUMERIC(10,2) NOT NULL,
  notes           TEXT,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE TABLE order_items (
  id              UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        UUID              NOT NULL REFERENCES orders(id),
  menu_item_id    UUID              NOT NULL REFERENCES menu_items(id),
  name            VARCHAR(255)      NOT NULL,
  quantity        INT               NOT NULL,
  unit_price      NUMERIC(10,2)     NOT NULL,
  status          order_item_status NOT NULL DEFAULT 'PENDING',
  notes           TEXT,
  created_at      TIMESTAMPTZ       NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ       NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_session ON orders(session_id);
CREATE INDEX idx_orders_restaurant ON orders(restaurant_id);
CREATE INDEX idx_order_items_order ON order_items(order_id);
```

**Files to create**: (package: `com.miniproject.plato.order`)

1. `order/Order.java`
2. `order/OrderItem.java`
3. `order/OrderRepository.java`
4. `order/OrderItemRepository.java`
5. `order/OrderService.java`
6. `order/OrderServiceImpl.java`
7. `order/dto/PlaceOrderRequest.java`
8. `order/dto/OrderResponse.java`
9. `order/dto/OrderItemResponse.java`
10. `order/dto/UpdateOrderStatusRequest.java`
11. `order/OrderMapper.java`
12. `order/OrderController.java`

**Endpoints unlocked**:
- `POST /api/v1/orders` — Customer places order (cart → order, then cart is cleared)
- `GET /api/v1/orders` — Customer sees their session's orders
- `GET /api/v1/restaurants/{rId}/orders` — Kitchen/MANAGER sees all incoming orders
- `PATCH /api/v1/orders/{id}/status` — CHEF updates order status
- `PATCH /api/v1/orders/{id}/items/{itemId}/status` — CHEF marks individual item ready

**State machine**:
```
PENDING → ACCEPTED → PREPARING → READY → SERVED
               ↘ CANCELLED (only before PREPARING)
```

**End-of-day test**: Place order from cart → cart should be empty after → login as CHEF (MANAGER role) → mark order ACCEPTED → PREPARING → READY → SERVED → try marking SERVED order as CANCELLED (expect 400).

---

### Day 10 — Payments

**Why now**: Orders exist. Customer wants to pay and leave.

**Migration first**: `V10__create_payments.sql`

```sql
CREATE TABLE payments (
  id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID           NOT NULL REFERENCES customer_sessions(id),
  restaurant_id   UUID           NOT NULL REFERENCES restaurants(id),
  amount          NUMERIC(10,2)  NOT NULL,
  tax_amount      NUMERIC(10,2)  NOT NULL DEFAULT 0,
  service_charge  NUMERIC(10,2)  NOT NULL DEFAULT 0,
  total_amount    NUMERIC(10,2)  NOT NULL,
  method          payment_method,
  status          payment_status NOT NULL DEFAULT 'PENDING',
  transaction_ref VARCHAR(100),
  created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_session ON payments(session_id);
```

**Files to create**: (package: `com.miniproject.plato.payment`)

1. `payment/Payment.java`
2. `payment/PaymentRepository.java`
3. `payment/PaymentService.java`
4. `payment/PaymentServiceImpl.java`
5. `payment/dto/RequestBillRequest.java`
6. `payment/dto/ProcessPaymentRequest.java`
7. `payment/dto/BillResponse.java`
8. `payment/dto/PaymentResponse.java`
9. `payment/PaymentController.java`

**Endpoints unlocked**:
- `POST /api/v1/payments/request-bill` — Customer requests bill (session auth)
- `GET /api/v1/payments/{sessionId}/bill` — Get full bill breakdown
- `PATCH /api/v1/payments/{id}/complete` — CASHIER marks paid
- `PATCH /api/v1/payments/{id}/refund` — MANAGER issues refund

**Bill formula**:
```
amount         = sum of all order totals in session
tax_amount     = amount × restaurant.taxPercentage / 100
service_charge = amount × restaurant.serviceCharge / 100
total_amount   = amount + tax_amount + service_charge
```

On payment complete: `session.status = CLOSED`. No more orders after that.

**End-of-day test**: Complete full flow from Day 7 → request bill → verify tax calculation is correct based on restaurant settings → mark paid → try to add another cart item (expect session CLOSED error).

---

### Day 11 — Feedback

**Why now**: Session is closed. Customer can now rate.

**Migration first**: `V11__create_feedback.sql`

```sql
CREATE TABLE feedback (
  id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id      UUID        NOT NULL REFERENCES customer_sessions(id),
  restaurant_id   UUID        NOT NULL REFERENCES restaurants(id),
  rating          INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment         TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_feedback_restaurant ON feedback(restaurant_id);
```

**Files to create**: (package: `com.miniproject.plato.feedback`)

1. `feedback/Feedback.java`
2. `feedback/FeedbackRepository.java`
3. `feedback/FeedbackService.java`
4. `feedback/FeedbackServiceImpl.java`
5. `feedback/dto/SubmitFeedbackRequest.java`
6. `feedback/dto/FeedbackResponse.java`
7. `feedback/FeedbackController.java`

**Endpoints unlocked**:
- `POST /api/v1/feedback` — Customer submits (only on CLOSED session)
- `GET /api/v1/restaurants/{rId}/feedback` — OWNER/MANAGER sees all
- `GET /api/v1/restaurants/{rId}/feedback/summary` — avg rating + count

**End-of-day test**: Try submitting feedback on ACTIVE session (expect 400) → close session → submit feedback → hit summary endpoint → verify avg rating is correct.

---

### Day 12 — WebSocket (Real-time Kitchen Updates)

**Why now**: All data exists. Add real-time layer on top.

**Files to create**: (package: `com.miniproject.plato.websocket`)

1. `websocket/WebSocketConfig.java` — STOMP over SockJS, `/ws` endpoint, `/topic` broker
2. `websocket/OrderEventPublisher.java` — wraps `SimpMessagingTemplate`

**Hook into `OrderServiceImpl`**:
- After `placeOrder()` saves → `publisher.notifyNewOrder(restaurantId, orderResponse)`
- After `updateStatus()` saves → `publisher.notifyStatusUpdate(restaurantId, orderId, status)`

**Topics customers and staff subscribe to**:
- `/topic/restaurant/{restaurantId}/orders` — kitchen board
- `/topic/restaurant/{restaurantId}/orders/{orderId}` — customer order tracking

**End-of-day test**: Open two Postman WebSocket connections → place an order → verify kitchen connection receives the event.

---

### Day 13 — Unit Tests

Write service-layer unit tests for every module. This is what you show in interviews.

**Tests to write** (all in `src/test/java/`):

1. `auth/AuthServiceImplTest` — login success, wrong password (401), inactive user (403)
2. `user/UserServiceImplTest` — create user, duplicate email (409), soft delete
3. `restaurant/RestaurantServiceImplTest` — create, ownership violation (403)
4. `order/OrderServiceImplTest` — cart → order conversion, invalid state transition (400)
5. `payment/PaymentServiceImplTest` — bill calculation with 10% tax + 5% service charge

Pattern for every test:
```java
@ExtendWith(MockitoExtension.class)
class SomeServiceImplTest {
    @Mock SomeRepository repo;
    @InjectMocks SomeServiceImpl service;

    @Test
    void should_throwConflict_when_emailAlreadyExists() {
        when(repo.existsByEmail(any())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.createUser(request));
    }
}
```

---

### Day 14 — Final Sweep + Documentation

**Goal**: Portfolio-ready. Run this checklist top to bottom.

1. `./mvnw clean test` — must pass with 0 errors
2. Verify all 11 Flyway migrations ran (`SELECT * FROM flyway_schema_history`)
3. Verify all `${ENV_VAR}` have defaults or are documented
4. Write `README.md` in project root:
   - What the project is (2 sentences)
   - How to run locally (5 steps max)
   - Tech stack (bullet list)
   - All endpoints grouped by module
   - Environment variables table
5. Export Postman collection with all ~45 endpoints
6. Git commit with message: `feat: complete Plato backend v1.0`

---

## Complete File Count When Done

| Module | Files to write |
|--------|----------------|
| User (complete) | 3 DTOs + Mapper + Controller = **5** |
| Restaurant (complete) | 4 DTOs + Mapper + Service + ServiceImpl + Controller = **8** |
| Tables | Entity + Repo + QrService + Service + Impl + 3 DTOs + Mapper + Controller = **10** |
| Employees | Entity + Enum + Repo + Service + Impl + 3 DTOs + Mapper + Controller = **10** |
| Menu | 2 Entities + 2 Repos + Service + Impl + 6 DTOs + Mapper + Controller = **14** |
| Customer Sessions | Entity + Repo + Service + Impl + Filter + 2 DTOs + Controller = **8** |
| Cart | Entity + Repo + Service + Impl + 3 DTOs + Controller = **7** |
| Orders | 2 Entities + 2 Repos + Service + Impl + 4 DTOs + Mapper + Controller = **12** |
| Payments | Entity + Repo + Service + Impl + 4 DTOs + Controller = **8** |
| Feedback | Entity + Repo + Service + Impl + 2 DTOs + Controller = **7** |
| WebSocket | Config + Publisher = **2** |
| Tests | 5 test files = **5** |
| Migrations | V4 through V11 = **8** |
| **Total new files** | | **~104** |

---

## One-Line Summary Per Day

| Day | What you build | Status |
|-----|---------------|--------|
| 1 | User DTOs + Mapper + Controller | ✅ **Done & tested** |
| 2 | Restaurant Service + DTOs + Controller | ✅ **Done & tested** |
| 3 | Tables entity + QR service + Controller + V4 migration | ✅ **Done & tested (curl verified)** |
| 4 | Employees entity + Controller + V5 migration | 🔄 **In progress** |
| 5 | Menu categories + items + public endpoint + V6 migration | 🔲 Todo |
| 6 | Integration test — staff side works end to end | 🔲 Todo |
| 7 | Session start + CustomerSessionFilter + V7 migration | 🔲 Todo |
| 8 | Cart CRUD scoped to session + V8 migration | 🔲 Todo |
| 9 | Orders + kitchen view + state machine + V9 migration | 🔲 Todo |
| 10 | Payments + bill calc + session close + V10 migration | 🔲 Todo |
| 11 | Feedback + summary endpoint + V11 migration | 🔲 Todo |
| 12 | WebSocket config + OrderEventPublisher | 🔲 Todo |
| 13 | Unit tests for Auth, User, Restaurant, Order, Payment | 🔲 Todo |
| 14 | README + Postman export + final compile check | 🔲 Todo |
