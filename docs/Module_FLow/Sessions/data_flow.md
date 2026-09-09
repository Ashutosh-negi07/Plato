# Customer Session Module — Complete Data Flow

> Every HTTP request through the Customer Session module traced step-by-step from HTTP wire to PostgreSQL and back.

---

## Architecture Overview

```
Customer Mobile Browser (Scans Table QR)
     │
     │  POST /api/v1/customer/sessions/start (qrToken, guestCount)
     ▼
CustomerSessionController.java   — public endpoint (no staff JWT required)
     │
     ▼
CustomerSessionService.java       — business interface contract
     │
     ▼
CustomerSessionServiceImpl.java   — token generation, QR resolution, multi-guest sharing,
     │                              sliding expiration window, table status sync
     ├── TableRepository          — resolves QR token to restaurant_tables
     ├── RestaurantRepository     — verifies restaurant is active & taking orders
     ├── CustomerSessionRepository— checks active sessions, saves session
     └── CustomerSessionMapper    — maps entity + restaurant + table to response DTO
     │
     ▼
PostgreSQL Tables:
     ├── customer_sessions (V7)
     ├── restaurant_tables (V4)
     └── restaurants (V3)
```

### Table Relationships

```
restaurants
     │
     ├── restaurant_tables (restaurant_id FK, qr_token UNIQUE)
     │        │
     │        └── customer_sessions (table_id FK, restaurant_id FK, session_token UNIQUE)
```

---

## Why No Customer Accounts?

1. **Frictionless Dining UX**: Diners at a restaurant will not download an app or register an email/password just to order food.
2. **Session-Bound Identity**: The customer is identified solely by their `session_token` (held in browser local storage or memory).
3. **Table & Restaurant Scoping**: All cart items, orders, and bill requests inherit the table and restaurant through `session_id`.

---

## Flow 1 — POST /api/v1/customer/sessions/start (Start or Rejoin Session)

### Request Example
```http
POST /api/v1/customer/sessions/start
Content-Type: application/json

{
  "qrToken": "550e8400-e29b-41d4-a716-446655440000",
  "guestCount": 2
}
```

### Step-by-Step Trace

#### STEP 1 — Request Validation
- Spring's `@Valid` checks `StartSessionRequest`:
  - `qrToken` must not be blank (`@NotBlank`).
  - `guestCount` must be >= 1 (`@Min(1)`). If omitted, defaults to 1.
- If validation fails, `GlobalExceptionHandler` intercepts and returns `400 Bad Request` with structured error details.

#### STEP 2 — Controller Dispatch
```java
@PostMapping("/customer/sessions/start")
public ResponseEntity<ApiResponse<CustomerSessionResponse>> startSession(
        @Valid @RequestBody StartSessionRequest request) {
    CustomerSessionResponse response = sessionService.startSession(request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Dining session started", response));
}
```

#### STEP 3 — Table Lookup via QR Token
```java
RestaurantTable table = tableRepository.findByQrToken(request.qrToken())
        .orElseThrow(() -> new ResourceNotFoundException("Table QR code is invalid"));
```
- **SQL Executed**:
  ```sql
  SELECT * FROM restaurant_tables WHERE qr_token = '550e8400-e29b-41d4-a716-446655440000';
  ```
- If QR token does not exist or has been regenerated, throws `ResourceNotFoundException("Table QR code is invalid")` -> maps to `404 Not Found`.

#### STEP 4 — Restaurant Status Checks
```java
Restaurant restaurant = restaurantRepository.findById(table.getRestaurantId())
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant", table.getRestaurantId()));

if (restaurant.getStatus() != RestaurantStatus.ACTIVE) {
    throw new ValidationException("This restaurant is not currently active");
}
if (Boolean.FALSE.equals(restaurant.getAcceptingOrders())) {
    throw new ValidationException("This restaurant is currently not accepting orders");
}
```
- **SQL Executed**:
  ```sql
  SELECT * FROM restaurants WHERE id = ?;
  ```
- Prevents customers from starting dining sessions at closed or suspended venues -> `400 Bad Request`.

#### STEP 5 — Multi-Guest & Rejoin Check
A dining table often has multiple guests scanning the same QR code, or a guest refreshing their browser:
```java
Optional<CustomerSession> existingSessionOpt = sessionRepository
        .findByTableIdAndStatus(table.getId(), SessionStatus.ACTIVE);

if (existingSessionOpt.isPresent()) {
    CustomerSession existing = existingSessionOpt.get();
    if (!existing.isExpired()) {
        log.info("Table {} already has an active session; rejoining existing session", table.getTableNumber());
        existing.refreshActivity(); // slides 30-min window
        return sessionMapper.toResponse(existing, restaurant, table);
    }
    // If expired, clean up state
    existing.setStatus(SessionStatus.EXPIRED);
    existing.setEndedAt(LocalDateTime.now());
}
```
- **SQL Executed**:
  ```sql
  SELECT * FROM customer_sessions 
  WHERE table_id = ? AND status = 'ACTIVE' 
  LIMIT 1;
  ```
- **If Active Session Exists**: All diners at the table share the same active dining session. The session expiration window is refreshed, and the existing session details are returned (`201 CREATED`).
- **If Session Expired**: Status transitions to `EXPIRED`, `ended_at` is set to `now()`, and a fresh session is provisioned below.

#### STEP 6 — Cryptographic Token Generation & Session Persistence
```java
CustomerSession newSession = CustomerSession.builder()
        .restaurantId(restaurant.getId())
        .tableId(table.getId())
        .sessionToken(generateSecureToken()) // 64-character hex string from 32 secure random bytes
        .status(SessionStatus.ACTIVE)
        .guestCount(guests)
        .startedAt(LocalDateTime.now())
        .lastActivity(LocalDateTime.now())
        .expiresAt(LocalDateTime.now().plusMinutes(30))
        .build();

CustomerSession savedSession = sessionRepository.save(newSession);
```
- **SQL Executed**:
  ```sql
  INSERT INTO customer_sessions (
      id, restaurant_id, table_id, session_token, status, guest_count,
      started_at, last_activity, expires_at, created_at, updated_at
  ) VALUES (
      gen_random_uuid(), ?, ?, 'a3f8c2...', 'ACTIVE', 2,
      now(), now(), now() + interval '30 minutes', now(), now()
  );
  ```

#### STEP 7 — Table Status Synchronization
```java
table.setStatus(TableStatus.OCCUPIED);
tableRepository.save(table);
```
- **SQL Executed**:
  ```sql
  UPDATE restaurant_tables SET status = 'OCCUPIED', updated_at = now() WHERE id = ?;
  ```

#### STEP 8 — Response Mapping
```json
{
  "success": true,
  "message": "Dining session started",
  "data": {
    "id": "7b8e1a22-1c23-4d56-8a90-123456789abc",
    "restaurantId": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
    "restaurantName": "The Tuscan Table",
    "tableId": "c9a0d8e7-5b6a-4f3e-bc21-0987654321fe",
    "tableNumber": "T-12",
    "sessionToken": "a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc",
    "status": "ACTIVE",
    "guestCount": 2,
    "startedAt": "2026-09-09T14:30:00",
    "expiresAt": "2026-09-09T15:00:00"
  },
  "timestamp": "2026-09-09T14:30:00.125"
}
```

---

## Flow 2 — GET /api/v1/customer/sessions/current (Get Session & Heartbeat)

### Request Example
```http
GET /api/v1/customer/sessions/current
X-Session-Token: a3f8c2b984d7e10f4433221100aabbccddeeff00112233445566778899aabbcc
```

### Step-by-Step Trace

#### STEP 1 — Token Header Extraction
```java
@GetMapping("/customer/sessions/current")
public ResponseEntity<ApiResponse<CustomerSessionResponse>> getCurrentSession(
        @RequestHeader("X-Session-Token") String sessionToken) {
    CustomerSessionResponse response = sessionService.getCurrentSession(sessionToken);
    return ResponseEntity.ok(ApiResponse.ok("Session retrieved", response));
}
```

#### STEP 2 — Session Lookup & Expiration Validation
```java
public CustomerSession validateAndRefreshSession(String sessionToken) {
    if (sessionToken == null || sessionToken.isBlank()) {
        throw new SessionExpiredException("Session token is missing");
    }

    CustomerSession session = sessionRepository.findBySessionToken(sessionToken)
            .orElseThrow(() -> new SessionExpiredException("Invalid session token"));

    if (session.isExpired()) {
        session.setStatus(SessionStatus.EXPIRED);
        session.setEndedAt(LocalDateTime.now());
        throw new SessionExpiredException("Your session has expired. Please scan the QR code again.");
    }

    // Slide window by 30 minutes
    session.refreshActivity();
    return session;
}
```
- **SQL Executed**:
  ```sql
  SELECT * FROM customer_sessions WHERE session_token = 'a3f8c2...' LIMIT 1;
  ```
- **Validation**:
  - Checks `LocalDateTime.now().isAfter(expiresAt)` or `status == EXPIRED`.
  - If expired: sets `ended_at = now()`, throws `SessionExpiredException` -> `401 Unauthorized` with friendly message.
  - If valid: executes `refreshActivity()`, updating `last_activity = now()` and `expires_at = now() + 30 mins`.

#### STEP 3 — Response Return
- Fetches `restaurant` and `table` for rich display on the customer web app.
- Returns `200 OK` with refreshed `expiresAt`.

---

## Flow 3 — POST /api/v1/restaurants/{restaurantId}/sessions/{sessionId}/close (Staff Close Session)

### Request Example
```http
POST /api/v1/restaurants/e3b0c442-.../sessions/7b8e1a22-.../close
Authorization: Bearer eyJhbGciOi... (Staff / Owner JWT)
```

### Step-by-Step Trace

#### STEP 1 — Security Filter & @PreAuthorize
- `JwtAuthenticationFilter` validates JWT and populates `SecurityContextHolder`.
- `@PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE', 'SUPER_ADMIN')")` verifies caller has authorized role.

#### STEP 2 — Tenant Ownership Verification
```java
if (!"SUPER_ADMIN".equals(callerRole)) {
    Restaurant restaurant = restaurantRepository.findById(session.getRestaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", session.getRestaurantId()));
    if (!restaurant.getOwnerId().equals(callerId) && !"EMPLOYEE".equals(callerRole)) {
        throw new UnauthorizedAccessException("You do not have permission to close this session");
    }
}
```
- Guarantees an owner or staff member can only close sessions belonging to their assigned restaurant.

#### STEP 3 — State Transition & Table Release
```java
session.setStatus(SessionStatus.CLOSED);
session.setEndedAt(LocalDateTime.now());

tableRepository.findById(session.getTableId()).ifPresent(table -> {
    table.setStatus(TableStatus.AVAILABLE);
    tableRepository.save(table);
});
```
- **SQL Executed**:
  ```sql
  UPDATE customer_sessions 
  SET status = 'CLOSED', ended_at = now(), updated_at = now() 
  WHERE id = '7b8e1a22-...';

  UPDATE restaurant_tables 
  SET status = 'AVAILABLE', updated_at = now() 
  WHERE id = 'c9a0d8e7-...';
  ```

#### STEP 4 — Response
```json
{
  "success": true,
  "message": "Session closed and table released",
  "data": null,
  "timestamp": "2026-09-09T15:15:00"
}
```

---

## Error Handling Matrix

| Scenario | Exception | HTTP Code | Response Message |
|---|---|:---:|---|
| Scanned QR code not found | `ResourceNotFoundException` | `404` | `"Table QR code is invalid"` |
| Restaurant is suspended | `ValidationException` | `400` | `"This restaurant is not currently active"` |
| Restaurant not accepting orders | `ValidationException` | `400` | `"This restaurant is currently not accepting orders"` |
| Session token expired | `SessionExpiredException` | `401` | `"Your session has expired. Please scan the QR code again."` |
| Invalid / missing session token | `SessionExpiredException` | `401` | `"Invalid session token"` |
| Unauthorized staff close attempt | `UnauthorizedAccessException` | `403` | `"You do not have permission to close this session"` |
| Negative or 0 guest count | `MethodArgumentNotValidException` | `400` | `"Guest count must be at least 1"` |
