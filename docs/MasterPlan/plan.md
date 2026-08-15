# Plato — 4-Week Backend Development & Deployment Plan

> **Stack**: Java 21 · Spring Boot 3.5.5 · Spring Security 6 · JPA/Hibernate · PostgreSQL · Flyway · JWT · Redis · WebSocket (STOMP) · Maven  
> **Goal**: Production-ready, deployed backend from scratch in 28 days.

---

## Overview

| Phase | Week | Focus |
|-------|------|-------|
| **Phase 1** | Week 1 | Project foundation, security, authentication |
| **Phase 2** | Week 2 | Core restaurant management modules |
| **Phase 3** | Week 3 | Customer flow — sessions, cart, orders, payments, feedback |
| **Phase 4** | Week 4 | WebSockets, analytics, testing, hardening, deployment |

---

## Database Hierarchy

This is the canonical structure. Every design and migration decision flows from this.

```
Platform
│
├── Users (Authentication)
│         │
│         ├── SUPER_ADMIN
│         ├── OWNER
│         └── EMPLOYEE
│
└── Restaurants
          │
          ├── Tables
          ├── Employees
          ├── Categories
          ├── Menu Items
          ├── Sessions
          ├── Orders
          └── Feedback
```

> **Implementation sub-tables** (not shown above but required): `cart_items` (under Sessions), `order_items` (under Orders), `payments` (under Sessions).

> **Auth rule**: Only platform users (Super Admin, Owner, Employee) authenticate with email + password → JWT. Customers never log in — they use a temporary `X-Session-Token` header.

---

## Definition of Done (applies to every feature)

Before marking any module complete:

- [ ] Entity + Flyway migration written  
- [ ] Repository defined  
- [ ] Service interface + implementation written  
- [ ] DTOs (Request + Response) created  
- [ ] Mapper written (entity ↔ DTO)  
- [ ] Bean Validation on all request DTOs  
- [ ] Controller written with correct HTTP methods and status codes  
- [ ] Role-based authorization enforced  
- [ ] Custom exceptions thrown, handled in `@RestControllerAdvice`  
- [ ] SLF4J logging added for key actions  
- [ ] Unit tests for service layer  
- [ ] Swagger/OpenAPI annotations added  
- [ ] Code compiles and all tests pass  

---

---

# WEEK 1 — Foundation, Infrastructure & Authentication

**Goal**: A running Spring Boot app with a real database, working auth, and all shared infrastructure in place. Every future module depends on this week.

---

## Day 1 — Project Setup & Infrastructure

### Tasks

1. ✅ **Fix `PlatoApplication.java`** — update package to `com.miniproject.plato`. Added `@EnableJpaAuditing` for auto-timestamps.

2. ✅ **Set up `application.yml`** (replaced `application.properties`)
   - `ddl-auto: validate` — Flyway owns the schema
   - `open-in-view: false` — no DB connections held across HTTP layer
   - HikariCP connection pool configured
   - All secrets use `${ENV_VAR}` with **no default values** in the committed file
   - Local dev overrides live in `application-local.yml` (git-ignored)
   - Custom `plato.jwt.*`, `plato.session.*`, `plato.qr.*`, `plato.redis.*` properties

3. **Redis infrastructure setup** (added Day 6 — required before Day 11 customer sessions)
   - Add `spring-boot-starter-data-redis` and `spring-boot-starter-cache` to `pom.xml`
   - Configure Redis in `application.yml` (`spring.data.redis.*`, `spring.cache.*`)
   - Add local Redis config in `application-local.yml`
   - Create `config/CacheConfig.java` — per-cache TTLs, JSON serialization via Jackson
   - Cache names defined: `restaurants`, `restaurantsByOwner`, `menuItems` (Day 9), `customerSession` (Day 11)

3. ✅ **Create `common` package — shared infrastructure**
   - `ApiResponse<T>` — universal response wrapper (`@JsonInclude(NON_NULL)` keeps error responses lean)
   - `PagedResponse<T>` — paginated response (wraps Spring's `Page<T>`, exposes only stable fields)
   - `BaseEntity` — abstract class with `id` (UUID), `createdAt`, `updatedAt`; uses `@MappedSuperclass`
   - No custom `AuditListener` needed — Spring Data's built-in `AuditingEntityListener` handles timestamps via `@EnableJpaAuditing` on `PlatoApplication`

4. ✅ **Create `exception` package**
   - `PlatoException` (base — carries `HttpStatus` so one handler covers all)
   - `ResourceNotFoundException` → 404
   - `UnauthorizedAccessException` → 403
   - `ConflictException` → 409
   - `ValidationException` → 400
   - `SessionExpiredException` → 401
   - `GlobalExceptionHandler` (`@RestControllerAdvice`) — central handler, no try-catch needed in controllers

5. ✅ **Set up Flyway migration directory**
   ```
   src/main/resources/db/migration/
   ```

6. ✅ **Create first Flyway migration** — `V1__create_enums.sql`
   - Enums created: `user_role`, `user_status`, `restaurant_status`, `employee_role`, `session_status`, `order_status`, `order_item_status`, `payment_method`, `payment_status`
   - Note: `cart_status` removed — no separate `carts` table; cart items are rows in `cart_items` keyed by session
   - `V2__create_users.sql` also created (moved here from Day 2 Task 1 since it logically follows the enums)

7. **Verify app starts** — `./mvnw spring-boot:run`

---

## Day 2 — Users & Database Schema

### Tasks

1. **Flyway migration `V2__create_users.sql`**
   ```sql
   CREATE TABLE users (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     full_name VARCHAR(100) NOT NULL,
     email VARCHAR(255) NOT NULL UNIQUE,
     phone VARCHAR(20),
     password_hash VARCHAR(255) NOT NULL,
     role user_role NOT NULL,
     status user_status NOT NULL DEFAULT 'ACTIVE',
     last_login TIMESTAMPTZ,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_users_email ON users(email);
   ```
   > `password_hash` stores BCrypt hash only. Customers do **not** have rows here — only Super Admin, Owners, and Employees.

2. **`user` module**
   - `User` entity (maps to `users` table)
   - `UserRole` enum: `SUPER_ADMIN`, `OWNER`, `EMPLOYEE`
   - `UserStatus` enum: `ACTIVE`, `SUSPENDED`, `DELETED`
   - `UserRepository` (JPA)
   - `UserService` interface + `UserServiceImpl`

3. **Seed Super Admin** — `DataInitializer` (`@Component` + `CommandLineRunner`) creates a default super admin on first run if none exists.

---

## Day 3 — Spring Security & JWT

### Tasks

1. **`security` package**
   - `SecurityConfig` (`@Configuration`) — define filter chain, CORS, CSRF off, stateless sessions
   - `JwtTokenProvider` — generate, validate, parse JWT (sign with HS256 + secret)
   - `JwtAuthenticationFilter` — `OncePerRequestFilter` — reads `Authorization: Bearer <token>` header on every request
   - `UserDetailsServiceImpl` — loads user by email from the database
   - `SecurityProperties` — `@ConfigurationProperties` for `plato.jwt.secret` and `plato.jwt.expiration`

2. **JWT configuration in `application.yml`**
   ```yaml
   plato:
     jwt:
       secret: ${JWT_SECRET}
       expiration: 86400000    # 24 hours — single access token, no refresh needed
     session:
       timeout-minutes: 30     # customer session inactivity timeout
   ```

3. **`auth` module** — for platform users only (Super Admin, Owner, Employee)
   - `LoginRequest` DTO — `@NotBlank email`, `@NotBlank password`
   - `LoginResponse` DTO — `token` (JWT), `role`, `fullName`, `restaurantId` (for employees)
   - `AuthService` interface + `AuthServiceImpl`
     - `login(email, password)` — verify password with BCrypt → generate 24h JWT → update `last_login` → return token
     - `logout()` — stateless; client deletes the token (no server-side revocation needed)
   - `AuthController` (`/api/v1/auth`)
     - `POST /api/v1/auth/login`
     - `POST /api/v1/auth/logout` _(optional — just a client-side clear)_

   > **Customers never use this**. They hit `/api/v1/qr/{token}` instead.

4. **Security path rules**
   ```
   /api/v1/auth/**        → permitAll  (login)
   /api/v1/qr/**          → permitAll  (customer QR scan — no login)
   /api/v1/customer/**    → CustomerSessionFilter (X-Session-Token header, not JWT)
   /api/v1/**             → JWT required (staff + owners)
   /swagger-ui/**         → permitAll  (dev only — lock down in prod)
   ```

5. **Test login end-to-end** via Swagger or Postman — get a JWT, use it on a protected endpoint.

---

## Day 4 — Global Exception Handler & Response Standards

### Tasks

1. **Complete `GlobalExceptionHandler`**
   - Handle `PlatoException` subclasses
   - Handle `MethodArgumentNotValidException` (validation errors)
   - Handle `AccessDeniedException`
   - Handle `AuthenticationException`
   - Handle `DataIntegrityViolationException`
   - Handle generic `Exception`
   - All responses use `ApiResponse<T>`

2. **`ApiResponse<T>` finalized**
   ```java
   public record ApiResponse<T>(
       boolean success,
       String message,
       T data,
       List<String> errors
   ) {}
   ```

3. **Swagger/OpenAPI configuration**
   - `SwaggerConfig` — add JWT bearer auth scheme
   - Global security requirement on all endpoints

4. **Logging configuration** (`logback-spring.xml` or `application.yml`)
   - Different log levels per environment (dev vs prod)
   - Never log passwords, tokens, or sensitive fields

---

## Day 5 — User Management Module

### Tasks

1. **`user` module — complete**
   - `UserResponse` DTO
   - `CreateUserRequest` DTO (for Super Admin creating owners)
   - `UpdateUserRequest` DTO
   - `UserMapper`
   - `UserController` (`/api/v1/users`)
     - `GET /users` — Super Admin only, paginated
     - `GET /users/{id}` — Super Admin or self
     - `POST /users` — Super Admin creates owner
     - `PATCH /users/{id}/status` — Super Admin suspends/activates
     - `DELETE /users/{id}` — soft delete (set status = DELETED)

2. **Role-based authorization** via `@PreAuthorize`
   - Enable with `@EnableMethodSecurity` in `SecurityConfig`

3. **Week 1 integration test** — verify auth flow, user CRUD, JWT validation

---

---

# WEEK 2 — Restaurant Management Modules

**Goal**: Full restaurant setup workflow — restaurants, settings, tables, QR codes, employees, menu.

---

## Day 6 — Restaurant Module

### Tasks

1. **Flyway `V4__create_restaurants.sql`**
   ```sql
   CREATE TABLE restaurants (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     owner_id UUID NOT NULL REFERENCES users(id),
     name VARCHAR(255) NOT NULL,
     description TEXT,
     logo_url TEXT,
     phone VARCHAR(20),
     email VARCHAR(255),
     address TEXT,
     city VARCHAR(100),
     state VARCHAR(100),
     country VARCHAR(100),
     zipcode VARCHAR(20),
     timezone VARCHAR(50),
     opening_time TIME,
     closing_time TIME,
     status restaurant_status NOT NULL DEFAULT 'ACTIVE',
     -- Settings (embedded — no separate table)
     tax_percentage NUMERIC(5,2) NOT NULL DEFAULT 0,
     service_charge NUMERIC(5,2) NOT NULL DEFAULT 0,
     allow_cash_payment BOOLEAN NOT NULL DEFAULT true,
     allow_card_payment BOOLEAN NOT NULL DEFAULT true,
     allow_upi BOOLEAN NOT NULL DEFAULT true,
     allow_online_payment BOOLEAN NOT NULL DEFAULT false,
     accepting_orders BOOLEAN NOT NULL DEFAULT true,
     auto_accept_orders BOOLEAN NOT NULL DEFAULT false,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_restaurants_owner_id ON restaurants(owner_id);
   ```

   > Settings are embedded directly in `restaurants` — no separate `restaurant_settings` table. This matches the database hierarchy.

2. **`restaurant` module**
   - `Restaurant` entity (settings columns are embedded directly in the `restaurants` table)
   - `RestaurantStatus` enum
   - `RestaurantRepository`
   - `RestaurantService` + `RestaurantServiceImpl`
   - DTOs: `CreateRestaurantRequest`, `UpdateRestaurantRequest`, `RestaurantResponse`, `RestaurantSettingsRequest`
   - `RestaurantMapper`
   - `RestaurantController` (`/api/v1/restaurants`)
     - `POST /restaurants` — Owner creates restaurant
     - `GET /restaurants` — Owner sees own restaurants; Super Admin sees all
     - `GET /restaurants/{id}`
     - `PUT /restaurants/{id}`
     - `PATCH /restaurants/{id}/status` — Super Admin only
     - `GET /restaurants/{id}/settings` — returns settings fields from the restaurant row
     - `PUT /restaurants/{id}/settings` — updates settings fields on the restaurant row

3. **Tenant isolation** — every service method validates `restaurant.owner_id == currentUser.id` (unless Super Admin).

4. **Redis caching — infrastructure setup** (done here; used progressively from Day 9 onward)
   - `pom.xml`: add `spring-boot-starter-data-redis`, `spring-boot-starter-cache`
   - `application.yml`: add `spring.data.redis.*` and `spring.cache.*` blocks
   - `application-local.yml`: Redis localhost defaults
   - `config/CacheConfig.java`: `RedisCacheManager` with JSON serializer, per-cache TTLs
   - `RestaurantServiceImpl`: add `@Cacheable` on `getById`, `@CacheEvict` on write methods
   - **Cache map**:
     | Cache name | Stores | TTL |
     |---|---|---|
     | `restaurants` | Single restaurant by ID | 10 min |
     | `restaurantsByOwner` | Owner's restaurant list | 5 min |
     | `menuItems` | Full menu per restaurant (Day 9) | 15 min |
     | `customerSession` | Active customer session token → data (Day 11) | 30 min sliding |

---

## Day 7 — Tables & QR Code Module

### Tasks

1. **Flyway `V6__create_restaurant_tables.sql`**
   ```sql
   CREATE TABLE restaurant_tables (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     table_number VARCHAR(20) NOT NULL,
     capacity INT,
     qr_token VARCHAR(64) NOT NULL UNIQUE,
     status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     UNIQUE(restaurant_id, table_number)
   );
   CREATE INDEX idx_tables_restaurant_id ON restaurant_tables(restaurant_id);
   CREATE INDEX idx_tables_qr_token ON restaurant_tables(qr_token);
   ```

2. **`table` module**
   - `RestaurantTable` entity
   - `TableRepository`
   - `QrCodeService` — `generateQrToken()` (secure random UUID-based token), `regenerateQrToken(tableId)`
   - `TableService` + `TableServiceImpl`
   - DTOs: `CreateTableRequest`, `TableResponse`, `QrCodeResponse`
   - `TableController` (`/api/v1/restaurants/{restaurantId}/tables`)
     - `POST /` — create table (auto-generates QR token)
     - `GET /` — list all tables for restaurant
     - `GET /{id}`
     - `PUT /{id}`
     - `DELETE /{id}` — soft delete / set inactive
     - `POST /{id}/qr/regenerate` — regenerate QR token
     - `GET /{id}/qr` — return QR token + full URL

3. **QR URL format**: `https://plato.app/qr/{qr_token}` (configurable base URL via `application.yml`)

---

## Day 8 — Employee Module

### Tasks

1. **Flyway `V7__create_employees.sql`**
   ```sql
   CREATE TABLE employees (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     user_id UUID NOT NULL UNIQUE REFERENCES users(id),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     employee_role employee_role NOT NULL,
     joined_at DATE,
     status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_employees_restaurant_id ON employees(restaurant_id);
   CREATE INDEX idx_employees_user_id ON employees(user_id);
   ```

2. **`employee` module**
   - `Employee` entity
   - `EmployeeRole` enum: `MANAGER`, `CHEF`, `WAITER`, `CASHIER`
   - `EmployeeRepository`
   - `EmployeeService` + `EmployeeServiceImpl`
   - DTOs: `CreateEmployeeRequest`, `EmployeeResponse`, `UpdateEmployeeRoleRequest`
   - `EmployeeMapper`
   - `EmployeeController` (`/api/v1/restaurants/{restaurantId}/employees`)
     - `POST /` — Owner/Manager creates employee (creates User + Employee in one transaction)
     - `GET /` — list employees
     - `GET /{id}`
     - `PATCH /{id}/role`
     - `PATCH /{id}/status`
     - `DELETE /{id}` — deactivate

3. **`UserDetailsServiceImpl` update** — when employee logs in, load their `employee_role` as an additional granted authority.

---

## Day 9 — Menu Category & Menu Items Module

### Tasks

1. **Flyway `V8__create_menu.sql`**
   ```sql
   CREATE TABLE menu_categories (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     name VARCHAR(100) NOT NULL,
     description TEXT,
     display_order INT DEFAULT 0,
     is_active BOOLEAN DEFAULT true,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );

   CREATE TABLE menu_items (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     category_id UUID NOT NULL REFERENCES menu_categories(id),
     name VARCHAR(255) NOT NULL,
     description TEXT,
     price NUMERIC(10,2) NOT NULL CHECK (price >= 0),
     image_url TEXT,
     preparation_time INT,
     is_veg BOOLEAN DEFAULT true,
     is_available BOOLEAN DEFAULT true,
     display_order INT DEFAULT 0,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_menu_categories_restaurant_id ON menu_categories(restaurant_id);
   CREATE INDEX idx_menu_items_restaurant_id ON menu_items(restaurant_id);
   CREATE INDEX idx_menu_items_category_id ON menu_items(category_id);
   ```

2. **`menu` module**
   - `MenuCategory` + `MenuItem` entities
   - `MenuCategoryRepository`, `MenuItemRepository`
   - `MenuCategoryService` + `MenuItemService` + their implementations
   - DTOs: `CreateCategoryRequest`, `CategoryResponse`, `CreateMenuItemRequest`, `UpdateMenuItemRequest`, `MenuItemResponse`, `MenuResponse` (category + items nested)
   - `MenuMapper`
   - `MenuController` (`/api/v1/restaurants/{restaurantId}/menu`)
     - `POST /categories`
     - `GET /categories`
     - `PUT /categories/{id}`
     - `DELETE /categories/{id}`
     - `POST /items`
     - `GET /items`
     - `GET /items/{id}`
     - `PUT /items/{id}`
     - `PATCH /items/{id}/availability`
     - `DELETE /items/{id}`
     - `GET /` — full menu (categories + items) — publicly accessible for customers

---

## Day 10 — Week 2 Consolidation & Testing

### Tasks

1. **Integration tests** for all Week 2 modules
2. **Postman collection** — create and organize all restaurant management endpoints
3. **Tenant isolation verification** — confirm no cross-restaurant data leaks
4. **Review Flyway migrations** — ensure all indexes, constraints, and FKs are correct
5. **Swagger documentation** — verify all endpoints appear correctly with auth
6. **Code review checklist** — no business logic in controllers, constructor injection everywhere, no entity exposure

---

---

# WEEK 3 — Customer Flow (Sessions, Cart, Orders, Payments, Feedback)

**Goal**: The complete customer dining journey from QR scan to feedback submission.

---

## Day 11 — Customer Session Module

### Tasks

1. **Flyway `V9__create_customer_sessions.sql`**
   ```sql
   CREATE TABLE customer_sessions (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     table_id UUID NOT NULL REFERENCES restaurant_tables(id),
     session_token VARCHAR(128) NOT NULL UNIQUE,
     status session_status NOT NULL DEFAULT 'ACTIVE',
     guest_count INT DEFAULT 1,
     started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     last_activity TIMESTAMPTZ NOT NULL DEFAULT now(),
     expires_at TIMESTAMPTZ NOT NULL,
     ended_at TIMESTAMPTZ,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_sessions_session_token ON customer_sessions(session_token);
   CREATE INDEX idx_sessions_restaurant_id ON customer_sessions(restaurant_id);
   CREATE INDEX idx_sessions_table_id ON customer_sessions(table_id);
   ```

2. **`session` module**
   - `CustomerSession` entity
   - `SessionStatus` enum
   - `CustomerSessionRepository`
   - `CustomerSessionService` + `CustomerSessionServiceImpl`
     - `createSession(qrToken)` — validates QR, handles existing active session policy
     - `getSession(sessionToken)` — validates and touches `last_activity` + `expires_at`
     - `expireSession(sessionId)`
     - `closeSession(sessionId)`
   - DTOs: `SessionInitResponse` (sessionToken, restaurant, table, menu)
   - `CustomerSessionController` (`/api/v1/qr/{qrToken}`) — `GET` → creates session

3. **`CustomerSessionFilter`** — `OncePerRequestFilter`
   - Reads `X-Session-Token` header
   - Validates session exists, is active, not expired
   - Injects session context into request
   - Extends `last_activity` and `expires_at` on every request

4. **Session expiration configuration**
   ```yaml
   plato:
     session:
       timeout-minutes: 30
   ```

5. **Scheduled task** — `SessionExpirationJob` (`@Scheduled`) — marks timed-out sessions as `EXPIRED` every 5 minutes.

---

## Day 12 — Cart Module

### Tasks

1. **Flyway `V10__create_cart.sql`**
   ```sql
   CREATE TABLE carts (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     session_id UUID NOT NULL UNIQUE REFERENCES customer_sessions(id),
     status cart_status NOT NULL DEFAULT 'ACTIVE',
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );

   CREATE TABLE cart_items (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     cart_id UUID NOT NULL REFERENCES carts(id),
     menu_item_id UUID NOT NULL REFERENCES menu_items(id),
     quantity INT NOT NULL CHECK (quantity > 0),
     price_at_time NUMERIC(10,2) NOT NULL,
     special_request TEXT,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     UNIQUE(cart_id, menu_item_id)
   );
   CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
   ```

2. **`cart` module**
   - `Cart` + `CartItem` entities
   - `CartRepository`, `CartItemRepository`
   - `CartService` + `CartServiceImpl`
     - `getOrCreateCart(sessionId)` — auto-creates cart if none exists
     - `addItem(sessionId, request)` — validates item is available + captures `price_at_time`
     - `updateItem(sessionId, itemId, quantity)`
     - `removeItem(sessionId, itemId)`
     - `clearCart(sessionId)`
     - `getCart(sessionId)` — returns full cart with totals
   - DTOs: `AddCartItemRequest`, `UpdateCartItemRequest`, `CartItemResponse`, `CartResponse`
   - `CartController` (`/api/v1/customer/cart`) — all require `X-Session-Token`
     - `GET /` — get cart
     - `POST /items`
     - `PATCH /items/{itemId}`
     - `DELETE /items/{itemId}`
     - `DELETE /` — clear cart

---

## Day 13 — Order Module

### Tasks

1. **Flyway `V11__create_orders.sql`**
   ```sql
   CREATE TABLE orders (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     table_id UUID NOT NULL REFERENCES restaurant_tables(id),
     session_id UUID NOT NULL REFERENCES customer_sessions(id),
     order_number VARCHAR(20) NOT NULL UNIQUE,
     status order_status NOT NULL DEFAULT 'PLACED',
     subtotal NUMERIC(10,2) NOT NULL,
     tax NUMERIC(10,2) NOT NULL DEFAULT 0,
     discount NUMERIC(10,2) NOT NULL DEFAULT 0,
     grand_total NUMERIC(10,2) NOT NULL,
     placed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     completed_at TIMESTAMPTZ,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );

   CREATE TABLE order_items (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     order_id UUID NOT NULL REFERENCES orders(id),
     menu_item_id UUID NOT NULL REFERENCES menu_items(id),
     quantity INT NOT NULL CHECK (quantity > 0),
     unit_price NUMERIC(10,2) NOT NULL,
     special_request TEXT,
     status order_item_status NOT NULL DEFAULT 'PENDING',
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_orders_restaurant_id ON orders(restaurant_id);
   CREATE INDEX idx_orders_session_id ON orders(session_id);
   CREATE INDEX idx_orders_status ON orders(status);
   CREATE INDEX idx_order_items_order_id ON order_items(order_id);
   ```

2. **`order` module**
   - `Order` + `OrderItem` entities
   - `OrderStatus`, `OrderItemStatus` enums
   - `OrderRepository`, `OrderItemRepository`
   - `OrderService` + `OrderServiceImpl`
     - `placeOrder(sessionId)` — **fully transactional**: validate session → validate cart → validate all items available → calculate totals (apply tax from restaurant settings) → create order + items → mark cart as `ORDER_PLACED` → create new empty cart → publish WebSocket event
     - `getOrdersBySession(sessionId)`
     - `getOrdersByRestaurant(restaurantId, pageable)` — for employees/owner
     - `updateOrderStatus(orderId, newStatus)` — validates allowed transitions
     - `updateOrderItemStatus(itemId, status)` — Chef use
     - `cancelOrder(orderId)`
   - `OrderNumberGenerator` — `ORD-{YYYYMMDD}-{sequence}`
   - DTOs: `OrderResponse`, `OrderItemResponse`, `OrderStatusUpdateRequest`, `BillResponse`
   - `CustomerOrderController` (`/api/v1/customer/orders`) — session token auth
     - `POST /` — place order from current cart
     - `GET /` — customer's session orders
   - `RestaurantOrderController` (`/api/v1/restaurants/{id}/orders`) — JWT auth
     - `GET /` — paginated orders
     - `GET /{orderId}`
     - `PATCH /{orderId}/status`
     - `PATCH /items/{itemId}/status`

---

## Day 14 — Payment Module

### Tasks

1. **Flyway `V12__create_payments.sql`**
   ```sql
   CREATE TABLE payments (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     session_id UUID NOT NULL REFERENCES customer_sessions(id),
     amount NUMERIC(10,2) NOT NULL,
     payment_method payment_method NOT NULL,
     payment_status payment_status NOT NULL DEFAULT 'PENDING',
     transaction_reference VARCHAR(255),
     paid_at TIMESTAMPTZ,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_payments_session_id ON payments(session_id);
   ```

2. **`payment` module**
   - `Payment` entity
   - `PaymentMethod`, `PaymentStatus` enums
   - `PaymentRepository`
   - `PaymentService` + `PaymentServiceImpl`
     - `getBill(sessionId)` — calculates complete bill across all session orders
     - `initiatePayment(sessionId, request)` — creates pending payment
     - `confirmPayment(paymentId)` — marks payment SUCCESS → closes session → publishes event
     - `getPaymentBySession(sessionId)`
   - DTOs: `BillResponse`, `InitiatePaymentRequest`, `PaymentResponse`
   - `PaymentController`
     - Customer: `GET /api/v1/customer/bill`, `POST /api/v1/customer/payment`
     - Employee/Cashier: `POST /api/v1/restaurants/{id}/payments/{paymentId}/confirm`

---

## Day 15 — Feedback Module & Week 3 Review

### Tasks

1. **Flyway `V13__create_feedback.sql`**
   ```sql
   CREATE TABLE feedback (
     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     restaurant_id UUID NOT NULL REFERENCES restaurants(id),
     session_id UUID NOT NULL UNIQUE REFERENCES customer_sessions(id),
     rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
     review TEXT,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX idx_feedback_restaurant_id ON feedback(restaurant_id);
   ```

2. **`feedback` module**
   - `Feedback` entity
   - `FeedbackRepository`
   - `FeedbackService` + `FeedbackServiceImpl`
     - `submitFeedback(sessionId, request)` — only if session is PAID; enforce one-per-session
     - `getFeedbackByRestaurant(restaurantId, pageable)`
   - DTOs: `SubmitFeedbackRequest`, `FeedbackResponse`
   - `FeedbackController`
     - Customer: `POST /api/v1/customer/feedback`
     - Owner: `GET /api/v1/restaurants/{id}/feedback`

3. **End-to-end customer flow test** (manual + automated)
   - QR scan → session created → menu viewed → item added → item updated → item removed → order placed → status updated → bill viewed → payment confirmed → feedback submitted

4. **Business rule validation** — check all constraints hold (no double feedback, no order after session closed, etc.)

---

---

# WEEK 4 — WebSockets, Analytics, Testing, Hardening & Deployment

**Goal**: Real-time layer, analytics, full test coverage, production hardening, Docker, deployment.

---

## Day 16 — WebSocket Layer

### Tasks

1. **`websocket` package**
   - `WebSocketConfig` — configure STOMP broker, SockJS endpoint
   - `WebSocketAuthInterceptor` — validate JWT or session token on `CONNECT`

2. **STOMP topic structure**
   ```
   /topic/restaurant/{restaurantId}/orders       ← Kitchen receives new orders
   /topic/restaurant/{restaurantId}/dashboard    ← Owner dashboard updates
   /topic/session/{sessionId}/orders             ← Customer receives order updates
   /topic/session/{sessionId}/payment            ← Customer receives payment confirmation
   ```

3. **`notification` package**
   - `WebSocketNotificationService` — wraps `SimpMessagingTemplate`
     - `notifyKitchen(restaurantId, orderEvent)`
     - `notifyCustomer(sessionId, event)`
     - `notifyDashboard(restaurantId, event)`

4. **Wire notifications into existing services**
   - `OrderServiceImpl.placeOrder()` → `notifyKitchen()`
   - `OrderServiceImpl.updateOrderStatus()` → `notifyCustomer()` + `notifyDashboard()`
   - `PaymentServiceImpl.confirmPayment()` → `notifyCustomer()` + `notifyDashboard()`

5. **WebSocket events are fire-and-forget** — failures must not roll back business transactions.

---

## Day 17 — Analytics Module

### Tasks

1. **`analytics` module**
   - `AnalyticsService` — uses JPQL/native queries against existing tables (no new tables needed for MVP)
   - Queries:
     - `getDashboardSummary(restaurantId)` — today's orders, revenue, active sessions, avg rating
     - `getRevenueByDate(restaurantId, from, to)`
     - `getTopItems(restaurantId, limit)`
     - `getOrdersByStatus(restaurantId)`
     - `getPlatformSummary()` — Super Admin: total restaurants, total revenue, orders today
   - DTOs: `DashboardSummaryResponse`, `RevenueDataPoint`, `TopItemResponse`
   - `AnalyticsController`
     - `GET /api/v1/restaurants/{id}/analytics/summary`
     - `GET /api/v1/restaurants/{id}/analytics/revenue`
     - `GET /api/v1/restaurants/{id}/analytics/top-items`
     - `GET /api/v1/admin/analytics/platform` — Super Admin

2. **Pagination** on all list endpoints that could grow large.

---

## Day 18 — Testing

### Tasks

1. **Unit Tests** (JUnit 5 + Mockito)
   - `AuthServiceImpl` — login, invalid credentials, token refresh
   - `RestaurantServiceImpl` — create, tenant isolation
   - `OrderServiceImpl` — place order, status transitions, cancellation
   - `CartServiceImpl` — add, update, remove, unavailable item
   - `CustomerSessionServiceImpl` — create, expiry
   - `PaymentServiceImpl` — bill calculation, confirm payment

2. **Integration Tests** (`@SpringBootTest` + Testcontainers PostgreSQL)
   - Full auth flow
   - Full order flow (session → cart → order → payment)
   - Tenant isolation (owner A cannot access restaurant B)
   - Session expiration enforcement

3. **Controller Tests** (`@WebMvcTest`)
   - Authorization checks (401/403 on wrong roles)
   - Validation rejection (400 on bad input)

4. **Test configuration**
   ```yaml
   # application-test.yml
   spring.datasource.url: (Testcontainers manages this)
   spring.flyway.enabled: true
   ```

---

## Day 19 — Production Hardening

### Tasks

1. **Security hardening**
   - CORS — restrict to production frontend domain only
   - HTTP security headers (via `SecurityConfig`)
   - Token blacklisting for logout (check revoked flag on every request)
   - Input sanitization on free-text fields (review + special requests)
   - Confirm no stack traces leak in any response

2. **Performance**
   - Review all JPA relationships — confirm no N+1 queries (use `JOIN FETCH` where needed)
   - Add `@Transactional(readOnly = true)` to all read-only service methods
   - Confirm HikariCP pool size is configured
   - Pagination enforced on all list endpoints

3. **Environment configuration**
   - `application.yml` references env vars only for secrets (`${DB_PASSWORD}`, `${JWT_SECRET}`)
   - No secrets hardcoded in source code

4. **Docker setup**
   - `Dockerfile` (multi-stage build)
     ```dockerfile
     FROM eclipse-temurin:21-jdk AS build
     WORKDIR /app
     COPY . .
     RUN ./mvnw clean package -DskipTests

     FROM eclipse-temurin:21-jre
     WORKDIR /app
     COPY --from=build /app/target/plato-*.jar app.jar
     ENTRYPOINT ["java", "-jar", "app.jar"]
     ```
   - `docker-compose.yml` — backend + PostgreSQL
     ```yaml
     services:
       db:
         image: postgres:16
         environment:
           POSTGRES_DB: plato
           POSTGRES_USER: ${DB_USER}
           POSTGRES_PASSWORD: ${DB_PASSWORD}
         volumes:
           - postgres_data:/var/lib/postgresql/data
       backend:
         build: .
         ports:
           - "8080:8080"
         environment:
           DB_URL: jdbc:postgresql://db:5432/plato
           DB_USER: ${DB_USER}
           DB_PASSWORD: ${DB_PASSWORD}
           JWT_SECRET: ${JWT_SECRET}
         depends_on:
           - db
     volumes:
       postgres_data:
     ```

5. **`.env.example`** — document all required environment variables.

---

## Day 20 — Deployment & Final Review

### Tasks

1. **Deployment options** (pick one)

   | Option | Steps |
   |--------|-------|
   | **Railway / Render** | Push Docker image → connect PostgreSQL addon → set env vars → deploy |
   | **DigitalOcean Droplet** | SSH in → install Docker → `git clone` → `docker compose up -d` |
   | **AWS EC2 + RDS** | Launch EC2 → create RDS PostgreSQL → push Docker image → run |

2. **Production checklist**
   - [ ] Database created and Flyway migrations applied
   - [ ] All env vars set in production
   - [ ] HTTPS configured (Nginx + Let's Encrypt or load balancer)
   - [ ] CORS restricted to production frontend domain
   - [ ] Swagger disabled in production (or protected by auth)
   - [ ] Health check endpoint working (`/actuator/health`)
   - [ ] Logs flowing to a log aggregator or file

3. **Spring Boot Actuator**
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health, info, metrics
     endpoint:
       health:
         show-details: never  # never expose DB details publicly
   ```

4. **Final API test** — run Postman collection against production URL.

5. **Docs folder** — write `README.md` in `Docs/` covering:
   - Project overview
   - Local setup steps
   - Environment variables
   - API base URL
   - Deployment instructions

---

---

# Module Completion Order (Summary)

Follows the confirmed database hierarchy exactly.

```
Platform
│
Week 1 — Users (Authentication)
  ✦ Project setup & package structure
  ✦ application.yml + Flyway
  ✦ common (ApiResponse, BaseEntity, GlobalExceptionHandler)
  ✦ V1 enums + V2 users + V3 refresh_tokens
  ✦ Spring Security + JWT filter
  ✦ auth module (login, refresh, logout)
  ✦ user management (CRUD)
│
Week 2 — Restaurants → {Tables, Employees, Categories, Menu Items}
  ✦ V4 restaurants (settings embedded)
  ✦ V5 restaurant_tables + QR code generation
  ✦ V6 employees
  ✦ V7 menu_categories + menu_items
│
Week 3 — Restaurants → {Sessions → {cart_items, Orders → order_items, payments, Feedback}}
  ✦ V8 customer_sessions + QR scan entry point + CustomerSessionFilter
  ✦ V9 cart_items (under sessions)
  ✦ V10 orders + order_items (transactional place order)
  ✦ V11 payments (under sessions)
  ✦ V12 feedback (under sessions)
│
Week 4 — Cross-cutting: Real-time, Analytics, Testing, Deployment
  ✦ WebSocket (STOMP + SockJS)
  ✦ Notification service wired into orders + payments
  ✦ Analytics (queries across all tables)
  ✦ Tests (unit + integration + controller)
  ✦ Docker + docker-compose
  ✦ Production hardening
  ✦ Deployment
  ✦ Documentation
```

---

# Flyway Migrations Sequence

Aligned to the confirmed database hierarchy. **11 migrations, 11 tables.**

| # | Migration | Table(s) | Hierarchy Level |
|---|-----------|----------|-----------------|
| V1 | `V1__create_enums.sql` | PostgreSQL enums only | Platform |
| V2 | `V2__create_users.sql` | `users` | Users |
| V3 | `V3__create_restaurants.sql` | `restaurants` (settings embedded) | Restaurants |
| V4 | `V4__create_restaurant_tables.sql` | `restaurant_tables` | Restaurants → Tables |
| V5 | `V5__create_employees.sql` | `employees` | Restaurants → Employees |
| V6 | `V6__create_menu.sql` | `menu_categories`, `menu_items` | Restaurants → Categories → Items |
| V7 | `V7__create_customer_sessions.sql` | `customer_sessions` | Restaurants → Sessions |
| V8 | `V8__create_cart_items.sql` | `cart_items` | Sessions (sub-table) |
| V9 | `V9__create_orders.sql` | `orders`, `order_items` | Sessions → Orders |
| V10 | `V10__create_payments.sql` | `payments` | Sessions (sub-table) |
| V11 | `V11__create_feedback.sql` | `feedback` | Sessions → Feedback |

> **Removed**: `refresh_tokens` — using a single 24h JWT, no refresh token needed.  
> **Removed**: `restaurant_settings` — settings are columns inside `restaurants`.  
> **Removed**: `carts` — cart is just `cart_items` linked directly to `session_id`.

---

# Package Structure (Final)

```
com.miniproject.plato
├── PlatoApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── SecurityProperties.java
│   ├── SwaggerConfig.java
│   └── WebSocketConfig.java
├── common/
│   ├── ApiResponse.java
│   ├── PagedResponse.java
│   └── BaseEntity.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── PlatoException.java
│   ├── ResourceNotFoundException.java
│   ├── UnauthorizedAccessException.java
│   ├── ConflictException.java
│   ├── SessionExpiredException.java
│   └── MenuItemUnavailableException.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── CustomerSessionFilter.java
│   └── UserDetailsServiceImpl.java
├── auth/
│   ├── controller/AuthController.java
│   ├── service/AuthService.java          ← login() + logout() only
│   ├── service/AuthServiceImpl.java
│   └── dto/ {LoginRequest, LoginResponse}
├── user/
│   ├── controller/UserController.java
│   ├── service/ ...
│   ├── repository/UserRepository.java
│   ├── entity/User.java                  ← no RefreshToken entity
│   └── dto/ ...
├── restaurant/                          ← Restaurants
│   ├── controller/RestaurantController.java
│   ├── service/ ...
│   ├── repository/RestaurantRepository.java
│   ├── entity/Restaurant.java            ← settings embedded here
│   └── dto/ ...
├── table/                               ← Restaurants → Tables
│   ├── controller/TableController.java
│   ├── service/ {TableService, QrCodeService}
│   ├── repository/TableRepository.java
│   ├── entity/RestaurantTable.java
│   └── dto/ ...
├── employee/                            ← Restaurants → Employees
│   └── ... (same structure)
├── menu/                                ← Restaurants → Categories → Menu Items
│   ├── controller/MenuController.java
│   ├── service/ {MenuCategoryService, MenuItemService}
│   ├── repository/ ...
│   ├── entity/ {MenuCategory, MenuItem}
│   └── dto/ ...
├── session/                             ← Restaurants → Sessions
│   ├── controller/SessionController.java
│   ├── service/CustomerSessionService.java
│   ├── repository/CustomerSessionRepository.java
│   ├── entity/CustomerSession.java
│   ├── entity/CartItem.java              ← Sessions (sub-table)
│   ├── entity/Payment.java              ← Sessions (sub-table)
│   ├── repository/ {CartItemRepository, PaymentRepository}
│   ├── scheduler/SessionExpirationJob.java
│   └── dto/ ...
├── order/                               ← Restaurants → Sessions → Orders
│   ├── controller/ {CustomerOrderController, RestaurantOrderController}
│   ├── entity/ {Order, OrderItem}
│   └── ... (same structure)
├── feedback/                            ← Restaurants → Sessions → Feedback
│   └── ... (same structure)
├── websocket/
│   ├── WebSocketAuthInterceptor.java
│   └── WebSocketNotificationService.java
├── analytics/
│   ├── controller/AnalyticsController.java
│   ├── service/AnalyticsService.java
│   └── dto/ ...
└── util/
    └── OrderNumberGenerator.java
```

---

> **Start Day 1**: Fix the package, set up `application.yml`, write `V1__create_enums.sql`, and verify the app boots against a local PostgreSQL instance.
