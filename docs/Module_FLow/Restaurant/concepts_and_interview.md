# Restaurant Module — Concepts, Architecture & Interview Prep

> Comprehensive guide to the Restaurant module's design choices, JPA/Hibernate mechanics, security architecture, and top backend SDE interview questions.

---

## Part 1: File-by-File Technical Breakdown

### 1. `Restaurant.java` — The JPA Entity

**What it is**: The core domain entity representing a physical restaurant. Maps directly to the `restaurants` table in PostgreSQL. Extends `BaseEntity` to inherit UUID primary key (`id`) and JPA auditing timestamps (`createdAt`, `updatedAt`).

**Annotations Breakdown**:
```java
@Entity
// Marks this class as a managed JPA entity mapped to a relational database table.

@Table(name = "restaurants")
// Explicitly specifies the database table name.

@Getter
@Setter
// Lombok: Generates getters and setters for all fields, needed by JPA and serialization.

@NoArgsConstructor
// JPA specification requires a default zero-arg constructor for reflection-based entity instantiation.

@AllArgsConstructor
// Generates full-args constructor, required by Lombok's @Builder.

@Builder
// Enables fluent, type-safe object construction.

@Column(name = "owner_id", nullable = false)
private UUID ownerId;
// Plain UUID column mapping to users(id). 
// WHY NOT @ManyToOne: Avoids lazy/eager loading overhead. We rarely need the full User entity 
// when interacting with a restaurant; a UUID is sufficient for security checks and queries.

@Enumerated(EnumType.STRING)
@Column(nullable = false, columnDefinition = "restaurant_status")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Builder.Default
private RestaurantStatus status = RestaurantStatus.ACTIVE;
// Maps custom PostgreSQL ENUM type ('restaurant_status'). 
// @JdbcTypeCode(SqlTypes.NAMED_ENUM) instructs Hibernate 6 to bind the parameter as a named enum 
// rather than VARCHAR, preventing PostgreSQL type mismatch errors.
// @Builder.Default ensures that Restaurant.builder().build() retains status = ACTIVE if unspecified.

@Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
@Builder.Default
private BigDecimal taxPercentage = BigDecimal.ZERO;
// precision = 5, scale = 2 allows values up to 999.99 with exact 2 decimal places.
```

---

### 2. `RestaurantStatus.java` — The Lifecycle Enum

**What it is**: Defines all permissible operational states of a restaurant:
- `ACTIVE`: Normal operations, visible to customers, accepting orders.
- `INACTIVE`: Hidden from customers; owner can still manage menu/settings.
- `SUSPENDED`: Admin-blocked due to policy or compliance violation.

**Why matching PostgreSQL Enum matters**:
The database defines `CREATE TYPE restaurant_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED')`. The Java enum constants must match the casing and spelling exactly.

---

### 3. `V3__create_restaurants.sql` — Flyway Schema Migration

**What it is**: Version-controlled DDL script executed once by Flyway at application startup.

**Key Database Constraints & Indexes**:
```sql
CREATE TABLE restaurants (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID            NOT NULL REFERENCES users(id),
    ...
    status              restaurant_status NOT NULL DEFAULT 'ACTIVE',
    tax_percentage      NUMERIC(5,2)    NOT NULL DEFAULT 0.00,
    service_charge      NUMERIC(5,2)    NOT NULL DEFAULT 0.00,
    allow_cash_payment  BOOLEAN         NOT NULL DEFAULT true,
    allow_card_payment  BOOLEAN         NOT NULL DEFAULT true,
    allow_upi           BOOLEAN         NOT NULL DEFAULT true,
    allow_online_payment BOOLEAN        NOT NULL DEFAULT false,
    accepting_orders    BOOLEAN         NOT NULL DEFAULT true,
    auto_accept_orders  BOOLEAN         NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Index for owner-filtered queries
CREATE INDEX idx_restaurants_owner_id ON restaurants(owner_id);

-- Index for admin status-filtered queries
CREATE INDEX idx_restaurants_status ON restaurants(status);
```

**Design Highlight**:
- `idx_restaurants_owner_id` turns `findByOwnerId` queries from an $O(N)$ full table scan into an $O(\log N)$ B-Tree index scan.

---

### 4. `RestaurantRepository.java` — Spring Data JPA Layer

**What it is**: Data access interface extending `JpaRepository<Restaurant, UUID>`.

```java
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    Page<Restaurant> findByOwnerId(UUID ownerId, Pageable pageable);
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
```

**Derived Query Mechanism**:
- `findByOwnerId`: Spring Data parses the method name and generates `SELECT * FROM restaurants WHERE owner_id = ? ORDER BY ... LIMIT ? OFFSET ?`.
- `existsByIdAndOwnerId`: Generates `SELECT COUNT(*) > 0 FROM restaurants WHERE id = ? AND owner_id = ?`. Returns a lightweight boolean without fetching entity rows into memory.

---

### 5. DTO Records (`CreateRestaurantRequest`, `UpdateRestaurantRequest`, `RestaurantSettingsRequest`, `RestaurantResponse`)

**Why Java `record` for Inbound DTOs**:
1. **Immutability**: Records are shallowly immutable; fields are `final`.
2. **Concise Syntax**: Boilerplate-free declaration of fields, canonical constructor, accessors, `equals()`, `hashCode()`, and `toString()`.
3. **Security**: Request bodies cannot be mutated once instantiated by the HTTP message converter.

**Validation Annotations Used**:
- `@NotBlank`: Ensures string is neither `null` nor empty whitespace.
- `@Email`: Validates RFC 5322 email syntax.
- `@DecimalMin("0.00")`: Ensures tax and service charge percentages cannot be negative.

---

### 6. `RestaurantMapper.java` — In-Memory Transformation & In-Place Mutation

**What it is**: Spring `@Component` that isolates all transformation logic between entities and DTOs.

```java
@Component
public class RestaurantMapper {
    public RestaurantResponse toResponse(Restaurant restaurant) { ... }
    public Restaurant toEntity(CreateRestaurantRequest request, UUID ownerId) { ... }
    public void applyUpdate(Restaurant restaurant, UpdateRestaurantRequest request) { ... }
    public void applySettings(Restaurant restaurant, RestaurantSettingsRequest request) { ... }
}
```

**Design Benefits**:
- **Separation of Concerns**: Service layer focuses on business workflows and security; it never contains manual property-copying boilerplate.
- **In-Place Mutation (`applyUpdate` & `applySettings`)**: Selectively applies non-null fields onto an existing managed entity, enabling clean partial updates with Hibernate dirty checking.

---

### 7. `RestaurantService.java` & `RestaurantServiceImpl.java` — Business Logic

**Architecture**:
- `RestaurantService`: Interface defining the public capability contract.
- `RestaurantServiceImpl`: Implementation marked with `@Service`, `@RequiredArgsConstructor`, and `@Transactional(readOnly = true)`.

**Transaction Strategy**:
- Class-level `@Transactional(readOnly = true)`: Optimizes read operations by disabling Hibernate dirty-check snapshot caching and setting JDBC connections to read-only where supported.
- Method-level `@Transactional` on write methods (`createRestaurant`, `updateRestaurant`, `updateSettings`, `updateStatus`, `deleteRestaurant`): Opens read-write transactions, committing changes upon normal completion and rolling back on runtime exceptions.

---

### 8. `RestaurantController.java` — REST Endpoints & Security

**What it is**: Exposes REST endpoints at `/api/v1/restaurants`.

**Key Responsibilities**:
1. Declares routing and HTTP verbs (`@PostMapping`, `@GetMapping`, `@PutMapping`, `@PatchMapping`).
2. Enforces method-level RBAC via `@PreAuthorize`.
3. Extracts authenticated caller identity (`getCurrentUserId()`) and role (`getCurrentRole()`) from `SecurityContextHolder`.
4. Delegates to `RestaurantService` and wraps outcomes in uniform `ApiResponse.ok(...)`.

---

## Part 2: Deep-Dive Architectural Concepts

```
                  ┌─────────────────────────────────────────────────────────┐
                  │                 JWT (Bearer Token)                      │
                  │  sub: "bd82c11a-..." (UUID)   role: "OWNER"             │
                  └────────────────────────────┬────────────────────────────┘
                                               │
                                               ▼
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                JwtAuthenticationFilter                                    │
│  Extracts sub -> Principal: "bd82c11a-..."                                                │
│  Extracts role -> SimpleGrantedAuthority: "ROLE_OWNER"                                    │
│  Sets Authentication in SecurityContextHolder                                             │
└──────────────────────────────────────────────┬────────────────────────────────────────────┘
                                               │
                                               ▼
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                 RestaurantController                                      │
│  @PreAuthorize("hasRole('OWNER')")  <-- Verified by Spring Security                       │
│  getCurrentUserId() -> Extracts UUID from Principal                                       │
│  Passes ownerId explicitly down to Service                                                │
└──────────────────────────────────────────────┬────────────────────────────────────────────┘
                                               │
                                               ▼
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                  RestaurantService                                        │
│  Ownership Check: if (!restaurant.getOwnerId().equals(callerId)) -> 403 Forbidden        │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

### Concept 1: Multi-Tenant Owner Isolation Pattern
- **Problem**: In a multi-tenant SaaS application, Owner A must never be able to view, edit, or delete Owner B's restaurant.
- **Vulnerability to Avoid**: Never trust client-supplied IDs in request bodies (e.g. `{"ownerId": "victim-uuid"}`). An attacker could impersonate other owners.
- **Our Solution**:
  1. The `ownerId` is extracted strictly from the cryptographically signed JWT in `SecurityContextHolder`.
  2. For write operations, the service verifies `restaurant.getOwnerId().equals(ownerId)`. If mismatched, it throws `UnauthorizedAccessException` (HTTP 403).
  3. For list operations, owners query `findByOwnerId(callerId)` while Super Admins query `findAll()`.

---

### Concept 2: Embedded Settings vs Separate Table
- **Tradeoff Analysis**:
  - *Option A (Normalized)*: Separate `restaurant_settings` table linked via foreign key.
    - *Cons*: Requires an extra SQL `JOIN` on every restaurant fetch, extra migration, and separate entity lifecycle.
  - *Option B (Embedded Columns in `restaurants` Table)* — **Our Choice**:
    - *Pros*: Zero join overhead. Settings (tax, payment toggles, order toggles) are loaded in the same query.
    - *Cons*: Slightly wider table row, but easily fits within PostgreSQL 8KB page limit.
- **Dedicated Settings Endpoints**: Even though data lives in one table, we provide dedicated `/settings` endpoints (`GET` and `PUT`) so frontends can build independent "Settings" dashboard pages without fetching or resending full business identity/address data.

---

### Concept 3: Hibernate Dirty Checking Mechanics
When updating an entity in a `@Transactional` method:
1. `findById(id)` loads the entity into the Hibernate **Persistence Context** (First-Level Cache).
2. Hibernate takes an in-memory **snapshot** of the entity's current property values.
3. The application mutates the entity in memory (e.g., `restaurant.setName("New Name")`).
4. When the method finishes, the Spring transaction interceptor attempts to commit the transaction.
5. Hibernate performs **dirty checking**: it compares the managed entity's current state with the initial snapshot.
6. If differences exist, Hibernate generates and flushes an SQL `UPDATE` statement before transaction commit.
7. **Takeaway**: Explicit calls to `repository.save(entity)` during updates are redundant and unnecessary in managed JPA transactions.

---

### Concept 4: `BigDecimal` vs `Double`/`Float` for Monetary Calculations
- Floating-point types (`double`, `float`) adhere to IEEE 754 standard (binary fractions). Certain base-10 decimals cannot be represented exactly:
  ```java
  double a = 0.1;
  double b = 0.2;
  System.out.println(a + b); // Prints: 0.30000000000000004
  ```
- In restaurant billing, tax percentages (5.00%), service charges (2.50%), and order amounts must be exact.
- `BigDecimal` stores values as an arbitrary precision integer unscaled value + a 32-bit integer scale, eliminating floating-point rounding errors.

---

### Concept 5: Spring Security Role vs Authority Prefix Rules
- `@PreAuthorize("hasRole('OWNER')")`:
  - Spring automatically prepends `ROLE_` to the argument, evaluating whether the user possesses authority `ROLE_OWNER`.
  - Writing `hasRole('ROLE_OWNER')` evaluates to `ROLE_ROLE_OWNER`, causing unexpected 403 Forbidden errors.
- `@PreAuthorize("hasAuthority('ROLE_OWNER')")`:
  - Evaluates the exact authority string without prepending any prefix.

---

## Part 3: SDE Interview Questions & Model Answers

### Question 1: How do you enforce multi-tenancy and data isolation between different restaurant owners in a REST API?
**Answer**:
"We implement multi-tenant data isolation at three distinct layers:
1. **Authentication / Token Extraction**: The caller's identity (`UUID`) and role are extracted from the cryptographically verified JWT inside the `JwtAuthenticationFilter` and stored in `SecurityContextHolder`. We never accept an `ownerId` from the client request body.
2. **Method-Level Authorization**: Endpoints are guarded with `@PreAuthorize("hasRole('OWNER')")` to restrict access by role.
3. **Service-Layer Ownership Validation**: For resource-specific operations (`PUT /api/v1/restaurants/{id}`), the service retrieves the entity and compares `restaurant.getOwnerId()` with the caller's UUID from the JWT. If they do not match, it throws an `UnauthorizedAccessException` which maps to HTTP 403 Forbidden. For list queries, we execute tenant-scoped queries like `findByOwnerId(callerId)` so owners only ever receive their own records."

---

### Question 2: What is Hibernate dirty checking, and why didn't we call `repository.save()` inside `updateRestaurant()`?
**Answer**:
"Inside a method annotated with `@Transactional`, entities retrieved via Spring Data JPA are placed in Hibernate's Persistence Context (1st-level cache) in a *managed* state. 

When loaded, Hibernate retains an internal snapshot copy of the entity's initial field values. When we mutate the entity using setters (or our mapper's `applyUpdate` method), the entity in the persistence context changes.

At the end of the transaction, Hibernate flushes the session. It compares the current state of every managed entity against its initial snapshot. Upon detecting modified fields, it automatically constructs and executes an optimized SQL `UPDATE` statement. Calling `repository.save()` is redundant because Hibernate already tracks and flushes all managed entity modifications upon transaction commit."

---

### Question 3: Why did we choose `BigDecimal` for `tax_percentage` and `service_charge` instead of `double` or `float`?
**Answer**:
"`double` and `float` use IEEE 754 binary floating-point representation, which cannot accurately represent fractional decimal numbers like 0.1 or 0.05. Over repeated additions, tax computations, or discounts, accumulated rounding errors cause financial discrepancies.

`BigDecimal` provides exact decimal representation with arbitrary precision and user-controlled rounding modes. In our schema and entity, we define `precision = 5, scale = 2` (supporting values up to 999.99 with exact two decimal places), guaranteeing exact precision for tax and charge calculations."

---

### Question 4: What is the difference between `@Transactional(readOnly = true)` at the class level and `@Transactional` on a method?
**Answer**:
"Applying `@Transactional(readOnly = true)` at the class level sets a safe, performant default for all query methods:
1. **Performance Optimization**: Hibernate disables dirty-check snapshot creation for entities loaded in a read-only transaction, reducing memory consumption and CPU overhead during flush.
2. **Database Driver Optimization**: The underlying JDBC connection and database engine can route queries to read-replicas and avoid acquiring write locks.

When a modifying method (e.g., `createRestaurant`, `updateRestaurant`) executes, its method-level `@Transactional` annotation overrides the class default, opening a read-write transaction that enables dirty checking and writes to the primary database."

---

### Question 5: Why did we embed restaurant settings directly into the `restaurants` table instead of creating a separate `restaurant_settings` table?
**Answer**:
"We evaluated normalization vs. query efficiency:
- Settings (tax %, service charge, payment toggles, order flags) have a strict 1-to-1 lifecycle with the restaurant. A restaurant cannot exist without settings, and settings never exist independently.
- In 99% of access patterns (customer menu browsing, order placement, checkout calculation), restaurant metadata and its operational settings are required simultaneously.
- Embedding settings as columns in the `restaurants` table eliminates an unnecessary SQL `JOIN` on every request. Because the table width remains under 30 columns, it easily fits within PostgreSQL's standard 8KB page size, yielding superior cache locality and read performance."

---

### Question 6: What is the difference between `@PreAuthorize("hasRole('OWNER')")` and `@PreAuthorize("hasAuthority('ROLE_OWNER')")`?
**Answer**:
"Both enforce the same authorization rule, but handle prefixes differently:
- `hasRole('X')` is a convenience helper in Spring Security that automatically prepends the default prefix `ROLE_`, evaluating whether the user has the authority `ROLE_X`.
- `hasAuthority('X')` performs an exact match against the `GrantedAuthority` string without appending any prefix.
- If you write `hasRole('ROLE_OWNER')`, Spring evaluates it as `ROLE_ROLE_OWNER`, causing authorization to fail with HTTP 403 Forbidden even if the user has `ROLE_OWNER`."

---

### Question 7: Why is soft delete preferred over hard delete (`DELETE FROM ...`) for restaurants in an enterprise food delivery / POS system?
**Answer**:
"Hard deletion (`DELETE FROM restaurants WHERE id = ...`) creates severe data integrity and compliance issues:
1. **Foreign Key Violations**: Existing orders, payment receipts, customer session logs, invoices, and analytics tables reference `restaurant_id`. Deleting the row breaks foreign keys or cascades deletes across historical financial data.
2. **Financial Auditing**: Tax authorities and accounting teams require historical transaction records to remain verifiable.
3. **Reactivation**: If a suspended or temporarily closed restaurant returns to the platform, soft deletion (`status = 'INACTIVE'`) allows simple reactivation with historical menu and configuration data intact."
