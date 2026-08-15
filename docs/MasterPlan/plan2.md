# Plato — Completion Plan (2 Weeks)

> **Purpose**: Honest, file-scanned status of what is built and what needs to be built — module by module, day by day.
> **Goal**: A complete, interview-demonstrable backend in 2 weeks.
> **Learning mode**: One module at a time. No skipping. Every piece understood before moving on.

---

## How to read this document

- ✅ **Done** — file exists, compiled, tested
- ⚠️ **Partial** — some files exist, incomplete
- 🔲 **Not started** — nothing built yet

---

## What Has Been Built (Verified by File Scan — 2026-08-15)

### Infrastructure & Foundation

| Component | Status | Notes |
|-----------|--------|-------|
| `PlatoApplication.java` | ✅ Done | `@EnableJpaAuditing` present |
| `BaseEntity.java` | ✅ Done | UUID id, createdAt, updatedAt via JPA Auditing |
| `ApiResponse<T>` | ✅ Done | Universal response wrapper |
| `PagedResponse.java` | ✅ Done | Pagination wrapper |
| `application.yml` + `application-local.yml` | ✅ Done | HikariCP, Flyway, JWT config |
| `AppConfig.java` | ✅ Done | BCryptPasswordEncoder, AuthenticationManager |
| `SecurityConfig.java` | ✅ Done | JWT filter chain, CORS, path rules |

### Exception Layer — All ✅ Done

`PlatoException`, `ResourceNotFoundException`, `ConflictException`, `UnauthorizedAccessException`, `ValidationException`, `SessionExpiredException`, `GlobalExceptionHandler`

### Security Layer — All ✅ Done

`JwtTokenProvider`, `JwtAuthenticationFilter`, `UserDetailsServiceImpl`, `SecurityProperties`

### Database Migrations

| Migration | Status | Creates |
|-----------|--------|---------|
| `V1__create_enums.sql` | ✅ Done | All 9 PostgreSQL custom enum types |
| `V2__create_users.sql` | ✅ Done | `users` table |
| `V3__create_restaurants.sql` | ✅ Done | `restaurants` table |

### User Module

| Component | Status | Notes |
|-----------|--------|-------|
| `User.java`, `UserRole.java`, `UserStatus.java` | ✅ Done | `@JdbcTypeCode` enum fix applied |
| `UserRepository.java` | ✅ Done | |
| `UserService.java` + `UserServiceImpl.java` | ✅ Done | Internal methods only — no API methods |
| `DataInitializer.java` | ✅ Done | Seeds Super Admin on first boot |
| `user/dto/` (UserResponse, CreateUserRequest, UpdateUserRequest) | ⚠️ Missing | Not created |
| `UserMapper.java` | ⚠️ Missing | Not created |
| `UserController.java` | ⚠️ Missing | `/api/v1/users` not exposed |

### Auth Module — All ✅ Done

`AuthController`, `AuthService`, `AuthServiceImpl`, `LoginRequest`, `LoginResponse`

### Restaurant Module

| Component | Status |
|-----------|--------|
| `Restaurant.java`, `RestaurantStatus.java`, `RestaurantRepository.java` | ✅ Done |
| `restaurant/dto/` (4 DTOs) | ⚠️ Missing |
| `RestaurantMapper`, `RestaurantService`, `RestaurantServiceImpl`, `RestaurantController` | ⚠️ Missing |

---

## 2-Week Build Plan

---

# WEEK 1 — Complete Existing Modules + Tables + Employees + Menu

---

## Day 1 — Finish User Module

**Goal**: `GET/POST/PATCH/DELETE /api/v1/users` fully working.

### Create these files:

**`user/dto/UserResponse.java`** — outbound DTO (no passwordHash)
Fields: `id`, `fullName`, `email`, `phone`, `role`, `status`, `createdAt`, `updatedAt`

**`user/dto/CreateUserRequest.java`** — validated inbound DTO
Rules: `@NotBlank` on name/email/password, `@Email`, `@Size(min=8)`, `@NotNull` on role.

**`user/dto/UpdateUserRequest.java`** — PATCH, all fields nullable (null = don't change)
Fields: `fullName`, `email`, `phone` only — password and role are separate flows.

**`UserMapper.java`** — `@Component`, `toResponse(User)` method only.

**`UserService.java`** — add to interface:
- `Page<UserResponse> getAllUsers(Pageable)`
- `UserResponse getUserById(UUID)`
- `UserResponse createUser(CreateUserRequest)`
- `UserResponse updateUser(UUID, UpdateUserRequest)`
- `void deleteUser(UUID)`
- `UserResponse updateStatus(UUID, UserStatus)`

**`UserServiceImpl.java`** — implement the 6 new methods.

**`UserController.java`**:

| Method | Path | Who | Returns |
|--------|------|-----|---------|
| GET | `/api/v1/users` | SUPER_ADMIN | `Page<UserResponse>` |
| GET | `/api/v1/users/{id}` | SUPER_ADMIN or self | `UserResponse` |
| POST | `/api/v1/users` | SUPER_ADMIN | `201 UserResponse` |
| PATCH | `/api/v1/users/{id}` | SUPER_ADMIN or self | `UserResponse` |
| PATCH | `/api/v1/users/{id}/status` | SUPER_ADMIN | `UserResponse` |
| DELETE | `/api/v1/users/{id}` | SUPER_ADMIN | `204 No Content` |

**Concepts learned**: DTOs, Mapper pattern, `@PreAuthorize`, `@PageableDefault`, HTTP 200/201/204, soft delete, dirty checking.

---

## Day 2 — Finish Restaurant Module

**Goal**: `GET/POST/PUT/PATCH /api/v1/restaurants` fully working.

### Create these files:

**`restaurant/dto/CreateRestaurantRequest.java`** — only `name` required.

**`restaurant/dto/UpdateRestaurantRequest.java`** — all nullable: name, description, logoUrl, phone, email, address, city, state, country, zipcode, timezone, openingTime, closingTime.

**`restaurant/dto/RestaurantSettingsRequest.java`** — settings-only DTO: taxPercentage, serviceCharge, allowCashPayment, allowCardPayment, allowUpi, allowOnlinePayment, acceptingOrders, autoAcceptOrders.

**`restaurant/dto/RestaurantResponse.java`** — full outbound DTO, all fields including settings.

**`RestaurantMapper.java`**, **`RestaurantService.java`**, **`RestaurantServiceImpl.java`**, **`RestaurantController.java`**

| Method | Path | Who |
|--------|------|-----|
| POST | `/api/v1/restaurants` | OWNER |
| GET | `/api/v1/restaurants` | OWNER (own) / SUPER_ADMIN (all) |
| GET | `/api/v1/restaurants/{id}` | OWNER (own) / SUPER_ADMIN |
| PUT | `/api/v1/restaurants/{id}` | OWNER / SUPER_ADMIN |
| PATCH | `/api/v1/restaurants/{id}/status` | SUPER_ADMIN only |
| GET | `/api/v1/restaurants/{id}/settings` | OWNER / SUPER_ADMIN |
| PUT | `/api/v1/restaurants/{id}/settings` | OWNER / SUPER_ADMIN |

**Concepts learned**: Tenant isolation (ownership check in service), `Authentication` passed to service, embedded settings.

---

## Day 3 — Tables & QR Code Module

**Goal**: Owners add tables, each gets a unique QR token.

### Migration `V4__create_restaurant_tables.sql`:
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

### Package `com.miniproject.plato.table/`:
`RestaurantTable.java`, `TableRepository.java`, `QrTokenService.java`, `TableService.java`, `TableServiceImpl.java`, DTOs (`CreateTableRequest`, `UpdateTableRequest`, `TableResponse`), `TableMapper.java`, `TableController.java`

| Method | Path | Who |
|--------|------|-----|
| POST | `/restaurants/{rId}/tables` | OWNER |
| GET | `/restaurants/{rId}/tables` | OWNER / SUPER_ADMIN |
| GET | `/restaurants/{rId}/tables/{id}` | OWNER / SUPER_ADMIN |
| PUT | `/restaurants/{rId}/tables/{id}` | OWNER |
| DELETE | `/restaurants/{rId}/tables/{id}` | OWNER (soft delete) |
| POST | `/restaurants/{rId}/tables/{id}/qr/regenerate` | OWNER |

QR URL: `https://plato.app/qr/{qr_token}` — base URL in `application.yml`.

**Concepts learned**: Nested REST resources, `SecureRandom`, service-to-service ownership validation.

---

## Day 4 — Employee Module

**Goal**: Owners assign employees (MANAGER, CHEF, WAITER, CASHIER) to their restaurant.

### Migration `V5__create_employees.sql`:
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

### Package `com.miniproject.plato.employee/`:
`Employee.java`, `EmployeeRole.java`, `EmployeeRepository.java`, `EmployeeService.java`, `EmployeeServiceImpl.java`, DTOs (`AssignEmployeeRequest`, `UpdateEmployeeRoleRequest`, `EmployeeResponse`), `EmployeeMapper.java`, `EmployeeController.java`

| Method | Path | Who |
|--------|------|-----|
| POST | `/restaurants/{rId}/employees` | OWNER |
| GET | `/restaurants/{rId}/employees` | OWNER / MANAGER |
| PATCH | `/restaurants/{rId}/employees/{id}/role` | OWNER |
| DELETE | `/restaurants/{rId}/employees/{id}` | OWNER (deactivate) |

**Concepts learned**: Junction table pattern, platform role vs restaurant role, soft deactivation with `is_active`.

---

## Day 5 — Menu Module

**Goal**: Owners create categories and items. Customers browse via public endpoint.

### Migration `V6__create_menu.sql`:
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

### Package `com.miniproject.plato.menu/`:
`MenuCategory.java`, `MenuItem.java`, repositories, `MenuService.java`, `MenuServiceImpl.java`, DTOs (`CreateCategoryRequest`, `CreateMenuItemRequest`, `UpdateMenuItemRequest`, `MenuItemResponse`, `MenuCategoryResponse`, `FullMenuResponse`), `MenuMapper.java`, `MenuController.java`

| Method | Path | Who |
|--------|------|-----|
| POST | `/restaurants/{rId}/menu/categories` | OWNER |
| GET | `/restaurants/{rId}/menu` | PUBLIC — no auth |
| POST | `/restaurants/{rId}/menu/items` | OWNER |
| PATCH | `/restaurants/{rId}/menu/items/{id}` | OWNER |
| PATCH | `/restaurants/{rId}/menu/items/{id}/availability` | OWNER / MANAGER |
| DELETE | `/restaurants/{rId}/menu/items/{id}` | OWNER |

**Concepts learned**: Public vs authenticated in same controller, `BigDecimal` for money (never `double`), nested JSON response, `display_order`.

---

# WEEK 2 — Customer Flow + Orders + Payments + Hardening

---

## Day 6 — Customer Sessions

**Goal**: Customer scans QR → session created → can browse and order.

### Migration `V7__create_customer_sessions.sql`:
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

### Package `com.miniproject.plato.session/`:
`CustomerSession.java`, `CustomerSessionRepository.java`, `CustomerSessionService.java`, `CustomerSessionServiceImpl.java`, **`CustomerSessionFilter.java`** (reads `X-Session-Token` header), DTOs (`StartSessionRequest`, `SessionResponse`), `SessionController.java`
Update `SecurityConfig.java` to add `CustomerSessionFilter`.

| Method | Path | Who |
|--------|------|-----|
| POST | `/api/v1/sessions/start` | PUBLIC — takes QR token |
| GET | `/api/v1/restaurants/{rId}/sessions` | OWNER / MANAGER |
| POST | `/api/v1/sessions/{id}/close` | STAFF |

**Flow**: QR scan → POST start → system finds table → creates session → returns `sessionToken` → customer sends `X-Session-Token: <token>` on all future requests.

**Concepts learned**: Second auth system (no JWT for customers), custom security filter, TTL expiry, `X-Session-Token` header.

---

## Day 7 — Cart

**Goal**: Customers add items to cart within their session.

### Migration `V8__create_cart_items.sql`:
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

### Package `com.miniproject.plato.cart/`:
`CartItem.java`, `CartItemRepository.java`, `CartService.java`, `CartServiceImpl.java`, DTOs (`AddToCartRequest`, `UpdateCartItemRequest`, `CartResponse`), `CartController.java`

| Method | Path | Who |
|--------|------|-----|
| GET | `/api/v1/cart` | Customer (session) |
| POST | `/api/v1/cart/items` | Customer (session) |
| PATCH | `/api/v1/cart/items/{id}` | Customer (session) |
| DELETE | `/api/v1/cart/items/{id}` | Customer (session) |
| DELETE | `/api/v1/cart` | Customer — clear all |

**Concepts learned**: Price snapshot at add-time (`unit_price` from menu item — not recalculated later), upsert (add same item = increase quantity), total computed in service.

---

## Day 8 — Orders

**Goal**: Customer converts cart to an order. Kitchen updates status.

### Migration `V9__create_orders.sql`:
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

### Package `com.miniproject.plato.order/`:
`Order.java`, `OrderItem.java`, repositories, `OrderService.java`, `OrderServiceImpl.java`, DTOs (`PlaceOrderRequest`, `OrderResponse`, `OrderItemResponse`, `UpdateOrderStatusRequest`), `OrderController.java`

| Method | Path | Who |
|--------|------|-----|
| POST | `/api/v1/orders` | Customer — cart to order |
| GET | `/api/v1/orders` | Customer — own session orders |
| GET | `/api/v1/restaurants/{rId}/orders` | CHEF / MANAGER |
| PATCH | `/api/v1/orders/{id}/status` | CHEF / MANAGER |
| PATCH | `/api/v1/orders/{id}/items/{itemId}/status` | CHEF |

**State machine**: `PENDING → ACCEPTED → PREPARING → READY → SERVED` (CANCELLED only before PREPARING)

**Concepts learned**: `@Transactional` atomicity (cart clear + order create), state machine enforcement, item-level vs order-level status tracking.

---

## Day 9 — Payments

**Goal**: Customer requests bill. Cashier confirms payment. Session closes.

### Migration `V10__create_payments.sql`:
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

### Package `com.miniproject.plato.payment/`:
`Payment.java`, `PaymentRepository.java`, `PaymentService.java`, `PaymentServiceImpl.java`, DTOs (`RequestBillRequest`, `ProcessPaymentRequest`, `BillResponse`, `PaymentResponse`), `PaymentController.java`

| Method | Path | Who |
|--------|------|-----|
| POST | `/api/v1/payments/request-bill` | Customer (session) |
| GET | `/api/v1/payments/{sessionId}/bill` | Customer / CASHIER |
| PATCH | `/api/v1/payments/{id}/complete` | CASHIER |
| PATCH | `/api/v1/payments/{id}/refund` | MANAGER |

**Bill formula**: `amount = sum(orders)`, `tax = amount × taxPercentage/100`, `service = amount × serviceCharge/100`, `total = amount + tax + service`.
On complete: `customer_session.status = CLOSED`.

**Concepts learned**: Aggregation across orders, restaurant settings applied at payment (not order) time, session close flow.

---

## Day 10 — Feedback

**Goal**: After session closes, customer rates the experience.

### Migration `V11__create_feedback.sql`:
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

### Package `com.miniproject.plato.feedback/`:
`Feedback.java`, `FeedbackRepository.java`, `FeedbackService.java`, `FeedbackServiceImpl.java`, DTOs (`SubmitFeedbackRequest`, `FeedbackResponse`), `FeedbackController.java`

| Method | Path | Who |
|--------|------|-----|
| POST | `/api/v1/feedback` | Customer — CLOSED session only |
| GET | `/api/v1/restaurants/{rId}/feedback` | OWNER / MANAGER |
| GET | `/api/v1/restaurants/{rId}/feedback/summary` | OWNER (avg + count) |

**Concepts learned**: Business rule enforcement in service (reject if session not CLOSED), `@Min`/`@Max` validation, aggregate `@Query`.

---

## Day 11 — WebSocket (Real-time Kitchen Updates)

**Goal**: New orders appear on kitchen screen instantly. No polling.

### Package `com.miniproject.plato.websocket/`:

1. `WebSocketConfig.java` — STOMP over SockJS, endpoint `/ws`, broker at `/topic`
2. `OrderEventPublisher.java` — wraps `SimpMessagingTemplate`, called from `OrderServiceImpl`

**Topics**:
- `/topic/restaurant/{restaurantId}/orders` — new order → kitchen sees it
- `/topic/restaurant/{restaurantId}/orders/{orderId}` — status change → customer sees it

**Hooks into `OrderServiceImpl`**:
- After `placeOrder()` → `publisher.notifyNewOrder(restaurantId, orderResponse)`
- After `updateStatus()` → `publisher.notifyStatusUpdate(restaurantId, orderId, status)`

**Concepts learned**: STOMP, `SimpMessagingTemplate`, push vs pull, decoupling event publishing from business logic.

---

## Day 12 — Security Hardening + Polish

1. Rate limiting on `POST /api/v1/auth/login`
2. Input sanitization for text fields (description, notes, comment)
3. Verify `passwordHash` never appears in any log
4. Lock CORS to production origin (no wildcard)
5. Swagger UI — add `@Operation`, `@ApiResponse` to all controllers; add JWT Bearer scheme
6. `GET /api/v1/health` endpoint — `{ status: "UP", version: "1.0.0" }`

---

## Day 13 — Unit Testing

Write service-layer unit tests using JUnit 5 + Mockito:

1. `AuthServiceImplTest` — login success, wrong password (401), inactive account (403)
2. `RestaurantServiceImplTest` — create, ownership violation (403), update
3. `OrderServiceImplTest` — cart-to-order, state machine (can't cancel SERVED)
4. `PaymentServiceImplTest` — bill calculation with tax + service charge
5. `CustomerSessionServiceImplTest` — session start, expiry, double-submit guard

Pattern: `@ExtendWith(MockitoExtension.class)`, `@Mock` repositories, `assertThrows` with type and message.

---

## Day 14 — Final Review + Documentation

1. `README.md` — what it is, how to run locally, env vars, API overview, tech stack
2. Postman collection — all ~45 endpoints exported with sample bodies
3. `./mvnw clean test` — 0 compile errors, 0 test failures
4. Document every `${ENV_VAR}` needed in production
5. Git hygiene — `.gitignore` must cover `application-local.yml`, `target/`, `.idea/`

---

## Complete Feature Checklist

### Endpoints (~45 total)

| Module | Endpoints | Status |
|--------|-----------|--------|
| Auth | 1 | ✅ Done |
| Users | 6 | ⚠️ Controller missing |
| Restaurants | 7 | ⚠️ Service + Controller missing |
| Tables + QR | 6 | 🔲 Not started |
| Employees | 4 | 🔲 Not started |
| Menu | 6 | 🔲 Not started |
| Customer Sessions | 3 | 🔲 Not started |
| Cart | 5 | 🔲 Not started |
| Orders | 5 | 🔲 Not started |
| Payments | 4 | 🔲 Not started |
| Feedback | 3 | 🔲 Not started |
| WebSocket topics | 2 | 🔲 Not started |
| Health | 1 | 🔲 Not started |

### Database Migrations

| Migration | Status | Needed |
|-----------|--------|--------|
| V1 enums | ✅ Done | — |
| V2 users | ✅ Done | — |
| V3 restaurants | ✅ Done | — |
| V4 restaurant_tables | 🔲 | Day 3 |
| V5 employees | 🔲 | Day 4 |
| V6 menu | 🔲 | Day 5 |
| V7 customer_sessions | 🔲 | Day 6 |
| V8 cart_items | 🔲 | Day 7 |
| V9 orders + order_items | 🔲 | Day 8 |
| V10 payments | 🔲 | Day 9 |
| V11 feedback | 🔲 | Day 10 |

---

## Interview Talking Points (earned per module)

| Module | What you explain |
|--------|-----------------|
| Foundation | Filter chain, JWT stateless auth, Flyway vs Hibernate DDL |
| Users | RBAC with `@PreAuthorize`, soft delete, BCrypt, partial update |
| Restaurants | Tenant isolation, embedded settings, ownership enforcement |
| Tables | Nested REST, `SecureRandom` token generation |
| Employees | Junction table, platform role vs restaurant role |
| Menu | Public + auth endpoints in same controller, `BigDecimal` for money |
| Sessions | Custom security filter, `X-Session-Token`, TTL expiry, dual auth |
| Cart | Price snapshot, upsert pattern, computed total |
| Orders | `@Transactional` atomicity, state machine, item-level status |
| Payments | Bill aggregation, applying settings at pay-time, session close |
| WebSocket | STOMP, `SimpMessagingTemplate`, push vs pull, event decoupling |
| Testing | Mockito, `@ExtendWith`, `assertThrows` with message verification |

---

## Key Design Decisions (Memorize These for Interviews)

1. **Customers are not Users** — no login, no JWT. Two auth systems in one app: JWT for staff, `X-Session-Token` for customers.
2. **Settings embedded in restaurant row** — no JOIN cost on every request. Settings are always needed with the restaurant.
3. **Price snapshot at cart time** — `unit_price` stored when added to cart. Menu price changes don't affect in-progress carts or orders.
4. **Soft delete everywhere** — `status = DELETED` or `is_active = false`. No physical DELETE. FK references stay intact for audit trail.
5. **`@JdbcTypeCode(SqlTypes.NAMED_ENUM)`** — Hibernate 6 fix for PostgreSQL custom enum binding (VARCHAR vs named type).
6. **Flyway owns the schema** — `ddl-auto: validate`. Hibernate validates but never modifies tables. All changes are version-controlled SQL.
7. **Interface + Impl pattern** — controllers depend on interfaces, never implementations. Easy to mock in tests. Easy to swap implementation.
8. **`@Transactional(readOnly = true)` class + `@Transactional` method** — explicit intent, reduces DB lock contention on read-heavy service.
