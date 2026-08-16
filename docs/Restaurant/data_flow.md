# Restaurant Module — Complete Data Flow

> Every HTTP request through the Restaurant module traced step by step.
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
RestaurantController.java           — receives HTTP, extracts principal UUID & role, calls service
     │
     ▼
RestaurantService.java              — interface (the contract)
     │
     ▼
RestaurantServiceImpl.java          — business logic, ownership checks, calls mapper & repository
     │
     ▼
RestaurantMapper.java               — converts Request DTO ↔ Entity ↔ Response DTO
     │
     ▼
RestaurantRepository.java           — executes SQL via Spring Data JPA / Hibernate
     │
     ▼
restaurants table (PostgreSQL)      — persists row data
```

### Reverse Journey
```
PostgreSQL returns row(s)
     │
     ▼
RestaurantRepository returns Restaurant, Optional<Restaurant>, or Page<Restaurant>
     │
     ▼
RestaurantServiceImpl receives the managed entity
     │
     ▼
RestaurantMapper converts Restaurant entity → RestaurantResponse DTO
     │
     ▼
RestaurantServiceImpl returns RestaurantResponse / Page<RestaurantResponse> to Controller
     │
     ▼
RestaurantController wraps payload in ApiResponse.ok("...", data)
     │
     ▼
Jackson serializes ApiResponse to JSON → HTTP 200/201 Response sent to Client
```

---

## Flow 1 — POST /api/v1/restaurants (Create a Restaurant)

### Request Example
```http
POST /api/v1/restaurants
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
Content-Type: application/json

{
  "name": "Spice Garden",
  "description": "Authentic Indian cuisine & tandoor",
  "phone": "9876543210",
  "email": "contact@spicegarden.com",
  "address": "42 MG Road",
  "city": "Mumbai",
  "state": "Maharashtra",
  "country": "India",
  "zipcode": "400001",
  "timezone": "Asia/Kolkata",
  "taxPercentage": 5.00,
  "serviceCharge": 2.50,
  "allowCashPayment": true,
  "allowCardPayment": true,
  "allowUpi": true,
  "allowOnlinePayment": false,
  "acceptingOrders": true,
  "autoAcceptOrders": false
}
```

---

### Step-by-Step Trace

#### STEP 1 — Spring Security Filter Chain
`JwtAuthenticationFilter.java` intercepts the request:
- Reads `Authorization: Bearer <token>` header (`extractTokenFromRequest()`, line 120)
- Validates JWT signature and expiry via `jwtTokenProvider.validateToken(token)` (line 47)
- Extracts `userId` and `role` ("OWNER") claims from token (lines 50, 62)
- Builds `SimpleGrantedAuthority("ROLE_OWNER")` (line 85)
- Sets `UsernamePasswordAuthenticationToken` in `SecurityContextHolder` with `principal = userId.toString()` (lines 92–105)
- Passes request down the filter chain (`filterChain.doFilter()`, line 113)

#### STEP 2 — Method Security Authorization Check
`RestaurantController.java`:
```java
@PreAuthorize("hasRole('OWNER')") // Line 34
```
Spring Security evaluates the SpEL expression `hasRole('OWNER')`. It appends the `ROLE_` prefix and checks if `SecurityContextHolder` has `ROLE_OWNER`.
- **Match**: Proceed to controller method.
- **Mismatch**: Throws `AccessDeniedException` → handled by `GlobalExceptionHandler` → HTTP 403 Forbidden.

#### STEP 3 — Controller Method Invocation & DTO Extraction
`RestaurantController.java` (lines 33–41):
```java
@PostMapping
@PreAuthorize("hasRole('OWNER')")
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<RestaurantResponse> createRestaurant(
        @Valid @RequestBody CreateRestaurantRequest request) {
    UUID ownerId = getCurrentUserId(); // Line 38: extracts caller's UUID from JWT principal
    return ApiResponse.ok("Restaurant created successfully",
            restaurantService.createRestaurant(request, ownerId)); // Lines 39-40
}
```
- `@Valid` triggers Bean Validation on `CreateRestaurantRequest`.
- `@RequestBody` deserializes incoming JSON into Java record `CreateRestaurantRequest`.
- `getCurrentUserId()` (line 124) calls `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` and converts the string to `UUID`.

#### STEP 4 — Bean Validation Evaluation
`CreateRestaurantRequest.java`:
```java
public record CreateRestaurantRequest(
    @NotBlank String name,
    String description,
    String phone,
    @Email String email,
    String address,
    String city,
    String state,
    String country,
    String zipcode,
    String timezone,
    LocalTime openingTime,
    LocalTime closingTime,
    @DecimalMin("0.00") BigDecimal taxPercentage,
    @DecimalMin("0.00") BigDecimal serviceCharge,
    Boolean allowCashPayment,
    Boolean allowCardPayment,
    Boolean allowUpi,
    Boolean allowOnlinePayment,
    Boolean acceptingOrders,
    Boolean autoAcceptOrders
) {}
```
- `@NotBlank` on `name`: If null, empty, or whitespace → fails.
- `@Email` on `email`: If present and malformed → fails.
- `@DecimalMin("0.00")` on `taxPercentage` & `serviceCharge`: If negative → fails.
- *On failure*: Spring throws `MethodArgumentNotValidException` → caught by `GlobalExceptionHandler` → HTTP 400 Bad Request.

#### STEP 5 — Service Layer Execution
`RestaurantServiceImpl.java` (lines 31–36):
```java
@Override
@Transactional
public RestaurantResponse createRestaurant(CreateRestaurantRequest request, UUID ownerId) {
    Restaurant restaurant = restaurantMapper.toEntity(request, ownerId); // Line 33
    Restaurant saved = restaurantRepository.save(restaurant);            // Line 34
    return restaurantMapper.toResponse(saved);                          // Line 35
}
```

#### STEP 6 — Mapper Builds Entity (`CreateRestaurantRequest` → `Restaurant`)
`RestaurantMapper.java` (lines 46–70):
```java
public Restaurant toEntity(CreateRestaurantRequest request, UUID ownerId) {
    return Restaurant.builder()
            .ownerId(ownerId) // Injected securely from JWT, never client body
            .name(request.name())
            .description(request.description())
            .phone(request.phone())
            .email(request.email())
            .address(request.address())
            .city(request.city())
            .state(request.state())
            .country(request.country())
            .zipcode(request.zipcode())
            .timezone(request.timezone())
            .openingTime(request.openingTime())
            .closingTime(request.closingTime())
            .taxPercentage(request.taxPercentage() != null ? request.taxPercentage() : BigDecimal.ZERO)
            .serviceCharge(request.serviceCharge() != null ? request.serviceCharge() : BigDecimal.ZERO)
            .allowCashPayment(request.allowCashPayment() != null ? request.allowCashPayment() : true)
            .allowCardPayment(request.allowCardPayment() != null ? request.allowCardPayment() : true)
            .allowUpi(request.allowUpi() != null ? request.allowUpi() : true)
            .allowOnlinePayment(request.allowOnlinePayment() != null ? request.allowOnlinePayment() : false)
            .acceptingOrders(request.acceptingOrders() != null ? request.acceptingOrders() : true)
            .autoAcceptOrders(request.autoAcceptOrders() != null ? request.autoAcceptOrders() : false)
            .build();
}
```
*Note*: Entity defaults `status = RestaurantStatus.ACTIVE` via `@Builder.Default` on `Restaurant.java`.

#### STEP 7 — Database Persistence via JPA Repository
`restaurantRepository.save(restaurant)`:
1. JPA checks if entity has an ID. ID is null → Hibernate executes SQL `INSERT`.
2. PostgreSQL generates UUID `gen_random_uuid()` for `id` and sets `created_at`, `updated_at`.
3. Hibernate retrieves the generated ID and attaches the entity to the Persistence Context.

```sql
INSERT INTO restaurants (
    id, owner_id, name, description, phone, email, address, city, state,
    country, zipcode, timezone, opening_time, closing_time, status,
    tax_percentage, service_charge, allow_cash_payment, allow_card_payment,
    allow_upi, allow_online_payment, accepting_orders, auto_accept_orders,
    created_at, updated_at
) VALUES (
    gen_random_uuid(), 'bd82c11a-...', 'Spice Garden', 'Authentic Indian...', '9876543210',
    'contact@spicegarden.com', '42 MG Road', 'Mumbai', 'Maharashtra', 'India',
    '400001', 'Asia/Kolkata', NULL, NULL, 'ACTIVE', 5.00, 2.50, true, true,
    true, false, true, false, now(), now()
);
```

#### STEP 8 — Mapper Converts Entity to Response DTO
`RestaurantMapper.java` (lines 15–44):
- Maps `saved.getId()`, `saved.getOwnerId()`, all fields, and audit timestamps `createdAt`, `updatedAt` to `RestaurantResponse`.

#### STEP 9 — Controller Wraps in Envelope & Returns
- `ApiResponse.ok("Restaurant created successfully", response)` wraps payload.
- Controller method returns with HTTP 201 Created status.

#### STEP 10 — Client Response
```json
HTTP/1.1 201 Created
Content-Type: application/json

{
  "success": true,
  "message": "Restaurant created successfully",
  "data": {
    "id": "ac6a281c-e292-43ea-be90-b72b9295300c",
    "ownerId": "bd82c11a-c346-44d8-b333-8dcb0f43bfa3",
    "name": "Spice Garden",
    "description": "Authentic Indian cuisine & tandoor",
    "phone": "9876543210",
    "email": "contact@spicegarden.com",
    "address": "42 MG Road",
    "city": "Mumbai",
    "state": "Maharashtra",
    "country": "India",
    "zipcode": "400001",
    "timezone": "Asia/Kolkata",
    "openingTime": null,
    "closingTime": null,
    "status": "ACTIVE",
    "taxPercentage": 5.0,
    "serviceCharge": 2.5,
    "allowCashPayment": true,
    "allowCardPayment": true,
    "allowUpi": true,
    "allowOnlinePayment": false,
    "acceptingOrders": true,
    "autoAcceptOrders": false,
    "createdAt": "2026-08-16T15:43:04.123456",
    "updatedAt": "2026-08-16T15:43:04.123456"
  }
}
```

---

## Flow 2 — GET /api/v1/restaurants (List Restaurants)

### Flow 2a: When called by OWNER
1. **Security**: `@PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")` passes for OWNER.
2. **Controller** (lines 47–56):
   - `callerId = getCurrentUserId()` → e.g., `bd82c11a-...`
   - `role = getCurrentRole()` → `"OWNER"` (strips `"ROLE_"`)
   - Calls `restaurantService.getAllRestaurants(callerId, "OWNER", pageable)`
3. **Service Logic** (lines 39–49):
   - Branch: `if ("SUPER_ADMIN".equals(role))` is FALSE.
   - Executes: `restaurantRepository.findByOwnerId(callerId, pageable)`
4. **Repository / SQL**:
   ```sql
   SELECT * FROM restaurants 
   WHERE owner_id = 'bd82c11a-...' 
   ORDER BY created_at DESC 
   LIMIT 10 OFFSET 0;
   
   SELECT count(*) FROM restaurants WHERE owner_id = 'bd82c11a-...';
   ```
5. **Transformation**: `.map(restaurantMapper::toResponse)` converts `Page<Restaurant>` to `Page<RestaurantResponse>`.
6. **Result**: Owner sees ONLY restaurants belonging to their account.

### Flow 2b: When called by SUPER_ADMIN
1. **Security**: Passes for SUPER_ADMIN.
2. **Controller**: `role = "SUPER_ADMIN"`. Calls service.
3. **Service Logic**:
   - Branch: `if ("SUPER_ADMIN".equals(role))` is TRUE.
   - Executes: `restaurantRepository.findAll(pageable)`
4. **Repository / SQL**:
   ```sql
   SELECT * FROM restaurants 
   ORDER BY created_at DESC 
   LIMIT 10 OFFSET 0;
   
   SELECT count(*) FROM restaurants;
   ```
5. **Result**: Super Admin sees ALL restaurants across all owners in the entire platform.

---

## Flow 3 — GET /api/v1/restaurants/{id} (Get One Restaurant)

### Request Example
```http
GET /api/v1/restaurants/ac6a281c-e292-43ea-be90-b72b9295300c
Authorization: Bearer eyJhbGci...
```

### Trace & Ownership Isolation Check
1. **Controller** (lines 61–68):
   - Extracts `callerId` and `role`.
   - Calls `restaurantService.getRestaurantById(id, callerId, role)`.
2. **Service** (`RestaurantServiceImpl.java`, lines 52–65):
   ```java
   Restaurant restaurant = restaurantRepository.findById(id)
           .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id)); // Line 54

   // Ownership Isolation Check (Lines 57-59)
   if (!"SUPER_ADMIN".equals(role) && !restaurant.getOwnerId().equals(callerId)) {
       throw new UnauthorizedAccessException("You do not own this restaurant");
   }

   return restaurantMapper.toResponse(restaurant); // Line 61
   ```
3. **Outcomes**:
   - **Case A (Not Found)**: If UUID doesn't exist in DB → throws `ResourceNotFoundException("Restaurant", id)` → HTTP 404 Not Found:
     `{"success": false, "message": "Restaurant not found with id: ac6a281c..."}`
   - **Case B (Wrong Owner)**: If caller is OWNER Bob and restaurant belongs to OWNER Alice → `!restaurant.getOwnerId().equals(callerId)` triggers → throws `UnauthorizedAccessException` → HTTP 403 Forbidden:
     `{"success": false, "message": "You do not own this restaurant"}`
   - **Case C (Authorized Owner or Super Admin)**: Maps entity to `RestaurantResponse` → returns HTTP 200 with payload.

---

## Flow 4 — PUT /api/v1/restaurants/{id} (Update Identity / Location)

### Request Example
```http
PUT /api/v1/restaurants/ac6a281c-e292-43ea-be90-b72b9295300c
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "name": "Spice Garden Premium",
  "city": "Pune",
  "phone": "9998887776"
}
```

### Trace & Dirty Checking Mechanism
1. **Controller** (lines 73–81):
   - `@PreAuthorize("hasRole('OWNER')")` ensures caller is an Owner.
   - Extracts `ownerId = getCurrentUserId()`.
   - Calls `restaurantService.updateRestaurant(id, request, ownerId)`.
2. **Service Execution** (`RestaurantServiceImpl.java`, lines 66–75):
   ```java
   @Override
   @Transactional
   public RestaurantResponse updateRestaurant(UUID id, UpdateRestaurantRequest request, UUID ownerId) {
       Restaurant restaurant = restaurantRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

       if (!restaurant.getOwnerId().equals(ownerId))
           throw new UnauthorizedAccessException("You do not own this restaurant");

       restaurantMapper.applyUpdate(restaurant, request);  // Mapper applies non-null values
       return restaurantMapper.toResponse(restaurant);     // Hibernate dirty checking issues UPDATE
   }
   ```
3. **Mapper In-Place Mutation** (`RestaurantMapper.java`, lines 73–86):
   - Only non-null fields in `UpdateRestaurantRequest` are copied to the entity:
   ```java
   if (request.name() != null)  restaurant.setName(request.name());  // "Spice Garden Premium"
   if (request.city() != null)  restaurant.setCity(request.city());  // "Pune"
   if (request.phone() != null) restaurant.setPhone(request.phone()); // "9998887776"
   // description, address, etc. are null in request -> skipped, preserving existing values
   ```
4. **Hibernate Automatic Dirty Checking**:
   - Because `restaurant` is attached to the active `@Transactional` persistence context, Hibernate compares entity snapshot values with current values at transaction commit.
   - Hibernate detects mutations on `name`, `city`, and `phone`.
   - Hibernate generates and executes the `UPDATE` statement automatically without calling `repository.save()`:
   ```sql
   UPDATE restaurants 
   SET name = 'Spice Garden Premium', city = 'Pune', phone = '9998887776', updated_at = now()
   WHERE id = 'ac6a281c-e292-43ea-be90-b72b9295300c';
   ```

---

## Flow 5 — PATCH /api/v1/restaurants/{id}/status (Super Admin Status Change)

### Request Example
```http
PATCH /api/v1/restaurants/ac6a281c-e292-43ea-be90-b72b9295300c/status?value=SUSPENDED
Authorization: Bearer eyJhbGci... (SUPER_ADMIN token)
```

### Trace
1. **Controller** (lines 86–93):
   ```java
   @PatchMapping("/{id}/status")
   @PreAuthorize("hasRole('SUPER_ADMIN')")
   public ApiResponse<RestaurantResponse> updateStatus(
           @PathVariable UUID id,
           @RequestParam RestaurantStatus value) {
       return ApiResponse.ok("Restaurant status updated successfully",
               restaurantService.updateStatus(id, value));
   }
   ```
   - Only `SUPER_ADMIN` can invoke this endpoint.
   - Spring converts request param string `"SUSPENDED"` to enum constant `RestaurantStatus.SUSPENDED`.
   - *Invalid enum handling*: If caller passes `?value=INVALID`, Spring throws `MethodArgumentTypeMismatchException` → caught by `GlobalExceptionHandler` → HTTP 400 Bad Request.
2. **Service Execution** (`RestaurantServiceImpl.java`, lines 92–98):
   ```java
   @Override
   @Transactional
   public RestaurantResponse updateStatus(UUID id, RestaurantStatus status) {
       Restaurant restaurant = restaurantRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

       restaurant.setStatus(status);
       return restaurantMapper.toResponse(restaurant);
   }
   ```
3. **Database Flush**:
   - `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` on `Restaurant.java` ensures Hibernate sends `'SUSPENDED'::restaurant_status` to PostgreSQL.
   ```sql
   UPDATE restaurants 
   SET status = 'SUSPENDED', updated_at = now()
   WHERE id = 'ac6a281c-e292-43ea-be90-b72b9295300c';
   ```

---

## Flow 6 — GET /api/v1/restaurants/{id}/settings (Get Settings)

### Request Example
```http
GET /api/v1/restaurants/ac6a281c-e292-43ea-be90-b72b9295300c/settings
Authorization: Bearer eyJhbGci... (OWNER token)
```

### Trace
1. **Controller** (lines 98–104):
   ```java
   @GetMapping("/{id}/settings")
   @PreAuthorize("hasRole('OWNER')")
   public ApiResponse<RestaurantResponse> getSettings(@PathVariable UUID id) {
       UUID ownerId = getCurrentUserId();
       return ApiResponse.ok("Settings fetched successfully",
               restaurantService.getRestaurantById(id, ownerId, "OWNER"));
   }
   ```
2. **Reusing Core Method**:
   - Controller delegates to `restaurantService.getRestaurantById(id, ownerId, "OWNER")`.
   - Verifies ownership (owner must own this restaurant).
   - Returns the complete `RestaurantResponse` which embeds the 8 settings fields (`taxPercentage`, `serviceCharge`, `allowCashPayment`, `allowCardPayment`, `allowUpi`, `allowOnlinePayment`, `acceptingOrders`, `autoAcceptOrders`).

---

## Flow 7 — PUT /api/v1/restaurants/{id}/settings (Update Settings)

### Request Example
```http
PUT /api/v1/restaurants/ac6a281c-e292-43ea-be90-b72b9295300c/settings
Authorization: Bearer eyJhbGci... (OWNER token)
Content-Type: application/json

{
  "taxPercentage": 8.50,
  "allowUpi": false,
  "autoAcceptOrders": true
}
```

### Trace
1. **Controller** (lines 109–117):
   - `@PreAuthorize("hasRole('OWNER')")` checks authority.
   - `@Valid @RequestBody RestaurantSettingsRequest request` validates `@DecimalMin("0.00")`.
   - Calls `restaurantService.updateSettings(id, request, ownerId)`.
2. **Service Execution** (`RestaurantServiceImpl.java`, lines 78–88):
   ```java
   @Override
   @Transactional
   public RestaurantResponse updateSettings(UUID id, RestaurantSettingsRequest request, UUID ownerId) {
       Restaurant restaurant = restaurantRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

       if (!restaurant.getOwnerId().equals(ownerId))
           throw new UnauthorizedAccessException("You do not own this restaurant");

       restaurantMapper.applySettings(restaurant, request);
       return restaurantMapper.toResponse(restaurant);
   }
   ```
3. **Mapper Mutates Only Settings Fields** (`RestaurantMapper.java`, lines 89–98):
   ```java
   public void applySettings(Restaurant restaurant, RestaurantSettingsRequest request) {
       if (request.taxPercentage() != null)    restaurant.setTaxPercentage(request.taxPercentage());   // 8.50
       if (request.serviceCharge() != null)    restaurant.setServiceCharge(request.serviceCharge());
       if (request.allowCashPayment() != null) restaurant.setAllowCashPayment(request.allowCashPayment());
       if (request.allowCardPayment() != null) restaurant.setAllowCardPayment(request.allowCardPayment());
       if (request.allowUpi() != null)         restaurant.setAllowUpi(request.allowUpi());             // false
       if (request.allowOnlinePayment() != null) restaurant.setAllowOnlinePayment(request.allowOnlinePayment());
       if (request.acceptingOrders() != null)  restaurant.setAcceptingOrders(request.acceptingOrders());
       if (request.autoAcceptOrders() != null) restaurant.setAutoAcceptOrders(request.autoAcceptOrders()); // true
   }
   ```
4. **Database UPDATE (Dirty Checking)**:
   ```sql
   UPDATE restaurants 
   SET tax_percentage = 8.50, allow_upi = false, auto_accept_orders = true, updated_at = now()
   WHERE id = 'ac6a281c-e292-43ea-be90-b72b9295300c';
   ```

---

## Flow 8 — DELETE /api/v1/restaurants/{id} (Soft Delete)

### Trace
1. **Service Execution** (`RestaurantServiceImpl.java`, lines 101–112):
   ```java
   @Override
   @Transactional
   public void deleteRestaurant(UUID id, UUID ownerId) {
       Restaurant restaurant = restaurantRepository.findById(id)
               .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

       if (!restaurant.getOwnerId().equals(ownerId)) {
           throw new UnauthorizedAccessException("You do not own this restaurant");
       }

       restaurant.setStatus(RestaurantStatus.INACTIVE);
   }
   ```
2. **Soft Delete vs Hard Delete**:
   - The row is **never deleted** with `DELETE FROM restaurants WHERE id = ?`.
   - Foreign keys to menus, orders, tables, and staff remain intact.
   - Status is set to `INACTIVE`. The restaurant is hidden from customer browsing but historical order and financial reports remain intact.

---

## Summary of Status Codes & Exceptions

| Situation | Exception Thrown | HTTP Status | Response Body |
|---|---|---|---|
| No JWT provided / Expired JWT | `AuthenticationException` | 401 Unauthorized | `{"success":false,"message":"Authentication required"}` |
| Non-Owner calling Owner-only route | `AccessDeniedException` | 403 Forbidden | `{"success":false,"message":"Access denied"}` |
| Owner trying to access another Owner's restaurant | `UnauthorizedAccessException` | 403 Forbidden | `{"success":false,"message":"You do not own this restaurant"}` |
| Restaurant ID not in DB | `ResourceNotFoundException` | 404 Not Found | `{"success":false,"message":"Restaurant not found with id: ..."}` |
| Request body fails validation (`@NotBlank`, `@DecimalMin`) | `MethodArgumentNotValidException` | 400 Bad Request | `{"success":false,"message":"Validation failed: ..."}` |
| Invalid Enum value in param (`?value=BOGUS`) | `MethodArgumentTypeMismatchException` | 400 Bad Request | `{"success":false,"message":"Invalid parameter value: ..."}` |
| Successful operation | None | 200 OK / 201 Created | `{"success":true,"message":"...","data":{...}}` |
