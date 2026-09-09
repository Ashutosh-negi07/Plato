# Customer Session Module — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### CustomerSession.java — The Entity

**What it is**: A JPA entity mapping to `customer_sessions`. Represents an ephemeral dining visit at a specific restaurant table.

**Key design decisions**:

```java
@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;

@Column(name = "table_id", nullable = false)
private UUID tableId;
// Direct UUID foreign keys rather than @ManyToOne.
// Prevents automatic eager fetching or hidden N+1 queries when querying sessions.

@Column(name = "session_token", nullable = false, unique = true, length = 128)
private String sessionToken;
// A 64-character hex cryptographically secure token passed by customer in X-Session-Token.
// Opaque to the client. The internal primary key 'id' (UUID) is never exposed as the auth credential.

@Enumerated(EnumType.STRING)
@Column(nullable = false, columnDefinition = "session_status")
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Builder.Default
private SessionStatus status = SessionStatus.ACTIVE;
// Maps directly to PostgreSQL custom enum 'session_status'.
// Hibernate 6 @JdbcTypeCode(SqlTypes.NAMED_ENUM) handles conversion without error.

@Column(name = "expires_at", nullable = false)
private LocalDateTime expiresAt;
// Implements a sliding inactivity expiration window (default 30 minutes from last activity).
```

#### Domain Helper Methods on the Entity:
```java
public boolean isExpired() {
    return LocalDateTime.now().isAfter(this.expiresAt) || this.status == SessionStatus.EXPIRED;
}

public void refreshActivity() {
    this.lastActivity = LocalDateTime.now();
    this.expiresAt = LocalDateTime.now().plusMinutes(30);
}
```
*Encapsulates state mutation logic inside the entity itself rather than scattering timestamp arithmetic across services.*

---

### SessionStatus.java — The Enum

```java
public enum SessionStatus {
    ACTIVE,
    CLOSED,
    EXPIRED
}
```
- **`ACTIVE`**: The customer is currently dining, browsing the menu, or waiting for food. Orders and cart modifications are permitted.
- **`CLOSED`**: The session was successfully completed (bill paid, customer left, staff released table). Read-only history.
- **`EXPIRED`**: Inactivity timeout reached (e.g. customer walked away without ordering). Any further request with this token requires a fresh QR scan.

---

### V7__create_customer_sessions.sql — Flyway Migration

```sql
CREATE TABLE customer_sessions (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    restaurant_id   UUID            NOT NULL REFERENCES restaurants(id),
    table_id        UUID            NOT NULL REFERENCES restaurant_tables(id),
    session_token   VARCHAR(128)    NOT NULL UNIQUE,
    status          session_status  NOT NULL DEFAULT 'ACTIVE',
    guest_count     INT             NOT NULL DEFAULT 1,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_activity   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_sessions_token          ON customer_sessions(session_token);
CREATE INDEX idx_customer_sessions_restaurant_id  ON customer_sessions(restaurant_id);
CREATE INDEX idx_customer_sessions_table_id       ON customer_sessions(table_id);
CREATE INDEX idx_customer_sessions_status         ON customer_sessions(status);
```

#### Indexing Strategy Rationale:
1. **`idx_customer_sessions_token`**: Every single customer request (cart, order, checkout) looks up the session by `session_token`. $O(1)$ B-Tree lookup is mandatory.
2. **`idx_customer_sessions_table_id` & `status`**: Scanning a QR code checks `WHERE table_id = ? AND status = 'ACTIVE'`.
3. **`idx_customer_sessions_restaurant_id`**: Enables restaurant managers/owners to view all historical dining sessions and metrics.

---

### CustomerSessionRepository.java — Spring Data JPA

```java
public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {
    Optional<CustomerSession> findBySessionToken(String sessionToken);
    Optional<CustomerSession> findByTableIdAndStatus(UUID tableId, SessionStatus status);
}
```
- **Derived Query Methods**: Leverages Spring Data JPA to generate optimized SQL without writing boilerplate JPQL.
- Returns `Optional<CustomerSession>` to enforce null-safety and idiomatic Java 8+ handling.

---

### CustomerSessionServiceImpl.java — Business Logic

#### 1. Cryptographic Token Generation
```java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

private String generateSecureToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
}
```
- **`SecureRandom`**: Uses the OS cryptographically strong pseudo-random number generator (`/dev/urandom` on Unix/Mac). Standard `java.util.Random` is pseudo-random and vulnerable to seed prediction.
- 32 bytes = 256 bits of entropy $\rightarrow$ formatted as a 64-character hexadecimal string. Brute-force guessing probability is $1 / 2^{256}$ (effectively zero).

#### 2. Multi-Guest Table Concurrency & Rejoining
```java
Optional<CustomerSession> existingSessionOpt = sessionRepository
        .findByTableIdAndStatus(table.getId(), SessionStatus.ACTIVE);

if (existingSessionOpt.isPresent()) {
    CustomerSession existing = existingSessionOpt.get();
    if (!existing.isExpired()) {
        existing.refreshActivity();
        return sessionMapper.toResponse(existing, restaurant, table);
    }
    existing.setStatus(SessionStatus.EXPIRED);
    existing.setEndedAt(LocalDateTime.now());
}
```
- **Scenario**: Guest A and Guest B sit at Table 4. Both scan the table QR code.
- Rather than creating two competing sessions for the same physical table, Guest B joins Guest A's active session.
- If the previous session expired due to inactivity, it is marked `EXPIRED` and a fresh session begins.

#### 3. Table State Machine Synchronization
- When a session starts: `table.setStatus(TableStatus.OCCUPIED)`.
- When a session closes: `table.setStatus(TableStatus.AVAILABLE)`.
- Keeps the floor map and host dashboard consistent with real-time physical table occupancy.

---

### CustomerSessionController.java — REST Endpoints

1. **`POST /api/v1/customer/sessions/start`**:
   - **Public**: Diners have no staff credentials; they enter via the physical QR code token.
   - Status: `201 CREATED`.
2. **`GET /api/v1/customer/sessions/current`**:
   - Authenticated via custom request header: `X-Session-Token`.
   - Slides the 30-minute inactivity window forward on every interaction.
3. **`POST /api/v1/restaurants/{restaurantId}/sessions/{sessionId}/close`**:
   - **Staff Only**: Protected by Spring Security `@PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")`.
   - Verifies tenant isolation so an employee cannot close another restaurant's session.

---

## Architectural & Interview Deep Dives

### Q1: Why use an opaque `session_token` in the database instead of a stateless customer JWT?
**Answer**:
> "There are three primary reasons:
> 1. **Immediate Revocation**: If a customer leaves the table, or staff closes the table at the register, a database-backed session can be invalidated immediately (`status = CLOSED`). A stateless JWT cannot be revoked without maintaining a distributed token blacklist or Redis revocation set, defeating statelessness.
> 2. **Sliding Inactivity Expiration**: Restaurant dining is inherently activity-based. Diners may stay 20 minutes or 2 hours. A database-backed `expires_at` can easily slide forward (+30 mins) on every interaction. Doing this with JWTs would require constantly re-issuing new JWTs on almost every HTTP response.
> 3. **Shared Table State**: When multiple diners at the same table order food together, they need to share one common active session ID. A database-backed session token allows seamless table-sharing."

---

### Q2: Why is the table `qr_token` separate from the `customer_sessions.session_token`?
**Answer**:
> "They have completely different lifecycles and threat models:
> - **`qr_token`** is **static and public**: It is printed on physical acrylic stickers placed on dining tables. It lasts for months or years. If someone photographs it or takes it home, the restaurant owner can regenerate that one token in the admin portal without altering any historical database IDs.
> - **`session_token`** is **ephemeral and secret**: It is generated upon scanning and only lasts for that specific dining visit. A person who scanned the QR code yesterday cannot use yesterday's `session_token` to place food orders at today's dinner."

---

### Q3: How do you prevent race conditions when two customers scan the QR code at the exact same millisecond?
**Answer**:
> "In high concurrency scenarios, two requests could hit `findByTableIdAndStatus(tableId, ACTIVE)` simultaneously and both find no active session, attempting to insert two `ACTIVE` sessions for the same table.
> We handle this at two levels:
> 1. At the application level, transactional isolation and checking existing sessions.
> 2. At the database schema level, we can add a PostgreSQL partial unique index:
>    `CREATE UNIQUE INDEX idx_one_active_session_per_table ON customer_sessions(table_id) WHERE status = 'ACTIVE';`
>    If two inserts race, one succeeds and the second triggers a unique constraint violation (`DataIntegrityViolationException`), which catches and gracefully re-queries the newly created active session."

---

### Q4: Why is `ended_at` stored separately from `expires_at`?
**Answer**:
> "`expires_at` represents the **projected** sliding expiration timestamp (e.g. `now() + 30 minutes`).
> `ended_at` represents the **actual** time the session terminated (either via staff checkout `CLOSED` or inactivity cleanup `EXPIRED`).
> Storing both gives restaurant owners rich analytics, such as average dining duration (`ended_at - started_at`) vs table turn-around time."

---

### Q5: How would you clean up millions of expired sessions in a production system?
**Answer**:
> "In a production system at scale:
> 1. **Spring Scheduled Task / Cron Job**:
>    A periodic `@Scheduled(cron = "0 */10 * * * *")` background task runs:
>    `UPDATE customer_sessions SET status = 'EXPIRED', ended_at = now() WHERE status = 'ACTIVE' AND expires_at < now();`
> 2. **Table status release**: The same job finds tables marked `OCCUPIED` with no active sessions and resets them to `AVAILABLE`.
> 3. **Partitioning**: For historical data, we can partition `customer_sessions` by `started_at` (monthly or quarterly) so old records can be archived to cold storage without degrading active B-tree index performance."
