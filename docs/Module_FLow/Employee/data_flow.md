# Employee Module — Complete Data Flow

> Every HTTP request through the Employee module traced step by step.
> Line numbers reference the actual files in the codebase.

---

## Architecture overview

```
HTTP Request
     |
     v
EmployeeController.java     — receives HTTP, extracts caller from SecurityContextHolder
     |
     v
EmployeeService.java        — interface (the contract)
     |
     v
EmployeeServiceImpl.java    — business logic, validates ownership, calls repo + mapper
     |
     v
EmployeeMapper.java         — converts Employee entity <-> EmployeeResponse DTO
     |
     v
EmployeeRepository.java     — speaks to PostgreSQL
     |
     v
employees table (PostgreSQL)
```

The reverse journey:
```
PostgreSQL returns data
     |
     v
EmployeeRepository returns Optional<Employee> or List<Employee>
     |
     v
EmployeeServiceImpl receives the entity/list
     |
     v
EmployeeMapper converts entity → EmployeeResponse
     |
     v
EmployeeServiceImpl returns EmployeeResponse to controller
     |
     v
EmployeeController wraps it in ApiResponse<EmployeeResponse>
     |
     v
Spring serializes to JSON → HTTP Response sent to client
```

---

## Flow 1 — POST /api/v1/restaurants/{restaurantId}/employees (Assign Employee)

### Request example
```
POST /api/v1/restaurants/3fa85f64-5717-4562-b3fc-2c963f66afa6/employees
Authorization: Bearer eyJhbGci...   ← OWNER token
Content-Type: application/json

{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "CHEF"
}
```

---

### Step-by-step trace

**STEP 1 — Spring Security Filter Chain (before controller)**
```
JwtAuthenticationFilter runs on every request:
  reads Authorization header → extracts JWT
  validates token → extracts userId (UUID) as principal
  extracts role → sets ROLE_OWNER as GrantedAuthority
  populates SecurityContextHolder
→ Authentication is now available for the rest of the request
```

**STEP 2 — @PreAuthorize fires**
```
EmployeeController.java
  @PreAuthorize("hasRole('OWNER')")
  Checks: does SecurityContextHolder contain ROLE_OWNER?
  YES → proceed
  NO  → 403 Forbidden
```

**STEP 3 — Controller method begins**
```java
// EmployeeController.java
@PostMapping
@PreAuthorize("hasRole('OWNER')")
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<EmployeeResponse> assignEmployee(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody AssignEmployeeRequest request) {
    UUID ownerId = getCurrentUserId();
//                 ^^^^^^^^^^^^^^^^^
//                 Reads from SecurityContextHolder — the UUID stored as principal
//                 by JwtAuthenticationFilter when it validated the token
```

**STEP 4 — @Valid runs Bean Validation on AssignEmployeeRequest**
```
AssignEmployeeRequest fields:
  @NotNull UUID userId    → fails if null
  @NotNull EmployeeRole role → fails if null or not a valid enum value

IF validation fails:
  Spring throws MethodArgumentNotValidException → 400 Bad Request

IF validation passes:
  request.userId() → "550e8400-e29b-41d4-a716-446655440000"
  request.role()   → EmployeeRole.CHEF
```

**STEP 5 — Controller calls service**
```java
// EmployeeController.java
return ApiResponse.ok("Employee assigned successfully",
        employeeService.assignEmployee(restaurantId, request, ownerId));
```

**STEP 6 — EmployeeServiceImpl.assignEmployee() begins**
```java
// EmployeeServiceImpl.java
@Transactional
@Override
public EmployeeResponse assignEmployee(UUID restaurantId, AssignEmployeeRequest request, UUID ownerId) {
```

**STEP 7 — Restaurant existence + ownership check**
```java
Restaurant restaurant = restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
// SQL: SELECT * FROM restaurants WHERE id = '3fa85f64...'
// If not found → ResourceNotFoundException → 404

if (!restaurant.getOwnerId().equals(ownerId)) {
    throw new UnauthorizedAccessException("You do not own this restaurant");
}
// Compares restaurant.owner_id (from DB) to ownerId (from JWT)
// If mismatch → UnauthorizedAccessException → 403
// This is the cross-tenant guard — prevents Owner A from adding employees to Owner B's restaurant
```

**STEP 8 — User validation**
```java
User user = userRepository.findById(request.userId())
        .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
// SQL: SELECT * FROM users WHERE id = '550e8400...'
// If user doesn't exist → 404

if (user.getRole() != UserRole.EMPLOYEE) {
    throw new ValidationException("Only users with platform role EMPLOYEE can be assigned to a restaurant");
}
// Checks the platform-level role (users.role column)
// Rejects OWNER and SUPER_ADMIN accounts from being restaurant employees
// Only UserRole.EMPLOYEE accounts can be assigned → 400 if not
```

**STEP 9 — Duplicate assignment check**
```java
if (employeeRepository.existsByUserIdAndRestaurantId(request.userId(), restaurantId)) {
    throw new ConflictException("User is already assigned to this restaurant");
}
// SQL: SELECT COUNT(*) > 0 FROM employees WHERE user_id = '550e8400...' AND restaurant_id = '3fa85f64...'
// DB-level UNIQUE(user_id, restaurant_id) is the hard guard
// This service-level check gives a friendly 409 message before hitting the constraint
```

**STEP 10 — Map to entity and save**
```java
Employee employee = employeeMapper.toEntity(restaurantId, request);
// Creates: Employee.builder()
//            .restaurantId(restaurantId)   ← UUID, not a FK object
//            .userId(request.userId())      ← UUID, not a FK object
//            .role(EmployeeRole.CHEF)
//            .build()
// isActive = true (set by @Builder.Default in Employee.java)
// id = null (Hibernate generates it on save)

return employeeMapper.toResponse(employeeRepository.save(employee));
// save() → INSERT INTO employees (id, user_id, restaurant_id, role, is_active, created_at, updated_at)
//           VALUES (gen_random_uuid(), '550e8400...', '3fa85f64...', 'CHEF', true, now(), now())
// After save: employee.getId() is populated by PostgreSQL
```

**STEP 11 — EmployeeMapper.toResponse() executes**
```java
// EmployeeMapper.java
public EmployeeResponse toResponse(Employee employee) {
    return new EmployeeResponse(
            employee.getId(),           // UUID generated by DB
            employee.getUserId(),       // "550e8400..."
            employee.getRestaurantId(), // "3fa85f64..."
            employee.getRole(),         // EmployeeRole.CHEF
            employee.isActive(),        // true
            employee.getCreatedAt(),    // now()
            employee.getUpdatedAt()     // now()
    );
}
// Note: EmployeeResponse is a record — uses the canonical constructor, not a builder
```

**STEP 12 — @Transactional commits**
```
Transaction commits here → INSERT is permanent in the employees table
```

**STEP 13 — HTTP Response**
```
HTTP/1.1 201 Created
Content-Type: application/json

{
  "success": true,
  "message": "Employee assigned successfully",
  "data": {
    "id": "a1b2c3d4-...",
    "userId": "550e8400-...",
    "restaurantId": "3fa85f64-...",
    "role": "CHEF",
    "isActive": true,
    "createdAt": "2026-08-24T09:00:00",
    "updatedAt": "2026-08-24T09:00:00"
  }
}
```

---

## Flow 2 — GET /api/v1/restaurants/{restaurantId}/employees (List Employees)

### Request example
```
GET /api/v1/restaurants/3fa85f64-5717-4562-b3fc-2c963f66afa6/employees
Authorization: Bearer eyJhbGci...   ← OWNER or SUPER_ADMIN token
```

---

### Step-by-step trace

**STEP 1 — @PreAuthorize**
```java
@PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
// Both roles allowed — but SUPER_ADMIN bypasses ownership check in service
```

**STEP 2 — Controller extracts callerId + role**
```java
UUID callerId = getCurrentUserId();   // from SecurityContextHolder principal
String role = getCurrentRole();       // "OWNER" or "SUPER_ADMIN" (ROLE_ prefix stripped)
```

**STEP 3 — EmployeeServiceImpl.getEmployees()**
```java
// Verify restaurant exists
Restaurant restaurant = restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

// SUPER_ADMIN bypass — sees any restaurant's employees without ownership
if (!"SUPER_ADMIN".equals(callerRole) && !restaurant.getOwnerId().equals(callerId)) {
    throw new UnauthorizedAccessException("You do not own this restaurant");
}
// OWNER → must own the restaurant
// SUPER_ADMIN → skips ownership check entirely
```

**STEP 4 — Repository query**
```java
return employeeRepository.findByRestaurantIdAndIsActiveTrue(restaurantId)
        .stream()
        .map(employeeMapper::toResponse)
        .toList();
// SQL: SELECT * FROM employees WHERE restaurant_id = '3fa85f64...' AND is_active = true
// Returns only active employees — deactivated (soft-deleted) employees are excluded
// .stream().map() → converts each Employee entity to EmployeeResponse
// .toList() → collects into an immutable List<EmployeeResponse>
```

**STEP 5 — Response**
```
HTTP 200 OK
{
  "success": true,
  "message": "Employees fetched successfully",
  "data": [
    { "id": "...", "role": "CHEF", "isActive": true, ... },
    { "id": "...", "role": "WAITER", "isActive": true, ... }
  ]
}
```

---

## Flow 3 — PATCH /api/v1/restaurants/{restaurantId}/employees/{employeeId}/role (Update Role)

### Request example
```
PATCH /api/v1/restaurants/3fa85f64-.../employees/a1b2c3d4-.../role
Authorization: Bearer eyJhbGci...   ← OWNER token
Content-Type: application/json

{ "role": "MANAGER" }
```

---

### Step-by-step trace

**STEP 1 — @PreAuthorize**
```java
@PreAuthorize("hasRole('OWNER')")
// Only OWNER can change job roles
```

**STEP 2 — EmployeeServiceImpl.updateEmployeeRole()**
```java
@Transactional   // enables dirty checking for the update
@Override
public EmployeeResponse updateEmployeeRole(UUID restaurantId, UUID employeeId,
        UpdateEmployeeRoleRequest request, UUID ownerId) {

    // 1. Verify restaurant + ownership
    Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

    if (!restaurant.getOwnerId().equals(ownerId)) {
        throw new UnauthorizedAccessException("You do not own this restaurant");
    }

    // 2. Fetch employee scoped to this restaurant (cross-restaurant guard)
    Employee employee = employeeRepository.findByIdAndRestaurantId(employeeId, restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    // SQL: SELECT * FROM employees WHERE id = 'a1b2c3d4...' AND restaurant_id = '3fa85f64...'
    // If the employeeId exists but belongs to a DIFFERENT restaurant → 404 (not 403)
    // This prevents information leakage — "not found" is safer than "access denied"

    // 3. Apply update — dirty checking handles the SQL
    employeeMapper.applyRoleUpdate(request, employee);
    // Sets employee.role = EmployeeRole.MANAGER on the managed entity
    // No explicit save() needed

    return employeeMapper.toResponse(employee);
    // Hibernate: at end of @Transactional:
    // UPDATE employees SET role = 'MANAGER', updated_at = now() WHERE id = 'a1b2c3d4...'
}
```

**STEP 3 — Response**
```
HTTP 200 OK
{
  "success": true,
  "message": "Employee role updated successfully",
  "data": {
    "id": "a1b2c3d4-...",
    "role": "MANAGER",
    "isActive": true,
    ...
  }
}
```

---

## Flow 4 — DELETE /api/v1/restaurants/{restaurantId}/employees/{employeeId} (Deactivate)

### Request example
```
DELETE /api/v1/restaurants/3fa85f64-.../employees/a1b2c3d4-...
Authorization: Bearer eyJhbGci...   ← OWNER token
```

---

### Step-by-step trace

**STEP 1 — @PreAuthorize**
```java
@PreAuthorize("hasRole('OWNER')")
@ResponseStatus(HttpStatus.NO_CONTENT)
// 204 No Content — no body returned
```

**STEP 2 — EmployeeServiceImpl.deactivateEmployee()**
```java
@Transactional
@Override
public void deactivateEmployee(UUID restaurantId, UUID employeeId, UUID ownerId) {

    // 1. Restaurant + ownership check
    Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));

    if (!restaurant.getOwnerId().equals(ownerId)) {
        throw new UnauthorizedAccessException("You do not own this restaurant");
    }

    // 2. Fetch employee scoped to this restaurant
    Employee employee = employeeRepository.findByIdAndRestaurantId(employeeId, restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

    // 3. Soft delete — set isActive = false
    employee.setActive(false);
    // Hibernate dirty checking at end of transaction:
    // UPDATE employees SET is_active = false, updated_at = now() WHERE id = 'a1b2c3d4...'
    //
    // The row is NEVER deleted. The employee record stays in the DB.
    // They simply stop appearing in getEmployees() (which filters isActiveTrue)
}
```

**STEP 3 — Response**
```
HTTP 204 No Content
(empty body)
```

---

## Data objects summary

| Object | Type | Direction | Notes |
|--------|------|-----------|-------|
| `AssignEmployeeRequest` | record DTO | IN (client → service) | userId + role |
| `UpdateEmployeeRoleRequest` | record DTO | IN (client → service) | role only |
| `Employee` | JPA Entity | Internal only | Maps to employees table |
| `EmployeeResponse` | record DTO | OUT (service → client) | All fields except nothing sensitive |

---

## Why UUID fields instead of @ManyToOne relationships?

```java
// Employee.java uses:
private UUID userId;
private UUID restaurantId;

// NOT:
@ManyToOne private User user;
@ManyToOne private Restaurant restaurant;
```

Reason: The Employee module only needs the IDs.
- Using `@ManyToOne` would force Hibernate to JOIN and load the full User and Restaurant
  objects every time an Employee is fetched — even when you only need the employee list.
- This is the N+1 query problem: for 20 employees, you'd get 1 employee query + 20 user queries + 20 restaurant queries.
- UUID fields avoid this: one clean query on the employees table only.

---

## Cross-restaurant isolation — the guard pattern

```
Every write/read operation checks:
  1. Does the restaurant exist? (404 if not)
  2. Does the caller own it? (403 if not)
  3. Does the employee belong to THIS restaurant? (404 if not)

Step 3 uses: findByIdAndRestaurantId(employeeId, restaurantId)
  → WHERE id = ? AND restaurant_id = ?

Without step 3:
  Owner A could call PATCH /restaurants/ownerA-restaurant/employees/ownerB-employee-id/role
  findById(ownerB-employee-id) would succeed even though it belongs to restaurant B
  → Owner A modifies Owner B's employee — data breach

With step 3:
  ownerB-employee-id + ownerA-restaurantId → no row found → 404
  → Cross-restaurant access silently blocked
```
