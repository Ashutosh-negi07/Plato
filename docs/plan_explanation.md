# Plato — Plan Explanation

> This document explains the **why, what, and how** behind every major decision in the development plan.  
> Read this alongside `plan.md`. This is the reasoning — that is the checklist.

---

## Table of Contents

1. [The Core Idea — What Are We Actually Building?](#1-the-core-idea)
2. [Why a Layered Architecture?](#2-why-layered-architecture)
3. [What Is Flyway and Why Do We Need It?](#3-what-is-flyway)
4. [The Two Types of Users — Auth Design](#4-auth-design)
5. [Why 4 Phases? Why This Order?](#5-why-this-order)
6. [Week 1 — Every Decision Explained](#6-week-1-deep-dive)

---

---

# 1. The Core Idea

Before anything technical, understand what Plato actually is at its core.

**Plato is a multi-tenant SaaS platform.**

Multi-tenant means one codebase, one database, one running server — but it serves **many restaurants at the same time**, each completely isolated from the others.

Restaurant A's customers can never see Restaurant B's orders. Restaurant B's employees can never access Restaurant A's menu. This isolation must be enforced in code at every level — not just in the database.

The second core idea: **customers never log in**.

This is unusual. Most systems have user accounts. Plato doesn't for customers. Instead, a customer scans a QR code, a temporary session is created in the database, and they get a token tied to that session. All their actions — browsing the menu, adding to cart, placing orders — are associated with that session. When they leave and the session expires, that is it. No account, no password, no registration.

This simplifies the customer experience dramatically (just scan and order) and simplifies our auth system (no need for customer password management).

---

---

# 2. Why Layered Architecture?

The backend follows a strict 4-layer pattern:

```
HTTP Request
     ↓
Controller     ← Receives request, validates input, calls service
     ↓
Service        ← All business logic lives here
     ↓
Repository     ← Talks to the database
     ↓
Database (PostgreSQL)
```

### Why not put everything in one place?

Because as the system grows, mixing concerns creates unmaintainable code.

**Without layers (bad):**

```java
// Controller doing everything — a disaster at scale
@PostMapping("/orders")
public OrderResponse placeOrder(...) {
    // validate session
    // check if restaurant is open
    // loop through cart items
    // calculate tax
    // save order to DB
    // send WebSocket notification
    // return response
}
```

This controller becomes 300 lines. You cannot test individual parts. Any change breaks multiple things.

**With layers (good):**

```java
// Controller — thin, just delegates
@PostMapping("/orders")
public ApiResponse<OrderResponse> placeOrder(@SessionContext session) {
    return ApiResponse.ok(orderService.placeOrder(session.getId()));
}

// Service — all logic here, testable in isolation
@Transactional
public OrderResponse placeOrder(UUID sessionId) {
    // all the logic here — but now testable, reusable, readable
}
```

**The rules:**
- **Controllers** only validate input and call services. Zero business logic.
- **Services** contain all business rules. They call repositories. They talk to other services if needed.
- **Repositories** only do database queries. No calculations, no decisions.
- **Entities** are just data — they map to database tables. No behavior.
- **DTOs** are what you send and receive over HTTP. Entities are never exposed directly.

---

---

# 3. What Is Flyway?

## The Problem Flyway Solves

When you build a Spring Boot application, Hibernate can auto-create your database tables by looking at your `@Entity` classes. You set `spring.jpa.hibernate.ddl-auto: create` and it creates everything for you.

This works fine on your laptop.

But in production:
- You already have data in the database
- If you change an entity and restart the app, Hibernate might drop and recreate the table — deleting all your data
- Multiple developers making changes at different times will conflict
- You have no history of what changed and when
- You cannot roll back a bad schema change

**Flyway is a database migration tool.** It solves all of these problems.

## How Flyway Works

You write plain SQL files. Each file is a **migration** — a specific, numbered change to the database schema.

```
src/main/resources/db/migration/
├── V1__create_enums.sql
├── V2__create_users.sql
├── V3__create_restaurants.sql
...
```

When your Spring Boot app starts, Flyway:
1. Connects to the database
2. Checks a special table called `flyway_schema_history`
3. Sees which migrations have already been applied
4. Runs any new migrations that have not been applied yet
5. Records them in `flyway_schema_history`

This means:
- **First run**: Flyway runs V1, V2, V3... all of them. Tables get created.
- **Second run**: Flyway sees everything is already applied. Does nothing.
- **After adding V4**: Flyway runs only V4. Existing data is safe.

## Why `ddl-auto: validate`?

In `application.yml`:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

`validate` means: Hibernate looks at your entities and checks if the database tables match. If they do not match, the app refuses to start. This is a safety net — it tells you immediately if your entity and your migration are out of sync.

It does **not** create, alter, or drop anything. Flyway is the only thing that touches the schema.

## The Golden Rule of Flyway

> **Never modify a migration file that has already been applied.**

If you need to change the schema, add a new migration file. If you modify an existing one, Flyway will detect the checksum mismatch and refuse to start. This is intentional — it protects you from inconsistent states across environments.

---

---

# 4. Auth Design — Two Types of Users, Two Different Systems

This is one of the most important design decisions in Plato.

## Who Needs to Authenticate?

| Person | Auth Method | Why |
|--------|------------|-----|
| Super Admin | Email + Password → JWT | Manages the platform |
| Owner | Email + Password → JWT | Manages their restaurants |
| Employee (Manager/Chef/Waiter/Cashier) | Email + Password → JWT | Works in a restaurant |
| **Customer** | **No login. Ever.** | Just scans QR, gets a session token |

## Why JWT for Staff?

**JWT = JSON Web Token.**

When a staff member logs in with their email and password:

1. The backend verifies the password using BCrypt
2. Generates a JWT — a signed, encoded token containing the user ID, role, and expiry
3. Returns the token to the client (the dashboard app)
4. The client stores it and sends it in every future request: `Authorization: Bearer <token>`
5. The backend reads the token, verifies the signature, extracts the user — **no database lookup needed**

This makes the backend **stateless** for staff. The server does not remember who is logged in. The token itself carries the information. Any server instance can verify any token. This is why horizontal scaling works.

**Why 24 hours?** We are not implementing refresh tokens for this project. A 24-hour expiry means staff log in once per day. Simple. No extra complexity, no extra table.

## Why Not JWT for Customers?

Customers do not have accounts. There is nothing to log in as. Instead:

1. Customer scans QR code (e.g., `https://plato.app/qr/7Kd92abLm`)
2. The frontend calls `GET /api/v1/qr/7Kd92abLm`
3. The backend looks up the table, creates a `customer_sessions` row, generates a random `session_token`
4. The session token is returned to the customer's browser
5. Every subsequent customer request includes: `X-Session-Token: <session_token>`
6. A `CustomerSessionFilter` intercepts these requests and validates the token against the database

The key difference:
- **JWT** — the token itself contains all info (self-contained)
- **Session token** — the token is just a key, the real data is in the database

Customer sessions are stored in PostgreSQL. Their expiry (`expires_at`) is updated on every request (sliding expiry). After 30 minutes of inactivity, the session expires and no more orders can be placed.

## Why BCrypt for Passwords?

BCrypt is a slow hashing algorithm designed specifically for passwords.

- It is one-way — you can never recover the original password from the hash
- It is slow by design — makes brute-force attacks extremely expensive
- Each hash includes a random "salt" — so two users with the same password get completely different hashes

When an employee tries to log in:

```
Provided password → BCrypt → compare with stored hash
```

If they match, authenticated. The original password is never stored or transmitted internally.

## Why No Refresh Tokens?

Refresh tokens add significant complexity:
- A separate table to store them
- An endpoint to exchange a refresh token for a new access token
- Logic to rotate tokens, detect token reuse, revoke on logout
- All of this has to be done correctly or it becomes a security vulnerability

For a project at this stage, a 24-hour JWT is a reasonable trade-off. Staff log in once per day. If a token is stolen, it expires in 24 hours. The attack window is limited.

When would you add refresh tokens? When you need very short-lived access tokens (5-15 minutes) for high-security scenarios, the ability to immediately revoke access for a suspended employee, or "remember me for 30 days" functionality. None of these are critical for the MVP.

---

---

# 5. Why 4 Phases? Why This Order?

The 4 phases are not arbitrary. Each one depends on the previous one.

```
Phase 1 (Week 1): Foundation + Auth
       ↓
Phase 2 (Week 2): Restaurant Management
       ↓
Phase 3 (Week 3): Customer Flow
       ↓
Phase 4 (Week 4): Real-time + Testing + Deploy
```

## Why Foundation First (Week 1)?

Every feature built in Weeks 2, 3, and 4 depends on the things built in Week 1:

- **`ApiResponse<T>`** — every endpoint returns this. You must define it before writing any controllers.
- **`GlobalExceptionHandler`** — every error flows through this. Must exist before features throw errors.
- **`BaseEntity`** — all entities extend this for `id`, `createdAt`, `updatedAt`. Exists before any entities.
- **Spring Security + JWT** — every protected endpoint uses this. Must be wired before building protected controllers.
- **Flyway** — must be set up before any tables can exist.
- **Users table** — almost every table references `users`. It must come first.

If you build the restaurant module first without common infrastructure, you spend Week 2 retrofitting it.

## Why Restaurant Management Before Customer Flow (Week 2 before Week 3)?

Because a customer session requires a restaurant and a table to exist first.

`customer_sessions` has foreign keys to `restaurants` and `restaurant_tables`. Those tables must exist before sessions can be created. Similarly, `cart_items` references `menu_items`, which belongs to a restaurant.

The database foreign key order **dictates the build order**:

```
users → restaurants → restaurant_tables → employees → menu_items
                                                          ↓
                                         customer_sessions → cart_items → orders → payments → feedback
```

You cannot create a session before you have a restaurant. You cannot add to cart before you have a menu. The hierarchy of the data determines the hierarchy of development.

## Why Real-time and Testing Last (Week 4)?

**WebSockets** wire into existing services. When `OrderServiceImpl.placeOrder()` runs, it publishes a WebSocket event. You need `OrderServiceImpl` to exist before you can wire notifications into it.

**Tests** need real code to test. You write meaningful tests after you understand how the code behaves.

**Deployment** needs a complete, tested application. Docker needs working code. You cannot deploy what does not exist.

---

---

# 6. Week 1 — Every Decision Explained

Week 1 is the most important week. Everything else is built on top of it.

---

## Day 1 — Project Setup & Infrastructure

### Fix the Package Name

The current `PlatoApplication.java` has package `com.miniProject.Plato`. The `pom.xml` now says `groupId = com.miniproject`. These must match exactly. Spring Boot scans from the package of `PlatoApplication` — if the package is wrong, it cannot find your other classes.

### Why `application.yml` Instead of `application.properties`?

Both work. YAML is hierarchical and more readable for grouped settings:

```yaml
plato:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000
  session:
    timeout-minutes: 30
```

Easier to see the grouping compared to flat `.properties` format.

### Why `${DB_PASSWORD}` Instead of the Actual Password?

```yaml
spring.datasource.password: ${DB_PASSWORD}
```

This reads the value from an environment variable. The actual password is never written in the source file.

Source code goes into Git. If you write your real password in `application.yml` and push to GitHub, your password is now public forever (even if you delete the file later, Git history keeps it). Environment variables are set on the machine running the app — not in the codebase.

### The `common` Package — Why Build This First?

Every response your API returns will use `ApiResponse<T>`. Every entity will extend `BaseEntity`. If you start building controllers without these, you return raw objects from every endpoint and then have to refactor all of them later.

Build the shared tools before building the features that use them.

**`ApiResponse<T>` — Why a generic wrapper?**

```json
Without wrapper — inconsistent, hard for frontend to handle:
{ "id": "abc-123", "name": "Pizza" }

With ApiResponse — consistent, predictable:
{
  "success": true,
  "message": "Menu item found",
  "data": { "id": "abc-123", "name": "Pizza" }
}
```

Every endpoint returning the same shape means the frontend only handles one response format. Errors are always `"success": false` with a message. The frontend can have one interceptor that handles all error responses.

**`BaseEntity` — Why an abstract class?**

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

Every entity in the system needs `id`, `createdAt`, `updatedAt`. Instead of writing these 3 fields in 11 entity classes, you write it once and every entity extends `BaseEntity`. DRY principle.

### The `exception` Package — Why Custom Exceptions?

Without custom exceptions:

```java
throw new RuntimeException("Restaurant not found");
```

The `GlobalExceptionHandler` cannot distinguish this from any other runtime error. It returns 500 for everything.

With custom exceptions:

```java
throw new ResourceNotFoundException("Restaurant", restaurantId);
```

The handler catches `ResourceNotFoundException` specifically and returns a 404 with a clean message. `SessionExpiredException` returns 401. `ConflictException` returns 409. Each error type gets the correct HTTP status automatically, defined in one central place.

### The First Flyway Migration — `V1__create_enums.sql`

PostgreSQL enums must be created before any table that uses them. Since `users` needs `user_role` and `user_status`, those enums must exist before `V2__create_users.sql` runs. This is also a clean place to define all status values in one file so you have a single reference.

---

## Day 2 — Users Table

### Why UUID Primary Keys?

```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid()
```

**Auto-increment integers** (1, 2, 3...): Predictable. A user can guess that the next restaurant ID is 5. They can probe your API by iterating through IDs. Also causes conflicts in distributed systems.

**UUID**: `7a3f9c21-4b2e-41d8-a6f1-9c3e85d2b0e4` — not guessable. Globally unique. Works across multiple databases and services.

`gen_random_uuid()` is PostgreSQL's built-in function for cryptographically random UUIDs. The database generates them automatically on INSERT.

### Why Only Platform Users in This Table?

Customers do not have rows in `users`. This is a deliberate design choice.

If customers were in `users`, the table would have millions of rows over time. You would need to distinguish real users from customers with a flag, which complicates every query and every authorization check.

More importantly, customers do not have identities in this system. They are represented by sessions, not accounts. Keeping them out of `users` keeps the table clean and the auth system simple.

### Why Seed a Super Admin?

The system needs at least one Super Admin to do anything — create owners, manage restaurants. But you cannot use the API to create a Super Admin because the API requires you to already be a Super Admin to create one. Classic chicken-and-egg problem.

The `DataInitializer` solves this by checking on startup whether a super admin exists. If not, it creates one with a default password. You log in, change the password, and then use the platform normally.

---

## Day 3 — Spring Security & JWT

### Why Spring Security Instead of a Simple Filter?

You could write a simple filter that checks a token header. Why use the full Spring Security framework?

1. It integrates with Spring everywhere — `@PreAuthorize("hasRole('OWNER')")` just works because Spring Security is wired into the application context
2. It handles edge cases — CORS, CSRF, session management, filter ordering
3. It is extensible — adding OAuth or 2FA later is straightforward
4. It is battle-tested — used in production by millions of applications

### Why `OncePerRequestFilter`?

In a Spring Boot app, HTTP requests go through a chain of filters before reaching your controller. `OncePerRequestFilter` guarantees your filter runs **exactly once per request**, even if the request is forwarded internally. Without this guarantee, a filter could run twice on the same request.

### How the JWT Filter Works

```
Incoming Request
      ↓
JwtAuthenticationFilter
      ↓
Read "Authorization: Bearer <token>" header
      ↓
If no header → continue chain (unauthenticated)
      ↓
Validate JWT signature + expiry
      ↓
Extract user ID from JWT claims
      ↓
Load UserDetails from database
      ↓
Set authentication in SecurityContextHolder
      ↓
Continue to Controller (Spring now knows who this is)
```

Every subsequent `@PreAuthorize` check uses what this filter set.

### Why `UserDetailsServiceImpl`?

Spring Security needs to know how to load a user. It defines the `UserDetailsService` interface with one method: `loadUserByUsername(String username)`. We implement this to load our `User` entity by email. This is the bridge between Spring Security's generic concept of a user and our specific `User` entity.

---

## Day 4 — Global Exception Handler & Response Standards

### Why a Global Exception Handler?

Without one, Spring Boot returns its default error response:

```json
{
  "timestamp": "2026-07-26T08:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/orders"
}
```

This reveals internal details and is inconsistent with your `ApiResponse` format.

With `@RestControllerAdvice`, you catch every exception type and return exactly what you want, in the format you define, with the correct HTTP status code.

### Why Build This Before Any Features?

The moment you write your first service method and it throws an exception, the response format needs to be correct. If you build 10 endpoints and then add the exception handler, you find that exceptions were being swallowed or returned incorrectly. Build the handler first.

### Swagger — Why and When?

Swagger (via springdoc-openapi) generates interactive API documentation automatically from your controller annotations. During development, Swagger UI lets you test endpoints directly in the browser without Postman. You can also test JWT auth by pasting your token into the Swagger auth dialog.

In production, Swagger should be protected or disabled. You do not want the world seeing your full API spec.

---

## Day 5 — User Management

### Why Complete User Management in Week 1?

The Super Admin needs to create Owners. The `UserController` provides the CRUD surface for this. It is also the first complete implementation of the full stack — entity, repository, service, controller, DTOs, mapper, auth — which serves as the **template** for every subsequent module.

Getting one module completely right in Week 1 means you have a reference implementation to follow for all the others.

### Why `@PreAuthorize` Instead of Security Config Rules?

You could define security rules centrally in `SecurityConfig`:

```java
.requestMatchers("/api/v1/users").hasRole("SUPER_ADMIN")
```

But as endpoints grow, this central list becomes hundreds of lines. With `@PreAuthorize`:

```java
@GetMapping
@PreAuthorize("hasRole('SUPER_ADMIN')")
public ApiResponse<Page<UserResponse>> getAllUsers(...) { ... }
```

The rule is right next to the endpoint it protects. You can see at a glance who can access what. Enable with `@EnableMethodSecurity` in `SecurityConfig`.

---

## Summary: What Week 1 Gives You

By the end of Day 5, you have:

```
✓ A running Spring Boot application connected to PostgreSQL
✓ Flyway managing the schema (V1 and V2 applied)
✓ A consistent API response format every endpoint will use
✓ A central exception handler every error will flow through
✓ A users table with proper indexing and BCrypt password storage
✓ Spring Security wired up with 24h JWT for staff
✓ A working login endpoint
✓ User management (CRUD) for the Super Admin
✓ The complete module pattern established (entity → repository → service → controller → DTO)
```

Every module built in Weeks 2, 3, and 4 follows the same pattern. Week 1 defines what that pattern is.

---

> Continue reading `plan.md` for the day-by-day implementation checklist of Weeks 2, 3, and 4.
