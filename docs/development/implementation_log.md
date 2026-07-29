# Plato — Implementation Log

> **Rule**: Every time a task from the plan is completed, a new section is added here.  
> One section per phase/feature. Updated in order as development progresses.

---

## Index

| # | Section | Phase | Status |
|---|---------|-------|--------|
| 1 | [Project Foundation](#1-project-foundation) | Week 1 · Day 1 | ✅ Done |
| 2 | [Common Package](#2-common-package) | Week 1 · Day 1 | ✅ Done |
| 3 | [Exception Package & GlobalExceptionHandler](#3-exception-package--globalexceptionhandler) | Week 1 · Day 1 | ✅ Done |
| 4 | [Flyway & Migrations V1–V2](#4-flyway--migrations-v1v2) | Week 1 · Day 1 | ✅ Done |
| 5 | [User Module — Day 2](#5-user-module--day-2) | Week 1 · Day 2 | ✅ Done |
| 6 | [Super Admin Seed — Day 2 Task 3](#6-super-admin-seed--day-2-task-3) | Week 1 · Day 2 | ✅ Done |
| 7 | Tables & QR | Week 2 · Day 7 | 🔲 Pending |
| 8 | Employees | Week 2 · Day 8 | 🔲 Pending |
| 9 | Menu | Week 2 · Day 9 | 🔲 Pending |
| 10 | Customer Sessions | Week 3 · Day 11 | 🔲 Pending |
| 11 | Cart | Week 3 · Day 12 | 🔲 Pending |
| 12 | Orders | Week 3 · Day 13 | 🔲 Pending |
| 13 | Payments | Week 3 · Day 14 | 🔲 Pending |
| 14 | Feedback | Week 3 · Day 15 | 🔲 Pending |
| 15 | WebSockets | Week 4 · Day 16 | 🔲 Pending |
| 16 | Analytics | Week 4 · Day 17 | 🔲 Pending |
| 17 | Testing | Week 4 · Day 18 | 🔲 Pending |
| 18 | Deployment | Week 4 · Day 19–20 | 🔲 Pending |

---

---

## 1. Project Foundation

**Phase**: Week 1 · Day 1  
**Date**: 2026-07-26  
**Plan tasks**: Task 1 (Fix PlatoApplication), Task 2 (application.yml)

---

### What Was Done

Two foundational files were set up before any feature code can be written.

---

### 1.1 — `pom.xml` (already corrected before Day 1 began)

**File**: [`backend/pom.xml`](../../backend/pom.xml)

The original generated `pom.xml` had several problems that would have caused build failures. It was replaced entirely.

#### Key changes and why

| Change | Reason |
|--------|--------|
| Spring Boot `3.5.5` (was `4.1.0`) | `4.1.0` does not exist. `3.5.5` is the latest stable release. |
| Java `21` (was `17`) | Java 21 is the current LTS. Virtual threads and modern language features. |
| `groupId` → `com.miniproject` (was `com.miniProject`) | Java package conventions require all-lowercase. |
| Added JWT dependencies (`jjwt-api`, `jjwt-impl`, `jjwt-jackson` v0.12.7) | Needed for generating and validating staff authentication tokens. |
| Added Flyway (`flyway-core`, `flyway-database-postgresql`) | Required for versioned, safe database migrations. |
| Added Swagger (`springdoc-openapi-starter-webmvc-ui` v2.8.9) | Auto-generates interactive API documentation from controller code. |
| Replaced broken test starters (e.g. `spring-boot-starter-security-test`) with `spring-boot-starter-test` | Those artifact IDs do not exist. `spring-boot-starter-test` is the correct single test dependency. |
| Simplified build plugin — removed verbose Lombok annotation processor config | Spring Boot 3.x handles Lombok annotation processing automatically. |

---

### 1.2 — `PlatoApplication.java`

**File**: [`backend/src/main/java/com/miniproject/plato/PlatoApplication.java`](../../backend/src/main/java/com/miniproject/plato/PlatoApplication.java)

#### What changed

```java
// Before
package com.miniProject.Plato;

@SpringBootApplication
public class PlatoApplication { ... }

// After
package com.miniproject.plato;

@SpringBootApplication
@EnableJpaAuditing
public class PlatoApplication { ... }
```

#### Why each change

**Package rename (`com.miniProject.Plato` → `com.miniproject.plato`)**

Spring Boot's component scan starts from the package of the class annotated with `@SpringBootApplication`. It finds all `@Service`, `@Repository`, `@Controller`, `@Component` classes by scanning that package and all sub-packages.

The `pom.xml` defines `groupId = com.miniproject`. The package and groupId must match. `com.miniProject` (mixed case) would not match `com.miniproject` on a case-sensitive filesystem (Linux, production servers). This would cause beans not to be found, leading to startup failures in production even if it appeared to work on macOS (which has a case-insensitive filesystem).

**`@EnableJpaAuditing`**

`BaseEntity` (to be created in the next section) uses `@CreatedDate` and `@LastModifiedDate` annotations. These are JPA Auditing annotations — they tell Hibernate to automatically set `created_at` and `updated_at` timestamps when an entity is saved.

For these annotations to actually work, JPA Auditing must be explicitly enabled somewhere in the application. The `@EnableJpaAuditing` annotation on the main class activates this feature for the entire application. Without it, `@CreatedDate` and `@LastModifiedDate` are silently ignored and timestamps remain null.

This was added now (Day 1) because `BaseEntity` depends on it. Adding it at the point of use prevents the hard-to-debug "why are my timestamps null?" problem later.

**File moved**

The physical file was moved from:
```
src/main/java/com/miniProject/Plato/PlatoApplication.java
```
to:
```
src/main/java/com/miniproject/plato/PlatoApplication.java
```
The old `com/miniProject/` directory was deleted. The test class `PlatoApplicationTests.java` was also moved and its package declaration updated to match.

---

### 1.3 — `application.yml`

**File**: [`backend/src/main/resources/application.yml`](../../backend/src/main/resources/application.yml)

The near-empty `application.properties` (`spring.application.name=Plato`) was deleted and replaced with a full `application.yml`.

#### Why YAML over `.properties`

YAML supports nested structure. Related settings group visually together (e.g., all `plato.jwt.*` settings under one `plato.jwt:` block). Both formats work in Spring Boot — this is a readability choice for a config file that will grow to 60+ lines.

#### Every setting explained

```yaml
spring:
  application:
    name: Plato
```
The application name appears in logs and in Spring Boot Actuator's `/actuator/info` endpoint. It identifies which service is running.

---

```yaml
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/plato}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
```
The `${VAR:default}` syntax means: read from environment variable `VAR`; if it's not set, use the default value after the colon.

This means in development, you can run without setting any env vars and it connects to `localhost:5432/plato` with `postgres/postgres`. In production, you set `DB_URL`, `DB_USER`, `DB_PASSWORD` as environment variables and the real values are used — never hardcoded in source code.

---

```yaml
    hikari:
      pool-name: PlatoHikariPool
      maximum-pool-size: 10
      minimum-idle: 2
```
HikariCP is Spring Boot's built-in connection pool. Instead of opening a new database connection for every request (expensive — takes ~50ms each), HikariCP maintains a pool of open connections that are reused.

`maximum-pool-size: 10` means at most 10 simultaneous database connections. This is appropriate for a single-instance deployment. `minimum-idle: 2` means at least 2 connections are always kept open and ready.

---

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
```
`validate` tells Hibernate: look at the entity classes, compare them with the actual database schema, and if they don't match, refuse to start. It does **not** create, alter, or drop any tables.

This is the correct setting for production. Flyway is responsible for creating and modifying the schema. Hibernate is only allowed to check that entities and tables are in sync.

The alternative `ddl-auto: create` would drop and recreate all tables on every restart — losing all data. `ddl-auto: update` would alter tables, which can silently break data integrity. Neither is acceptable in production.

---

```yaml
    open-in-view: false
```
By default, Spring Boot holds the JPA `EntityManager` (the database connection context) open for the entire duration of an HTTP request — including view rendering time. This is called the "Open Session in View" anti-pattern. It means database connections are held open much longer than necessary, reducing throughput.

Setting this to `false` means the database connection is released as soon as the service method finishes. Controllers and view layer have no database access. This enforces clean separation of layers and improves connection pool efficiency.

---

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```
Enables Flyway and points it to `src/main/resources/db/migration/` where migration SQL files will live. `baseline-on-migrate: false` means Flyway will not attempt to mark an existing non-empty database as a baseline — it expects to manage the schema from scratch.

---

```yaml
plato:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-in-production-must-be-at-least-32-chars}
    expiration: 86400000
```
Custom application properties under the `plato` namespace. A `SecurityProperties` `@ConfigurationProperties` class will bind these at startup.

`expiration: 86400000` = 24 hours in milliseconds. Staff JWT tokens last 24 hours. No refresh tokens are needed.

The secret must be at least 256 bits (32 characters) for HMAC-SHA256 signing. The default value is intentionally long and labelled "change in production" so it's obvious when it's the dev default.

---

```yaml
  session:
    timeout-minutes: 30
```
Customer dining sessions expire after 30 minutes of inactivity. Every customer request resets this timer (sliding expiry). A scheduled job runs every 5 minutes to mark expired sessions in the database.

---

```yaml
  qr:
    base-url: ${QR_BASE_URL:http://localhost:3000/qr}
```
When a table's QR code is generated, the URL is: `{base-url}/{qr_token}`. In production this would be `https://plato.app/qr`. In development it points to the local frontend. Making this configurable means changing the domain requires only an environment variable change, not a code change.

---

```yaml
springdoc:
  swagger-ui:
    try-it-out-enabled: true
```
Enables the "Try it out" button in Swagger UI so endpoints can be tested directly from the browser during development.

---

```yaml
logging:
  level:
    com.miniproject.plato: DEBUG
    org.springframework.security: INFO
```
`DEBUG` logging for our own code shows detailed information during development. `INFO` for Spring Security avoids flooding logs with filter chain decisions on every request.

---

### Files Created / Modified

| File | Action |
|------|--------|
| `backend/pom.xml` | Replaced entirely |
| `backend/src/main/java/com/miniproject/plato/PlatoApplication.java` | Created (moved + updated) |
| `backend/src/test/java/com/miniproject/plato/PlatoApplicationTests.java` | Created (moved + updated) |
| `backend/src/main/resources/application.yml` | Created |
| `backend/src/main/resources/application.properties` | Deleted |

---

### What This Enables

With these two files in place:
- Spring Boot can scan the correct package for components
- The database connection is configured (will work once PostgreSQL is running locally)
- Flyway is enabled and waiting for migration files
- JPA auditing is active for `@CreatedDate` / `@LastModifiedDate`
- Custom `plato.*` properties can be injected via `@ConfigurationProperties`
- All secrets come from environment variables, never hardcoded

---

---

## 2. Common Package

**Phase**: Week 1 · Day 1  
**Date**: 2026-07-26  
**Plan task**: Day 1 · Task 3

---

### What Was Done

Three infrastructure classes were created in `com.miniproject.plato.common`. These are not features — they are the tools every feature uses. They must exist before any controller, service, or entity can be written.

---

### 2.1 — `ApiResponse<T>`

**File**: [`common/ApiResponse.java`](../../backend/src/main/java/com/miniproject/plato/common/ApiResponse.java)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(String message, T data) { ... }
    public static <T> ApiResponse<T> ok(String message) { ... }       // no data
    public static <T> ApiResponse<T> error(String message) { ... }    // data omitted
}
```

**Why `@JsonInclude(NON_NULL)`**  
When `data` is null (error responses, successful deletes), Jackson skips the `data` field entirely. The JSON stays clean:
```json
{ "success": false, "message": "Restaurant not found" }
```
instead of:
```json
{ "success": false, "message": "Restaurant not found", "data": null }
```

**Why static factory methods instead of constructors**  
Controller code reads like plain English:
```java
return ApiResponse.ok("Order placed", orderResponse);   // clear intent
return ApiResponse.error("Menu item unavailable");       // clear intent
```

---

### 2.2 — `PagedResponse<T>`

**File**: [`common/PagedResponse.java`](../../backend/src/main/java/com/miniproject/plato/common/PagedResponse.java)

```java
public class PagedResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean last;

    public PagedResponse(Page<T> pageResult) { ... }  // built from Spring's Page<T>
}
```

**Why a separate class instead of returning `Page<T>` directly**  
Spring's internal `Page<T>` serialises to JSON with internal implementation fields that the frontend doesn't need and shouldn't depend on. `PagedResponse` exposes only the fields that matter and gives a stable contract — if Spring changes its internal `Page` structure, the API response stays the same.

**Usage in a controller**:
```java
Page<OrderResponse> page = orderService.getOrders(restaurantId, pageable);
return ResponseEntity.ok(ApiResponse.ok("Orders retrieved", new PagedResponse<>(page)));
```

---

### 2.3 — `BaseEntity`

**File**: [`common/BaseEntity.java`](../../backend/src/main/java/com/miniproject/plato/common/BaseEntity.java)

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

**Key annotation decisions:**

| Annotation | Why |
|------------|-----|
| `@MappedSuperclass` | No table is created for `BaseEntity`. Its columns are included in each subclass table. |
| `@EntityListeners(AuditingEntityListener.class)` | Wires Spring Data JPA auditing onto this class. `@EnableJpaAuditing` on `PlatoApplication` activates it globally. |
| `GenerationType.UUID` | Hibernate generates a UUID before the INSERT. Database-independent, non-sequential, non-guessable. |
| `@Column(updatable = false)` on `createdAt` | Prevents any UPDATE statement from ever changing the creation timestamp. |

**Note on `AuditListener` in the plan**  
The plan referenced an `AuditListener` class. No custom class is needed. Spring Data JPA ships `AuditingEntityListener` built-in. By placing `@EntityListeners(AuditingEntityListener.class)` on `BaseEntity` and `@EnableJpaAuditing` on `PlatoApplication`, timestamps are managed automatically. This is one less file to maintain.

---

### Files Created

| File | Purpose |
|------|---------|
| `common/ApiResponse.java` | Universal response envelope for all endpoints |
| `common/PagedResponse.java` | Paginated list response with metadata |
| `common/BaseEntity.java` | Abstract parent: UUID id + auto-timestamps |

---

### What This Enables

- Every controller can return `ApiResponse.ok(...)` or `ApiResponse.error(...)`
- Every paginated endpoint has a consistent JSON shape
- Every entity that extends `BaseEntity` gets `id`, `createdAt`, `updatedAt` for free
- No timestamp logic needs to be written in any service method

---

---

## 3. Exception Package & GlobalExceptionHandler

**Phase**: Week 1 · Day 1  
**Date**: 2026-07-26  
**Plan tasks**: Day 1 · Task 3 (GlobalExceptionHandler) + Task 4 (exception classes)

---

### What Was Done

Six exception classes and one handler were created. They work as a system — the exceptions carry meaning and HTTP status codes, the handler converts them to the correct JSON response automatically.

---

### 3.1 — Exception Class Hierarchy

```
RuntimeException
  └── PlatoException (carries HttpStatus)
        ├── ResourceNotFoundException   → 404 Not Found
        ├── UnauthorizedAccessException → 403 Forbidden
        ├── ConflictException           → 409 Conflict
        ├── ValidationException         → 400 Bad Request
        └── SessionExpiredException     → 401 Unauthorized
```

**`PlatoException`** — base class. Stores the `HttpStatus` in the exception itself.
```java
public class PlatoException extends RuntimeException {
    private final HttpStatus status;   // e.g. HttpStatus.NOT_FOUND
}
```

This is the key design. Because every custom exception carries its own `HttpStatus`, the `GlobalExceptionHandler` needs only **one** handler for all of them — not a separate method per exception type:
```java
@ExceptionHandler(PlatoException.class)
public ResponseEntity<ApiResponse<Void>> handlePlatoException(PlatoException ex) {
    return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(ex.getMessage()));
}
```

New exception types added in the future automatically work with this handler.

---

### 3.2 — Each Exception Explained

| Class | HTTP | When to throw |
|-------|------|---------------|
| `ResourceNotFoundException` | 404 | Entity not found by id or field: `orElseThrow(() -> new ResourceNotFoundException("Restaurant", id))` |
| `UnauthorizedAccessException` | 403 | Ownership violation in service: Owner trying to access another Owner's data |
| `ConflictException` | 409 | Duplicate unique value: email already exists, restaurant name already taken |
| `ValidationException` | 400 | Business rule failure that `@Valid` can't express: empty cart, restaurant closed |
| `SessionExpiredException` | 401 | Customer session token missing, not found, or past `expires_at` |

**`UnauthorizedAccessException` vs Spring Security's `AccessDeniedException`**  
Spring Security throws `AccessDeniedException` when `@PreAuthorize` role checks fail (e.g., a Waiter trying to call an Owner-only endpoint). `UnauthorizedAccessException` is for business-level ownership checks inside service methods (e.g., an Owner trying to edit another restaurant they don't own). Both get handled — Spring's by its own handler in `GlobalExceptionHandler`, ours by the `PlatoException` handler.

---

### 3.3 — `GlobalExceptionHandler`

**File**: [`exception/GlobalExceptionHandler.java`](../../backend/src/main/java/com/miniproject/plato/exception/GlobalExceptionHandler.java)

`@RestControllerAdvice` tells Spring: intercept every unhandled exception from every controller and route it here.

**Handler order (most specific wins):**

```
1. PlatoException        → uses ex.getStatus() + ex.getMessage() → any 4xx
2. MethodArgumentNotValidException → 400 + list of field errors (from @Valid)
3. AccessDeniedException → 403 (Spring Security role check failed)
4. AuthenticationException → 401 (no/bad JWT token)
5. Exception (catch-all) → 500 (logs full stack trace; safe vague message to client)
```

**Why field errors are returned as `List<String>` in `data`**  
For `@Valid` failures, each field has its own error message (e.g., `"email: must not be blank"`, `"password: size must be between 8 and 100"`). Putting them all in one message string makes them hard to parse. Returning them as a list lets the frontend highlight the exact fields that failed.

**Why the catch-all logs the full stack trace but returns a vague message**  
Stack traces contain class names, file paths, and internal logic that could help an attacker understand the system. The client gets `"An unexpected error occurred"`. The developer gets the full trace in the server logs.

---

### Files Created

| File | HTTP Status |
|------|-------------|
| `exception/PlatoException.java` | base |
| `exception/ResourceNotFoundException.java` | 404 |
| `exception/UnauthorizedAccessException.java` | 403 |
| `exception/ConflictException.java` | 409 |
| `exception/ValidationException.java` | 400 |
| `exception/SessionExpiredException.java` | 401 |
| `exception/GlobalExceptionHandler.java` | handler |

---

### What This Enables

- Zero try-catch blocks needed in any controller
- Every error response uses `ApiResponse` format automatically
- Adding a new exception type requires only: extend `PlatoException`, pick an `HttpStatus` — the handler picks it up automatically
- Security: internal details never leak to clients

---

---

## 4. Flyway & Migrations V1–V2

**Phase**: Week 1 · Day 1  
**Date**: 2026-07-27  
**Plan tasks**: Day 1 · Tasks 5 & 6

---

### What Was Done

The Flyway migration directory was created and the first two migration files were written. These two files build the foundation of the entire database schema.

---

### 4.1 — Flyway Migration Directory

```
backend/src/main/resources/db/migration/
  V1__create_enums.sql
  V2__create_users.sql
```

This location matches the `spring.flyway.locations: classpath:db/migration` setting in `application.yml`. When the app starts, Flyway scans this directory, compares the files against its internal `flyway_schema_history` table, and runs any migrations it hasn't executed yet — in version order.

**The golden rule**: once a migration file is committed and run on any environment, it must never be modified. Changes to the schema always go in a new numbered migration file.

---

### 4.2 — `V1__create_enums.sql`

**File**: [`db/migration/V1__create_enums.sql`](../../backend/src/main/resources/db/migration/V1__create_enums.sql)

All PostgreSQL custom enum types for the entire schema are created in the very first migration. Why first? Because every table that uses these types must be created **after** the types exist. If the enums were spread across multiple migrations, the ordering dependency would be fragile and easy to break.

| Enum | Values | Used by |
|------|--------|---------|
| `user_role` | `SUPER_ADMIN`, `OWNER`, `EMPLOYEE` | `users` |
| `user_status` | `ACTIVE`, `SUSPENDED`, `DELETED` | `users` |
| `restaurant_status` | `ACTIVE`, `INACTIVE`, `SUSPENDED` | `restaurants` |
| `employee_role` | `MANAGER`, `CHEF`, `WAITER`, `CASHIER` | `employees` |
| `session_status` | `ACTIVE`, `CLOSED`, `EXPIRED` | `customer_sessions` |
| `order_status` | `PENDING`, `ACCEPTED`, `PREPARING`, `READY`, `SERVED`, `CANCELLED` | `orders` |
| `order_item_status` | `PENDING`, `PREPARING`, `READY`, `SERVED`, `CANCELLED` | `order_items` |
| `payment_method` | `CASH`, `CARD`, `UPI`, `ONLINE` | `payments` |
| `payment_status` | `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED` | `payments` |

**`cart_status` removed** — The original plan included `cart_status` but there is no separate `carts` table. Cart items are rows in `cart_items` associated to a session. The session's `session_status` covers the lifecycle of the cart implicitly.

---

### 4.3 — `V2__create_users.sql`

**File**: [`db/migration/V2__create_users.sql`](../../backend/src/main/resources/db/migration/V2__create_users.sql)

```sql
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    phone         VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    role          user_role    NOT NULL,
    status        user_status  NOT NULL DEFAULT 'ACTIVE',
    last_login    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

**Key decisions:**

| Decision | Reason |
|----------|--------|
| `password_hash` not `password` | Enforces that only BCrypt hashes ever go in this column. The name makes it obvious. |
| No `Customers` in this table | Customers use QR scan → `customer_sessions`. They never have accounts or passwords. |
| `DEFAULT gen_random_uuid()` | UUID is generated at the database layer, not the application layer — consistent regardless of which service creates the row. |
| `status DEFAULT 'ACTIVE'` | New accounts are active immediately. Suspension is an admin action, not the default state. |
| `last_login TIMESTAMPTZ` (nullable) | Null means "never logged in". Updated on every successful authentication. |
| 3 indexes (email, role, status) | Email: login lookup. Role: admin listing owners. Status: filtering suspended accounts. |

---

### Files Created

| File | What it creates |
|------|-----------------|
| `db/migration/V1__create_enums.sql` | 9 PostgreSQL enum types |
| `db/migration/V2__create_users.sql` | `users` table + 3 indexes |

---

### What This Enables

- Flyway will run V1 and V2 automatically on next app startup (once PostgreSQL is running)
- All subsequent table migrations (V3–V11) can reference `user_role`, `user_status`, and the `users` table
- The `User` JPA entity (Day 2) will map to the `users` table that V2 creates

---

---

## 5. User Module — Day 2

**Phase**: Week 1 · Day 2  
**Date**: 2026-07-27  
**Plan task**: Day 2 · Task 2

---

### What Was Done

The `user` package was created with 6 files: two enums, one JPA entity, one repository, one service interface, and one service implementation.

---

### Why a `user/` Folder and Not `controller/`, `service/`, `repository/` Folders?

This is one of the most important structural decisions in the entire project, and it directly affects how maintainable the codebase is as it grows.

#### The two approaches

**Layer-based packaging (what most tutorials show)**
```
com.miniproject.plato/
├── controller/
│   ├── UserController.java
│   ├── RestaurantController.java
│   └── OrderController.java
├── service/
│   ├── UserService.java
│   ├── RestaurantService.java
│   └── OrderService.java
├── repository/
│   ├── UserRepository.java
│   ├── RestaurantRepository.java
│   └── OrderRepository.java
└── model/
    ├── User.java
    └── Restaurant.java
```

**Feature-based packaging (what Plato uses)**
```
com.miniproject.plato/
├── user/
│   ├── User.java
│   ├── UserRepository.java
│   ├── UserService.java
│   └── UserServiceImpl.java
├── restaurant/
│   ├── Restaurant.java
│   ├── RestaurantRepository.java
│   └── RestaurantService.java
└── order/
    ├── Order.java
    └── OrderService.java
```

#### Why feature-based is the right choice here

**1. High cohesion — everything about a feature is in one place.**  
When you need to change how a `User` is created, you open the `user/` folder. You don't hunt across `controller/`, `service/`, `repository/`, and `model/` folders. All the code that belongs together, lives together.

**2. Layer-based collapses under scale.**  
With 11 entities (users, restaurants, tables, employees, menu, orders, etc.), the layer-based approach produces:
- `controller/` with 11 controllers
- `service/` with 22 files (interface + impl per feature)
- `repository/` with 11 repositories
- `model/` with 11 entities

Navigating to `OrderServiceImpl` means going into `service/`, scrolling past every other service. In feature-based, you go straight to `order/`.

**3. The Spring annotations don't care about folder structure.**  
`@Service`, `@Repository`, `@RestController` work the same regardless of which folder the file is in. Spring scans the entire `com.miniproject.plato` package. The folder is for **humans**, not for Spring.

**4. It mirrors the business domain.**  
The business thinks in terms of "users", "restaurants", "orders" — not in terms of "services" and "repositories". Feature-based packaging makes the code structure match the business structure. A new developer reading the codebase understands what the system does just by looking at the folder names.

**5. Package-private encapsulation becomes possible.**  
In layer-based packaging, every class must be `public` because it's accessed from a different package (`UserService` in `service/` is called from `UserController` in `controller/`).  
In feature-based packaging, classes that are only used within a feature can be package-private (no access modifier). For example, `UserServiceImpl` could be package-private — nothing outside `user/` needs to instantiate it directly. Only `UserService` (the interface) needs to be public.

---

### Each File Explained

#### `UserRole.java` — Java Enum

```java
public enum UserRole {
    SUPER_ADMIN, OWNER, EMPLOYEE
}
```

A Java enum that mirrors the `user_role` PostgreSQL enum from `V1__create_enums.sql`. Its only job is to give the Java code type safety — you can never accidentally assign an invalid role string because the compiler prevents it. The same three values must exist in both the Java enum and the PostgreSQL enum, exactly — Hibernate uses the `name()` of each enum constant (e.g. `"OWNER"`) when reading from and writing to the database.

---

#### `UserStatus.java` — Java Enum

```java
public enum UserStatus {
    ACTIVE, SUSPENDED, DELETED
}
```

Same concept as `UserRole`. Maps to `user_status` PostgreSQL enum. `DELETED` is a soft-delete state — the row stays in the database for audit purposes but is treated as inaccessible by the application. No row is ever physically deleted from `users`.

---

#### `User.java` — JPA Entity

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity { ... }
```

This is the Java representation of a row in the `users` table. Every field maps to a column. The annotations tell Hibernate exactly how to translate between Java objects and SQL rows.

Key annotation decisions:

| Annotation | What it does |
|------------|--------------|
| `@Entity` | Marks this class as a JPA-managed object; Hibernate will map it to a table |
| `@Table(name = "users")` | The table is named `users` (not `user`); explicit to avoid surprises |
| `extends BaseEntity` | Inherits `id` (UUID), `createdAt`, `updatedAt` — auto-managed, never set manually |
| `@Enumerated(EnumType.STRING)` | Store enum as its name (e.g. `"OWNER"`) not as an integer index |
| `@Column(columnDefinition = "user_role")` | Tells Hibernate the actual PostgreSQL column type is `user_role` (a custom enum), not plain `VARCHAR` |
| `@Builder.Default` on `status` | Without this, Lombok's `@Builder` ignores field initializers — `status` would be `null` even though the field says `= UserStatus.ACTIVE` |
| `@NoArgsConstructor` | Required by Hibernate — it creates entity instances using a no-arg constructor via reflection |
| `@AllArgsConstructor` | Required by Lombok when both `@Builder` and `@NoArgsConstructor` are present together |

**Why `passwordHash` and not `password`?**  
The column is named `password_hash`, and the Java field is `passwordHash`. This is an explicit design choice. If someone reads the schema or the Java class, the name makes it obvious that what's stored here is a BCrypt hash — never a plain-text password. It's impossible to accidentally confuse the raw password with the stored hash.

---

#### `UserRepository.java` — JPA Repository

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

This interface has no method bodies. Spring Data JPA reads the method names at startup and generates the SQL automatically:
- `findByEmail(email)` → `SELECT * FROM users WHERE email = ?`
- `existsByEmail(email)` → `SELECT COUNT(*) > 0 FROM users WHERE email = ?`

By extending `JpaRepository<User, UUID>`, we also get for free:
- `findById(UUID)`, `findAll()`, `save(User)`, `delete(User)`, `count()`, and more.

Nothing in this interface needs to be written for standard operations. Custom methods are only added when the query cannot be expressed through method naming — in that case, `@Query("SELECT u FROM User u WHERE ...")` is used.

---

#### `UserService.java` — Interface (Contract)

```java
public interface UserService {
    User findById(UUID id);
    User findByEmail(String email);
    boolean existsByEmail(String email);
    User save(User user);
}
```

This is the contract. Any class that needs to work with users depends on this interface, not on the implementation. This matters for three reasons:

1. **Decoupling**: `AuthServiceImpl` (Day 3) will inject `UserService`. If we ever swap the implementation, `AuthServiceImpl` doesn't change.
2. **Testability**: In unit tests, `UserService` can be mocked with Mockito in one line. Testing `AuthServiceImpl` doesn't require a real database.
3. **Clarity**: The interface shows what operations exist on users. It's a readable API surface — you understand what `UserService` can do without reading any implementation code.

---

#### `UserServiceImpl.java` — Implementation

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    ...
}
```

This is where business rules live. Three annotations do the heavy lifting:

**`@Service`** — tells Spring this is a service bean; it gets picked up by component scanning and can be injected anywhere.

**`@RequiredArgsConstructor`** (Lombok) — generates a constructor for all `final` fields. This is constructor injection — the recommended way to inject dependencies in Spring Boot. No `@Autowired` needed. The `UserRepository` is injected automatically when Spring creates this bean.

**`@Transactional(readOnly = true)` on the class** — every method in this class runs in a database transaction. `readOnly = true` is an optimization: Spring tells the database connection pool that this transaction won't write anything, allowing the DB to skip certain locking operations. This is the default for all methods.

**`@Transactional` on `save()`** — overrides the class-level `readOnly = true` for just this method, giving it a writable transaction.

**The duplicate email check in `save()`:**
```java
if (user.getId() == null && userRepository.existsByEmail(user.getEmail())) {
    throw new ConflictException(...);
}
```
The condition `user.getId() == null` distinguishes create from update. On create, the entity hasn't been persisted yet so `id` is null. On update, `id` is already set. This prevents throwing a conflict error when a user updates their own data (they already own their email address).

---

### User API Flow & Workflow

No `UserController` exists yet — that comes later. Here is the full flow once the controller is added and how these pieces connect:

```
HTTP Request
    │
    ▼
[UserController]          — receives HTTP, validates DTO with @Valid, calls service
    │
    ▼
[UserService]             — interface; Spring injects UserServiceImpl
    │
    ▼
[UserServiceImpl]         — applies business rules (conflict check, not-found throw)
    │
    ▼
[UserRepository]          — Spring Data JPA generates SQL, executes via Hibernate
    │
    ▼
[PostgreSQL — users table] — returns result set
    │
    ▲ (back up the chain)
    │
[UserServiceImpl]         — returns User entity
    │
    ▼
[UserController]          — maps entity to DTO, wraps in ApiResponse.ok(...)
    │
    ▼
HTTP Response             — { "success": true, "message": "...", "data": { ... } }
```

#### Concrete example — login flow (Day 3)

When a staff member logs in (`POST /api/v1/auth/login`):

```
1. AuthController receives LoginRequest { email, password }
2. AuthController calls AuthService.login(email, password)
3. AuthServiceImpl calls UserService.findByEmail(email)
       └── UserServiceImpl calls UserRepository.findByEmail(email)
           └── SQL: SELECT * FROM users WHERE email = ?
               └── If not found → ResourceNotFoundException → GlobalExceptionHandler → 404
               └── If found → returns User entity
4. AuthServiceImpl checks user.getStatus() == ACTIVE
       └── If SUSPENDED/DELETED → ValidationException → 403
5. AuthServiceImpl compares raw password with user.getPasswordHash() using BCrypt
       └── If mismatch → ValidationException → 401
6. AuthServiceImpl generates 24h JWT containing user.getId(), user.getRole()
7. AuthServiceImpl calls UserService.save(user.setLastLogin(now()))
       └── SQL: UPDATE users SET last_login = now(), updated_at = now() WHERE id = ?
8. AuthController wraps token in LoginResponse, returns ApiResponse.ok("Login successful", loginResponse)
```

#### Concrete example — get user by ID (admin)

```
1. GET /api/v1/users/{id}
2. JwtAuthenticationFilter reads Authorization: Bearer <token> header
       └── Validates token → extracts userId, role
       └── Sets SecurityContext (Spring Security knows who this is)
3. @PreAuthorize("hasRole('SUPER_ADMIN')") on the controller method blocks non-admins → 403
4. UserController calls UserService.findById(id)
5. UserServiceImpl calls UserRepository.findById(id)
       └── If not found → ResourceNotFoundException → GlobalExceptionHandler → 404
6. Returns User entity → mapped to UserResponse DTO (never return the raw entity with passwordHash)
7. ApiResponse.ok("User found", userResponse) → 200
```

---

### Files Created

| File | Type | Purpose |
|------|------|---------|
| `user/UserRole.java` | Java enum | Maps to `user_role` PostgreSQL enum |
| `user/UserStatus.java` | Java enum | Maps to `user_status` PostgreSQL enum |
| `user/User.java` | JPA entity | Maps to `users` table |
| `user/UserRepository.java` | JPA repository | SQL generated from method names |
| `user/UserService.java` | Interface | Public contract; what other packages depend on |
| `user/UserServiceImpl.java` | Service impl | Business logic, transaction management |

---

### What This Enables

- Day 3's `AuthServiceImpl` can inject `UserService` to look up users during login
- Day 3's `UserDetailsServiceImpl` (Spring Security) will call `UserRepository.findByEmail()` directly to load the user for JWT validation
- The `DataInitializer` (Day 2 Task 3) can call `UserService.save()` to seed the Super Admin
- `@Transactional` boundaries are correctly set — no risk of lazy-loading exceptions outside a transaction

---

---

## 6. Super Admin Seed — Day 2 Task 3

**Phase**: Week 1 · Day 2  
**Date**: 2026-07-28  
**Plan task**: Day 2 · Task 3

---

### What Was Done

Four things were created/modified to implement idempotent super admin seeding on first startup:

1. `plato.seed.admin.*` properties added to `application.yml` and `application-local.yml`
2. `UserRepository` gained a new query method: `existsByRole(UserRole)`
3. `config/AppConfig.java` created with the `PasswordEncoder` bean
4. `user/DataInitializer.java` created as the seeding component

---

### What is DataInitializer?

`DataInitializer` is a class that implements Spring's `CommandLineRunner` interface. Spring Boot automatically calls its `run()` method once, after the entire application context is fully loaded — every time the server starts.

Its job: **check if a Super Admin exists in the database. If not, create one.**

This solves the chicken-and-egg problem: the system needs at least one privileged user to create other users, but there is no way to create that first user through the API because the API requires authentication. The seeder bypasses this by writing directly to the database at startup.

---

### Why `CommandLineRunner` and Not Something Else?

There are several ways to run code at startup in Spring Boot. Here’s why `CommandLineRunner` is the right choice:

| Option | Problem |
|--------|---------|
| `@PostConstruct` on a bean | Runs during bean initialization — Flyway migrations may not have finished yet. Writing to the DB here risks a missing table error. |
| `ApplicationListener<ContextRefreshedEvent>` | Fires on every context refresh, including after hot-reload (DevTools). Would try to seed on every file save in dev. |
| `@EventListener(ApplicationReadyEvent.class)` | Valid alternative — same timing as `CommandLineRunner`. Works equally well. |
| `CommandLineRunner` | Runs **after** all beans are created AND Flyway is done AND the context is fully ready. Clean, simple, idiomatic Spring Boot. |

`CommandLineRunner` wins because it’s guaranteed to run after everything is ready, it’s the most idiomatic interface for "do this once at startup", and it’s trivial to test.

---

### Why is the Check Idempotent?

```java
if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
    log.info("Super Admin already exists — skipping seed.");
    return;
}
```

The server may restart many times during development and in production. Without this guard:
- Every restart would try to `INSERT` a new super admin row
- The second attempt would throw a database constraint violation (email is `UNIQUE`)
- The server would crash on startup

With the guard: the first run creates the admin. Every subsequent run detects it and returns immediately. The database never sees a duplicate insert attempt.

`existsByRole` generates this SQL:
```sql
SELECT COUNT(*) > 0 FROM users WHERE role = 'SUPER_ADMIN'
```
One cheap query, no data transferred, no entity hydration. Fast.

---

### Where Does the Password Go?

```java
.passwordHash(passwordEncoder.encode(adminPassword))
```

The plain-text password (`Admin@1234` in dev) **never touches the database**. `passwordEncoder.encode()` runs BCrypt on it — producing a 60-character hash like:
```
$2a$10$XQCg3zAVFe3vhXNWb0K7fuJd7fNtPh3H1M6v8/sFnGxlqDnRhTm0u
```
That hash is what gets stored in `password_hash`. BCrypt is a one-way function — it cannot be reversed. During login, BCrypt compares the incoming plain-text against the stored hash without ever decrypting it.

---

### Why a Separate `AppConfig.java` for PasswordEncoder?

The `PasswordEncoder` bean needs to be available to `DataInitializer` which runs on Day 2. The full `SecurityConfig` (filter chain, JWT, CORS rules) is a Day 3 concern.

If `PasswordEncoder` was defined inside `SecurityConfig`, then:
- `DataInitializer` (in the `user` package) would depend on `SecurityConfig` (in the `security` package)
- That creates a coupling between unrelated layers at an early stage
- More importantly, defining an incomplete `SecurityConfig` now could break the Spring Security auto-configuration prematurely

By putting `PasswordEncoder` in `config/AppConfig.java`:
- It’s available to any bean that needs it, right now
- Day 3’s `SecurityConfig` can import or inject it from `AppConfig`
- No cross-package coupling introduced before it’s needed

---

### Why `@Value` and Not `@ConfigurationProperties`?

There are two ways to bind properties to a class in Spring Boot:

**`@Value`** — injects individual properties:
```java
@Value("${plato.seed.admin.email}")
private String adminEmail;
```

**`@ConfigurationProperties`** — binds a whole prefix to a POJO:
```java
@ConfigurationProperties(prefix = "plato.seed.admin")
public class SeedProperties {
    private String email;
    private String password;
    private String name;
}
```

`@ConfigurationProperties` is better when:
- You have many related properties
- You want type safety and IDE autocompletion
- You want validation on startup (`@NotBlank`, etc.)

`@Value` is fine when:
- You have only 2–3 values
- The class only uses these values in one method
- You don’t want to create an extra POJO just for a one-time seeder

`DataInitializer` only has 3 properties used in one `run()` method. `@Value` is the appropriate choice here.

---

### Where Do the Credentials Come From?

```
In development:
  application-local.yml (git-ignored) → plato.seed.admin.*
    email:    admin@plato.com
    password: Admin@1234
    name:     Super Admin

In production:
  Environment variables → ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME
  These map to ${ADMIN_EMAIL}, ${ADMIN_PASSWORD}, ${ADMIN_NAME} in application.yml
  The committed application.yml has NO default values for these — the app refuses to start without them
```

This two-tier approach means:
- Local developers don’t need to set environment variables — just use the file
- Production environments never have credentials in any committed file
- Rotating the production password means updating one environment variable, not touching code

---

### Startup Log Output

On **first run** (no Super Admin yet):
```
INFO  DataInitializer : Super Admin seeded successfully.
INFO  DataInitializer : Login email → admin@plato.com
WARN  DataInitializer : Using default seed password. Change it immediately via the admin panel in production.
```

On **every subsequent restart**:
```
INFO  DataInitializer : Super Admin already exists — skipping seed.
```

---

### Files Created / Modified

| File | Type | What changed |
|------|------|--------------|
| `application.yml` | Config | Added `plato.seed.admin.*` block with `${ENV_VAR}` references, no defaults |
| `application-local.yml` | Local config | Added dev credentials (git-ignored) |
| `user/UserRepository.java` | Repository | Added `existsByRole(UserRole)` method |
| `config/AppConfig.java` | Config bean | `PasswordEncoder` bean (BCrypt, 10 rounds) |
| `user/DataInitializer.java` | Component | `CommandLineRunner` implementation, idempotent seeder |

---

### What This Enables

- On every fresh database, there is always one Super Admin to log in with
- No manual SQL inserts needed to bootstrap the system
- The first thing Day 3’s JWT login flow can be tested against is this admin account
- `PasswordEncoder` bean is ready for Day 3’s `AuthServiceImpl` to use for login verification

---
