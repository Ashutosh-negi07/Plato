# Table Module — Complete Data Flow

> Every HTTP request through the Table module traced step by step.
> Line numbers reference the actual files in the codebase.

---

## Architecture Overview

### Forward Journey
```
HTTP Request (Client / Postman / Frontend)
     │
     ▼
JwtAuthenticationFilter.java        — extracts JWT, verifies signature, populates SecurityContext
     │
     ▼
SecurityConfig.java / @PreAuthorize — checks role (OWNER / SUPER_ADMIN)
     │
     ▼
TableController.java                — receives HTTP, extracts principal UUID & role, calls service
     │
     ▼
TableService.java                   — interface (the contract)
     │
     ▼
TableServiceImpl.java               — business logic, ownership checks, QR generation, calls mapper & repository
     │
     ├── QrTokenService.java         — generates cryptographically secure 64-char hex token (createTable / regenerateQrToken only)
     │
     ▼
TableMapper.java                    — converts Request DTO ↔ Entity ↔ Response DTO
     │
     ▼
TableRepository.java                — executes SQL via Spring Data JPA / Hibernate
     │
     ▼
restaurant_tables (PostgreSQL)      — persists row data
```

### Reverse Journey
```
PostgreSQL returns row(s)
     │
     ▼
TableRepository returns RestaurantTable, Optional<RestaurantTable>, or List<RestaurantTable>
     │
     ▼
TableServiceImpl receives the managed entity
     │
     ▼
TableMapper converts RestaurantTable entity → TableResponse DTO
     │
     ▼
TableServiceImpl returns TableResponse / List<TableResponse> to Controller
     │
     ▼
TableController wraps payload in ApiResponse.ok("...", data)
     │
     ▼
Jackson serializes ApiResponse to JSON → HTTP 200/201 Response sent to Client
```

---

## File Map — Every File in the Module

| File | Package | Purpose |
|---|---|---|
| `V4__create_restaurant_tables.sql` | `db/migration` | Creates `table_status` enum + `restaurant_tables` table with indexes |
| `TableStatus.java` | `table` | Java enum mirroring PostgreSQL `table_status` (4 values) |
| `RestaurantTable.java` | `table` | JPA entity mapped to `restaurant_tables` — extends `BaseEntity` |
| `TableRepository.java` | `table` | Spring Data JPA repo — 3 custom query methods |
| `QrTokenService.java` | `table` | Generates a 64-char cryptographically secure hex token via `SecureRandom` |
| `TableService.java` | `table` | Interface — defines 6 method signatures (the contract) |
| `TableServiceImpl.java` | `table` | Implements all 6 methods — owns business logic, ownership checks, security |
| `TableMapper.java` | `table` | `toEntity()`, `toResponse()`, `applyUpdate()` — pure data conversion |
| `dto/CreateTableRequest.java` | `table/dto` | Input DTO for creating a table — `tableNumber` required |
| `dto/UpdateTableRequest.java` | `table/dto` | Input DTO for partial update — all fields optional |
| `dto/TableResponse.java` | `table/dto` | Output DTO — all 9 fields including audit timestamps |
| `TableController.java` | `table` | 6 REST endpoints under `/api/v1/restaurants/{restaurantId}/tables` |

---

## Flow 1 — POST /api/v1/restaurants/{restaurantId}/tables (Create a Table)

### Request Example
```http
POST /api/v1/restaurants/ac6a281c-e292-43ea-be90-b72b9295300c/tables
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
Content-Type: application/json

{
  "tableNumber": "T1",
  "capacity": 4,
  "label": "Window Seat"
}
```

---

### Step-by-Step Trace

#### STEP 1 — JWT Filter
`JwtAuthenticationFilter.java` intercepts the request:
- Reads `Authorization: Bearer <token>` header
- Validates JWT signature via `jwtTokenProvider.validateToken(token)`
- Extracts `userId` and `role` ("OWNER") from claims
- Builds `SimpleGrantedAuthority("ROLE_OWNER")`
- Populates `SecurityContextHolder`

#### STEP 2 — Method Security Check
`TableController.java`:
```java
@PreAuthorize("hasRole('OWNER')")
```
Spring Security checks if the authenticated principal has `ROLE_OWNER`.
- **Match** → Proceed.
- **Mismatch** → `AccessDeniedException` → `GlobalExceptionHandler` → HTTP 403.

#### STEP 3 — Controller Method
`TableController.java`:
```java
@PostMapping
@PreAuthorize("hasRole('OWNER')")
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<TableResponse> createTable(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody CreateTableRequest request,
        HttpServletRequest httpRequest) {
    UUID ownerId = getCurrentUserId(httpRequest);
    return ApiResponse.ok("Table created successfully",
            tableService.createTable(restaurantId, request, ownerId));
}
```
- `@PathVariable UUID restaurantId` → Spring parses UUID from URL. Malformed UUID → HTTP 400.
- `getCurrentUserId(httpRequest)` → strips "Bearer ", calls `jwtTokenProvider.getUserIdFromToken(token)` → returns UUID directly.

#### STEP 4 — Bean Validation
`CreateTableRequest.java`:
```java
public record CreateTableRequest(
    @NotBlank String tableNumber,   // REQUIRED — "T1", "VIP-3", "Bar-2"
    Integer capacity,               // optional — Integer not int (can be null)
    String label                    // optional — "Window Seat"
) {}
```
- `@NotBlank` on `tableNumber`: null, empty, or whitespace → `MethodArgumentNotValidException` → HTTP 400.

#### STEP 5 — Service: 3 Guards Before Insert
`TableServiceImpl.java`:
```java
@Override
@Transactional
public TableResponse createTable(UUID restaurantId, CreateTableRequest request, UUID ownerId) {
    // Guard 1: Restaurant must exist
    Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

    // Guard 2: Caller must own this restaurant
    if (!restaurant.getOwnerId().equals(ownerId)) {
        throw new UnauthorizedAccessException("You do not own this restaurant");
    }

    // Guard 3: No duplicate table number in this restaurant
    if (tableRepository.existsByRestaurantIdAndTableNumber(restaurantId, request.tableNumber())) {
        throw new ConflictException("Table number '" + request.tableNumber() + "' already exists in this restaurant");
    }

    String token = qrTokenService.generateToken();
    RestaurantTable table = tableMapper.toEntity(restaurantId, request, token);
    return tableMapper.toResponse(tableRepository.save(table));
}
```

**Why this exact order?**
1. 404 before 403 — no point checking ownership of a non-existent restaurant
2. 403 before 409 — no point checking duplicates for an unauthorized caller
3. 409 last — only after we know the request is valid and authorized

#### STEP 6 — QR Token Generation
`QrTokenService.java`:
```java
public String generateToken() {
    byte[] bytes = new byte[32];          // 256 bits of random space
    secureRandom.nextBytes(bytes);        // OS-sourced entropy — cryptographically unpredictable
    return HexFormat.of().formatHex(bytes); // → 64-char hex string
}
```
Result: `"a3f82c91de047b6e3f5a1c2d8e9b4f70a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d"`

Used in QR codes: `http://localhost:3000/qr/<token>` → customer scans → Day 7 session starts.

#### STEP 7 — Mapper Builds Entity
`TableMapper.java`:
```java
return RestaurantTable.builder()
        .restaurantId(restaurantId)
        .tableNumber(request.tableNumber())   // "T1"
        .capacity(request.capacity())          // 4
        .label(request.label())                // "Window Seat"
        .qrToken(qrToken)                      // 64-char hex
        .build();
// status → @Builder.Default → TableStatus.AVAILABLE (not set explicitly)
```

#### STEP 8 — Database INSERT
```sql
INSERT INTO restaurant_tables (
    id, restaurant_id, table_number, capacity, label, qr_token, status, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    'ac6a281c-e292-43ea-be90-b72b9295300c',
    'T1', 4, 'Window Seat',
    'a3f82c91de047b6e3f5a1c2d8e9b4f70...',
    'AVAILABLE',
    now(), now()
);
```

#### STEP 9 — Client Response
```json
HTTP/1.1 201 Created

{
  "success": true,
  "message": "Table created successfully",
  "data": {
    "id": "7f3a1b22-4c9d-4e8f-a123-bc456def7890",
    "restaurantId": "ac6a281c-e292-43ea-be90-b72b9295300c",
    "tableNumber": "T1",
    "capacity": 4,
    "label": "Window Seat",
    "qrToken": "a3f82c91de047b6e3f5a1c2d8e9b4f70...",
    "status": "AVAILABLE",
    "createdAt": "2026-08-20T13:10:22.445312",
    "updatedAt": "2026-08-20T13:10:22.445312"
  }
}
```

---

## Flow 2 — GET /api/v1/restaurants/{restaurantId}/tables (List All Tables)

### Why List and not Page?
Tables are physically bounded by the restaurant floor — 5 to 50 at most, never thousands.
Pagination adds controller complexity (`?page=0&size=20`) and service complexity (`PageRequest`) with zero practical benefit.

**Rule**: Use `Page` when the dataset is unbounded (all restaurants across all owners). Use `List` when bounded by physical or business reality.

### Repository Query
```java
// TableRepository.java — Spring Data JPA derives SQL from method name
List<RestaurantTable> findByRestaurantId(UUID restaurantId);
```
```sql
SELECT * FROM restaurant_tables
WHERE restaurant_id = 'ac6a281c-...'
-- idx_tables_restaurant_id makes this O(log n)
```

---

## Flow 3 — GET /api/v1/restaurants/{restaurantId}/tables/{tableId}

Fetches a single table. Verifies restaurant access first, then fetches by `tableId`.

> For read endpoints we do NOT check `table.getRestaurantId().equals(restaurantId)`.
> The caller already proved they own or have access to the restaurant. We trust the tableId parameter.
> For **write** endpoints we DO check — see Flow 4.

---

## Flow 4 — PUT /api/v1/restaurants/{restaurantId}/tables/{tableId} (Update Table)

### Request Example
```http
PUT /api/v1/restaurants/ac6a281c-.../tables/7f3a1b22-...
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "capacity": 6,
  "label": "VIP Corner"
}
```

### Partial Update + Dirty Checking
```java
// TableMapper.applyUpdate
if (request.tableNumber() != null) table.setTableNumber(request.tableNumber());
if (request.capacity()    != null) table.setCapacity(request.capacity());   // 6
if (request.label()       != null) table.setLabel(request.label());         // "VIP Corner"
// tableNumber is null in request → skipped → existing value preserved
```

No `repository.save()` needed. Hibernate detects mutations at transaction commit:
```sql
UPDATE restaurant_tables
SET capacity = 6, label = 'VIP Corner', updated_at = now()
WHERE id = '7f3a1b22-...';
```

### Cross-Restaurant Security Check (write operations only)
```java
RestaurantTable table = tableRepository.findById(tableId).orElseThrow(...);

if (!table.getRestaurantId().equals(restaurantId)) {
    throw new ResourceNotFoundException("Table", tableId);  // 404, not 403
}
// Why 404 and not 403?
// Never confirm that a table with this UUID exists in another restaurant.
// Returning 404 leaks no information about other tenants' data.
```

---

## Flow 5 — DELETE /api/v1/restaurants/{restaurantId}/tables/{tableId}

### Hard Delete vs Soft Delete
| | Restaurant (soft delete) | Table (hard delete) |
|---|---|---|
| Operation | Sets `status = INACTIVE` | `tableRepository.delete(table)` — actual row deletion |
| Why? | Financial history, orders, staff all linked | Tables have no direct financial history |
| Row stays? | ✅ Yes | ❌ No |

```sql
DELETE FROM restaurant_tables WHERE id = '7f3a1b22-...';
```

Response: **HTTP 204 No Content** — no body, nothing to return.

---

## Flow 6 — POST /{tableId}/qr/regenerate (Regenerate QR Token)

### Why This Endpoint Exists
If a QR code is compromised (e.g. printed on a card and photographed by someone who left), the owner regenerates the token. The old token immediately becomes invalid — no new sessions can be started with it.

### Trace
```java
// In-place mutation — only qr_token changes
table.setQrToken(qrTokenService.generateToken());

// Hibernate dirty checking fires at commit:
// UPDATE restaurant_tables SET qr_token = 'newtoken...', updated_at = now() WHERE id = '...';
```

### Why qrToken is NOT the entity id
```
id (UUID)        → Internal — FK in customer_sessions, never changes
qrToken (String) → External — embedded in QR images, safe to regenerate freely
```
If they were the same field, regenerating the QR would require changing the PK — cascade-breaking all session foreign keys.

---

## Cross-Cutting Concerns

### Dual Security Checkpoint (Defense in Depth)

```
Checkpoint 1 — @PreAuthorize on Controller method
    Blocks wrong role (e.g. EMPLOYEE trying to create a table) before entering the method

Checkpoint 2 — Ownership check in ServiceImpl
    Blocks authenticated OWNER who owns Restaurant A from modifying Restaurant B's tables
```

Neither layer trusts the other. Both must pass.

### getCurrentUserId in TableController

```java
private UUID getCurrentUserId(HttpServletRequest request) {
    String token = request.getHeader("Authorization").substring(7); // strip "Bearer "
    return jwtTokenProvider.getUserIdFromToken(token);               // already returns UUID
}
```

The `JwtAuthenticationFilter` already validated and verified the token before this controller method runs. Extracting it again here is safe — we're just reading already-verified claims.

---

## Summary of Status Codes & Exceptions

| Situation | Exception | HTTP | Response |
|---|---|---|---|
| No JWT / Expired JWT | `AuthenticationException` | 401 | `{"success":false,"message":"Authentication required"}` |
| Non-OWNER calling OWNER-only route | `AccessDeniedException` | 403 | `{"success":false,"message":"Access denied"}` |
| Owner accessing another owner's restaurant | `UnauthorizedAccessException` | 403 | `{"success":false,"message":"You do not own this restaurant"}` |
| `restaurantId` not in DB | `ResourceNotFoundException` | 404 | `{"success":false,"message":"Restaurant not found with id: ..."}` |
| `tableId` not in DB | `ResourceNotFoundException` | 404 | `{"success":false,"message":"Table not found with id: ..."}` |
| `tableId` belongs to different restaurant | `ResourceNotFoundException` | 404 | Same — intentional, no info leak |
| Duplicate `tableNumber` in same restaurant | `ConflictException` | 409 | `{"success":false,"message":"Table number 'T1' already exists..."}` |
| `tableNumber` blank or missing | `MethodArgumentNotValidException` | 400 | `{"success":false,"message":"Validation failed: tableNumber must not be blank"}` |
| Successful GET | — | 200 | `{"success":true,"message":"...","data":{...}}` |
| Successful POST | — | 201 | `{"success":true,"message":"...","data":{...}}` |
| Successful DELETE | — | 204 | *(no body)* |

---

---

# Table Module — Concepts & Interview Prep

> Every design decision explained + the exact questions an SDE-1 interviewer will ask.

---

## Part 1: File-by-File Technical Breakdown

### 1. `RestaurantTable.java` — The JPA Entity

**What it is**: The core domain entity representing a physical table in a restaurant. Maps to `restaurant_tables` in PostgreSQL. Extends `BaseEntity` to inherit UUID primary key and audit timestamps.

**Key annotation breakdown**:
```java
@Entity
// Marks this as a JPA-managed entity

@Table(name = "restaurant_tables")
// Explicit table name — 'RestaurantTable' (the class name) would have mapped
// to 'restaurant_table' by default (singular). We need plural to match V4.

@Builder
// Enables fluent construction. Combined with @Builder.Default on status field.

public class RestaurantTable extends BaseEntity
// Inherits: UUID id, LocalDateTime createdAt, LocalDateTime updatedAt
// BaseEntity uses @MappedSuperclass + AuditingEntityListener for auto-fill

@Enumerated(EnumType.STRING)
@Column(nullable = false, columnDefinition = "table_status")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Builder.Default
private TableStatus status = TableStatus.AVAILABLE;
// All 4 annotations are required together:
// @Enumerated(EnumType.STRING) — store enum name, not ordinal
// columnDefinition = "table_status" — tell Hibernate the PostgreSQL type name
// @JdbcTypeCode(SqlTypes.NAMED_ENUM) — Hibernate 6 requires this to bind
//     the parameter as the actual named enum type (not VARCHAR), preventing
//     "column is of type table_status but expression is of type character varying"
// @Builder.Default — ensures builder uses AVAILABLE if status not set explicitly
```

**Why `Integer` for capacity and not `int`?**
The DB column has no `NOT NULL` — capacity is optional. Java's `int` primitive defaults to `0` which would incorrectly mean "zero seats". `Integer` (wrapper) defaults to `null`, correctly representing "not specified".

---

### 2. `TableStatus.java` — Operational State Enum

**4 states and their meaning**:
```
AVAILABLE   → table is free, no active customer session
OCCUPIED    → a customer session is active at this table
RESERVED    → reserved in advance (future feature)
MAINTENANCE → table is temporarily out of service (broken, cleaning, etc.)
```

**Why it's defined in V4, not V1**:
V1 should be frozen after creation — it defines the enums that exist at project start. `table_status` belongs to the table module and was added in V4 alongside the `restaurant_tables` table. This keeps each migration self-contained and reasoned about independently.

---

### 3. `QrTokenService.java` — Token Generation

**Why `SecureRandom` and not `UUID.randomUUID()` or `Math.random()`?**

| | `Math.random()` | `UUID.randomUUID()` | `SecureRandom` |
|---|---|---|---|
| Predictable? | ✅ Yes (seeded by time) | ❌ No | ❌ No |
| Cryptographically secure? | ❌ No | ✅ Yes | ✅ Yes |
| URL-safe output? | Has to be formatted | Has hyphens: `550e8400-e29b-...` | Hex: `a3f82c91de04...` |
| Bits of entropy | ~53 | 122 | 256 (32 bytes × 8) |

`SecureRandom` gives more entropy (256 bits) and cleaner output. `UUID.randomUUID()` would also work but has hyphens and only 122 bits.

**Why `final` on the SecureRandom instance?**
```java
private final SecureRandom secureRandom = new SecureRandom();
```
`SecureRandom` is thread-safe. Creating it once and reusing is more efficient than creating a new instance per request. `final` signals it should never be reassigned.

---

### 4. `TableRepository.java` — The 3 Query Methods

```java
public interface TableRepository extends JpaRepository<RestaurantTable, UUID> {

    // Method 1: List all tables for a restaurant
    List<RestaurantTable> findByRestaurantId(UUID restaurantId);
    // SQL: SELECT * FROM restaurant_tables WHERE restaurant_id = ?
    // Uses: idx_tables_restaurant_id index

    // Method 2: Duplicate check before insert
    boolean existsByRestaurantIdAndTableNumber(UUID restaurantId, String tableNumber);
    // SQL: SELECT EXISTS(SELECT 1 FROM restaurant_tables
    //      WHERE restaurant_id = ? AND table_number = ?)
    // Returns true/false — never loads entities into memory

    // Method 3: QR scan lookup (used Day 7 — Customer Sessions)
    Optional<RestaurantTable> findByQrToken(String qrToken);
    // SQL: SELECT * FROM restaurant_tables WHERE qr_token = ?
    // Uses: idx_tables_qr_token index — must be fast, called on every QR scan
}
```

**Why `Optional` on `findByQrToken`?**
The token comes from an untrusted source (a customer's phone camera). It could be expired, corrupted, or manually tampered with. `Optional` forces the caller to handle the "not found" case explicitly via `.orElseThrow(...)`. A plain return type would return `null` and risk NPEs.

---

### 5. `TableServiceImpl.java` — Business Logic Layer

**The cross-restaurant table validation**:
```java
if (!table.getRestaurantId().equals(restaurantId)) {
    throw new ResourceNotFoundException("Table", tableId);
}
```
Owner A can't update Owner B's table by guessing a UUID. Returns 404 (not 403) to avoid leaking that the table exists in another tenant's data.

**Why `readOnly = true` at class level + `@Transactional` on write methods?**
```java
@Transactional(readOnly = true)   // class-level default — all methods are read-only
public class TableServiceImpl implements TableService {

    @Transactional               // overrides class-level for write methods
    public TableResponse createTable(...) { ... }
```
`readOnly = true` tells Hibernate to skip dirty checking on reads (no snapshot comparison at commit) — performance improvement on all GET methods. Write methods override this to get full transaction semantics.

---

### 6. `TableMapper.java` — Conversion Layer

**Why `@Component` and not `@Service`?**
`@Service` is for business logic. `@Component` is for utility beans. A mapper does no business logic — it only converts data shapes. Using the correct annotation makes intent clear to any developer reading the code.

**Why `applyUpdate` mutates the entity in-place instead of returning a new one?**
```java
// applyUpdate doesn't return anything
public void applyUpdate(UpdateTableRequest request, RestaurantTable table) {
    if (request.capacity() != null) table.setCapacity(request.capacity());
    ...
}
```
The entity is inside an active `@Transactional` persistence context. Hibernate tracks it. When we mutate the entity and the transaction commits, Hibernate auto-issues the `UPDATE` SQL (dirty checking). If we created a new entity and tried to save it, Hibernate would try to INSERT — wrong operation.

---

## Part 2: Core Concepts

### Concept 1: Hibernate Dirty Checking

When you call `tableRepository.findById(id)` inside a `@Transactional` method, Hibernate:
1. Fetches the row from DB
2. Creates a **snapshot** (copy) of the entity's state
3. Attaches the entity to the **Persistence Context**

At transaction commit, Hibernate:
1. Compares current entity state with the snapshot
2. For every field that changed → generates an `UPDATE` statement
3. No explicit `save()` needed

**This is used in**: `updateTable()`, `regenerateQrToken()` — we mutate the entity and return without calling `.save()`.

---

### Concept 2: Multi-Tenant Isolation

This backend serves multiple restaurants from one deployment. Every query that could return data from multiple tenants (restaurant owners) is guarded:

```java
// Pattern used in every method:
Restaurant restaurant = restaurantRepository.findById(restaurantId).orElseThrow();
if (!restaurant.getOwnerId().equals(callerId)) throw new UnauthorizedAccessException(...);
```

This ensures Owner A can never see or modify Owner B's tables, even if they know the UUID. This is called **row-level tenant isolation** — enforced in the service layer, not the DB (no row-level security policies in PostgreSQL for this project).

---

### Concept 3: Why `qrToken` is Separate from `id`

```
UUID id       → Internal key. Used in FK relationships (customer_sessions.table_id).
               → Never shown to customers, never in QR codes.
               → Changing it would break all FK constraints.

String qrToken → External identifier. Embedded in QR image URLs.
               → Can be regenerated freely — only this column changes.
               → Old sessions keep their table reference via `id`.
```

If they were the same field, a compromised QR would require changing the PK — impossible in a live system with FK constraints.

---

### Concept 4: Defense in Depth (Two Security Checkpoints)

```
Layer 1 — Spring Security (@PreAuthorize)
    → Checks: "Does this caller have the right ROLE?"
    → e.g., hasRole('OWNER') — blocks EMPLOYEE from creating tables

Layer 2 — Service Layer (manual ownership check)
    → Checks: "Is this the right OWNER for this specific restaurant?"
    → e.g., restaurant.getOwnerId().equals(ownerId) — blocks Owner A from
       modifying Owner B's tables even though both have ROLE_OWNER
```

Neither layer trusts the other. Both must pass. This is **Defense in Depth** — a standard security principle. An interviewer asking "how do you prevent horizontal privilege escalation?" — this is your answer.

---

### Concept 5: Hard Delete vs Soft Delete

| | Restaurant | Table |
|---|---|---|
| Delete operation | Sets `status = INACTIVE` | `tableRepository.delete(table)` |
| Why? | Financial records, order history, staff — all FK-linked | No direct financial history on the table row itself |
| Row removed? | No | Yes |

**Rule**: Soft delete when historical data or FK relationships matter. Hard delete when the row has no downstream dependencies that need to stay visible.

---

### Concept 6: `@Transactional(readOnly = true)` — What It Actually Does

1. **Hibernate skips dirty checking**: No snapshot comparison at commit → faster reads.
2. **Connection pool optimization**: Some connection pools (HikariCP) can route read-only transactions to a read replica (if configured). Our app uses one DB, but the config is correct for scalability.
3. **Accidental write protection**: If you accidentally call `table.setCapacity(5)` in a `readOnly` transaction, Hibernate will either ignore it or throw — depending on the JPA provider. This catches bugs.

Write methods override to `@Transactional` (read-write) — full semantics, dirty checking enabled.

---

## Part 3: Interview Questions & Answers

### Q1: "Walk me through what happens when a restaurant owner adds a new table."

**Answer**:
"When the owner sends `POST /api/v1/restaurants/{restaurantId}/tables` with a JWT:

1. `JwtAuthenticationFilter` validates the token and sets `ROLE_OWNER` in the security context.
2. `@PreAuthorize("hasRole('OWNER')")` confirms the role before the controller method runs.
3. The controller extracts the `ownerId` UUID from the JWT and passes it to `createTable()` — the owner ID is never trusted from the request body.
4. The service performs three guards in order: restaurant exists (404), caller owns it (403), table number not duplicate in this restaurant (409).
5. `QrTokenService.generateToken()` generates 32 cryptographically random bytes via `SecureRandom`, formatted as a 64-char hex string.
6. The mapper builds the entity, the repository saves it, Hibernate issues an INSERT.
7. The response includes the `qrToken` which gets embedded in a QR image the owner can print and place on the table."

---

### Q2: "Why is `qrToken` a separate field and not just the table's UUID?"

**Answer**:
"Two reasons. First, the `id` UUID is used as a foreign key in `customer_sessions`. If we changed it, every existing session record would have a broken FK reference. Second, security: when a QR code is compromised — say a printed code is photographed by a former employee — the owner needs to regenerate it. By keeping `qrToken` separate, regeneration only changes that one string column. The `id` stays the same, all FK relationships remain intact."

---

### Q3: "Why do you return 404 instead of 403 when a table doesn't belong to the given restaurant?"

**Answer**:
"Information security — never reveal more than necessary. If we return 403, we're confirming that a table with that UUID exists somewhere in the system, just not in this restaurant. That's a data leak about another tenant. By returning 404, we tell the caller 'this table doesn't exist in this context' — which is true from their perspective. This prevents enumeration attacks where an attacker guesses UUIDs and infers which tables exist across different restaurants."

---

### Q4: "Why use `SecureRandom` instead of `UUID.randomUUID()` for the QR token?"

**Answer**:
"Two reasons: entropy and format. `UUID.randomUUID()` has 122 bits of randomness, which is enough, but the UUID format has hyphens that need encoding in URLs. `SecureRandom` with 32 bytes gives 256 bits — more entropy — and when hex-encoded gives a clean 64-character string that's directly URL-safe with no encoding needed. Both are cryptographically secure, but our approach gives cleaner QR code URLs and more entropy."

---

### Q5: "Explain Hibernate dirty checking and where you use it."

**Answer**:
"When you load an entity inside a `@Transactional` method, Hibernate creates a snapshot of its initial state and attaches it to the Persistence Context. At transaction commit, Hibernate compares the current state against the snapshot and automatically generates UPDATE SQL for any changed fields.

In this module, `updateTable()` and `regenerateQrToken()` use this. I load the entity, mutate it via the mapper or `setQrToken()`, and return without ever calling `repository.save()`. Hibernate detects the mutations and issues the UPDATE automatically. This is more efficient than explicit saves and is idiomatic JPA."

---

### Q6: "Why is `List` used for `getTablesByRestaurant` instead of `Page`?"

**Answer**:
"Pagination is useful when the result set is unbounded. A restaurant owner listing all restaurants in the platform could see thousands — pagination is essential there. But tables are physically bounded by the restaurant floor. A restaurant has 5 to 50 tables at most. Adding `Pageable` would require `?page=0&size=20` query parameters in the controller, a `PageRequest` in the service, and a `Page<TableResponse>` return type — all for data that always fits in a single small JSON array. It would be over-engineering. Simplicity is a design choice."

---

### Q7: "How does `@Builder.Default` work with Lombok?"

**Answer**:
"When you use `@Builder` on a class, Lombok generates a builder. But it doesn't respect field initializers — so `private TableStatus status = TableStatus.AVAILABLE` would be ignored by the builder, and you'd get `null` if you don't explicitly call `.status(...)`. `@Builder.Default` tells Lombok to use the specified default value when the field isn't explicitly set in the builder chain. Without it, every `RestaurantTable.builder().build()` call would have a null `status`, which would violate the `NOT NULL` constraint in the DB."

---

### Q8: "What is the difference between `@Service` and `@Component`?"

**Answer**:
"Both register a Spring-managed bean. The difference is semantic. `@Service` signals that the class contains business logic — it's the service layer. `@Component` is generic — it signals a utility bean. `@Repository` signals a data access bean and adds exception translation. Using the right one makes intent clear: `TableMapper` is a utility converter with no business logic, so `@Component` is appropriate. `TableServiceImpl` owns business rules and decisions, so `@Service` is correct."

---

### Q9: "What would happen if two owners simultaneously created a table with the same number in the same restaurant?"

**Answer**:
"There are two layers of protection. First, the service-layer check: `tableRepository.existsByRestaurantIdAndTableNumber(restaurantId, request.tableNumber())`. This catches the duplicate and throws `ConflictException` → HTTP 409 in normal cases.

However, in a race condition — two requests hitting the service check simultaneously before either commits — both could pass the check. That's why the DB-level constraint exists: `UNIQUE(restaurant_id, table_number)` in V4. PostgreSQL will reject the second INSERT with a unique violation. Hibernate converts this to a `DataIntegrityViolationException`, caught by `GlobalExceptionHandler` → HTTP 409.

Two layers: application check for good error messages, DB constraint as the final guarantee."

---

### Q10: "How does `@Transactional(readOnly = true)` at the class level work with method-level `@Transactional`?"

**Answer**:
"Spring's `@Transactional` supports attribute inheritance. When `readOnly = true` is on the class, all methods inherit it as the default. When a specific method has its own `@Transactional` annotation (with `readOnly` defaulting to `false`), Spring uses the method-level annotation — it overrides the class-level. So `createTable`, `updateTable`, `deleteTable`, and `regenerateQrToken` all run with full read-write transactions, while `getTablesByRestaurant` and `getTableById` run with read-only transactions. This is a clean way to express the access pattern of the whole class with minimal repetition."

---

### Q11: "Why doesn't `TableController` use `SecurityContextHolder.getContext().getAuthentication()` like `RestaurantController` does?"

**Answer**:
"Both approaches are valid and produce the same result — the `JwtAuthenticationFilter` already stored the principal in the security context. `TableController` extracts the token directly from the `Authorization` header and calls `jwtTokenProvider.getUserIdFromToken()` — explicit and easy to unit test without needing to mock `SecurityContextHolder`. `RestaurantController` reads from the context — slightly less code. In a real team, you'd standardize on one approach. The important thing to understand is that both read from data already validated by the filter — neither is parsing or trusting raw user input."

---

### Q12: "Can a customer access the `/tables` endpoints?"

**Answer**:
"No. All 6 table management endpoints require either `ROLE_OWNER` or `ROLE_SUPER_ADMIN`. Customers in this system don't have user accounts — they're identified by session tokens (`X-Session-Token` header, not JWT). The table endpoints are strictly management APIs for restaurant staff. Customers interact with tables indirectly: they scan the QR code printed on the physical table, which hits the public `POST /sessions/start` endpoint (Day 7). That endpoint uses `findByQrToken()` on the repository — a completely separate code path from these management endpoints."
