# Employee Module — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### Employee.java — The Entity

**What it is**: A JPA entity mapping to the `employees` table.
An `Employee` row is not a person — it's an **assignment record**: "User X works at Restaurant Y as a CHEF."

**Why it extends `BaseEntity`**:
`BaseEntity` provides `id` (UUID PK), `createdAt`, and `updatedAt` automatically.
Every module entity extends it — avoids repeating the same 3 fields everywhere.

**Key design decisions**:

```java
@Column(name = "user_id", nullable = false)
private UUID userId;

@Column(name = "restaurant_id", nullable = false)
private UUID restaurantId;
// These are UUID fields, NOT @ManyToOne relationships.
// See "Why UUID fields instead of @ManyToOne" section below.

@Enumerated(EnumType.STRING)
@JdbcTypeCode(SqlTypes.NAMED_ENUM)
@Column(name = "role", nullable = false)
private EmployeeRole role;
// EnumType.STRING → stores "CHEF" not 2 (ordinal)
// @JdbcTypeCode(SqlTypes.NAMED_ENUM) → Hibernate 6 fix for PostgreSQL custom enum types

@Builder.Default
private boolean isActive = true;
// @Builder.Default ensures isActive = true when using Employee.builder()...build()
// Without this, Lombok's builder would set boolean to false (Java default)
// No need to set isActive explicitly when assigning a new employee
```

---

### EmployeeRole.java — Enum

**What it is**: The restaurant job title for an assigned employee.
Completely separate from `UserRole` (platform role).

```
UserRole:     SUPER_ADMIN | OWNER | EMPLOYEE
              ↑ Who are you on the platform?

EmployeeRole: MANAGER | CHEF | WAITER | CASHIER
              ↑ What is your job in this restaurant?
```

A user with `UserRole.EMPLOYEE` gets assigned an `EmployeeRole` when a restaurant owner
hires them. The same person could theoretically be a `CHEF` at Restaurant A and a `WAITER`
at Restaurant B — two rows in `employees`, same `user_id`.

---

### V5__create_employees.sql — Flyway Migration

**What it creates**:
```sql
CREATE TABLE employees (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    restaurant_id   UUID NOT NULL REFERENCES restaurants(id),
    role            employee_role NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, restaurant_id)
);
```

**Key constraint: `UNIQUE(user_id, restaurant_id)`**:
A user can only be assigned to the same restaurant once.
The DB enforces this at the hard level. The service checks `existsByUserIdAndRestaurantId`
before insert to give a friendly 409 instead of a cryptic DB constraint violation.

**Why `employee_role` not VARCHAR?**:
`employee_role` is a PostgreSQL custom enum type (created in V1__create_enums.sql).
Only valid values can be stored — the DB rejects anything outside `MANAGER, CHEF, WAITER, CASHIER`.

---

### EmployeeRepository.java — The Repository

**Custom queries beyond the standard JPA methods**:

```java
List<Employee> findByRestaurantIdAndIsActiveTrue(UUID restaurantId);
// Spring Data parses: findBy + RestaurantId + And + IsActive + True
// SQL: SELECT * FROM employees WHERE restaurant_id = ? AND is_active = true
// Used by getEmployees() — returns only active staff, excludes deactivated ones

boolean existsByUserIdAndRestaurantId(UUID userId, UUID restaurantId);
// SQL: SELECT COUNT(*) > 0 FROM employees WHERE user_id = ? AND restaurant_id = ?
// Used by assignEmployee() duplicate check — avoids loading the full entity just to check existence

Optional<Employee> findByIdAndRestaurantId(UUID id, UUID restaurantId);
// SQL: SELECT * FROM employees WHERE id = ? AND restaurant_id = ?
// Critical for cross-restaurant isolation — see guard pattern section below
```

---

### EmployeeService.java — The Interface

**4 method signatures**:

```java
EmployeeResponse assignEmployee(UUID restaurantId, AssignEmployeeRequest request, UUID ownerId);
List<EmployeeResponse> getEmployees(UUID restaurantId, UUID callerId, String callerRole);
EmployeeResponse updateEmployeeRole(UUID restaurantId, UUID employeeId, UpdateEmployeeRoleRequest request, UUID ownerId);
void deactivateEmployee(UUID restaurantId, UUID employeeId, UUID ownerId);
```

**Why `callerRole` as a String in `getEmployees`?**:
`getEmployees` allows both `OWNER` and `SUPER_ADMIN`. Inside the service, we check if the
caller is `SUPER_ADMIN` to bypass the ownership check. The role is extracted from the JWT
as a String (`"SUPER_ADMIN"`) — passing it as a String avoids importing `UserRole` into
the employee service (keeps modules decoupled).

---

### EmployeeServiceImpl.java — The Implementation

**Class-level `@Transactional(readOnly = true)`**:
- All 4 methods inherit read-only by default.
- `assignEmployee`, `updateEmployeeRole`, `deactivateEmployee` override with `@Transactional`
  (writable transaction) since they modify the DB.
- `getEmployees` stays read-only — it only SELECTs.

**Ownership check pattern (repeated in all 3 write methods)**:
```java
Restaurant restaurant = restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant", restaurantId));
if (!restaurant.getOwnerId().equals(ownerId)) {
    throw new UnauthorizedAccessException("You do not own this restaurant");
}
```
This is the cross-tenant guard. Even if an OWNER's JWT is valid, they can only
modify employees in restaurants they own. A restaurant owner can't manage another
owner's staff.

**Why `findByIdAndRestaurantId` instead of just `findById`?**:
```java
// WRONG pattern (vulnerable):
Employee employee = employeeRepository.findById(employeeId)
        .orElseThrow(() -> new ResourceNotFoundException(...));
// Problem: employeeId might belong to a DIFFERENT restaurant.
// You'd load and modify it — cross-restaurant data breach.

// CORRECT pattern:
Employee employee = employeeRepository.findByIdAndRestaurantId(employeeId, restaurantId)
        .orElseThrow(() -> new ResourceNotFoundException(...));
// Only returns a result if the employee belongs to THIS restaurant.
// If employeeId belongs to another restaurant → no row found → 404.
```

**Dirty checking for `updateEmployeeRole`**:
```java
employeeMapper.applyRoleUpdate(request, employee);
// This just calls employee.setRole(request.role()) on the managed entity.
// No explicit save() needed.
// At the end of @Transactional, Hibernate sees role changed:
// UPDATE employees SET role = 'MANAGER', updated_at = now() WHERE id = ?
```

**Dirty checking for `deactivateEmployee`**:
```java
employee.setActive(false);
// Hibernate dirty check:
// UPDATE employees SET is_active = false, updated_at = now() WHERE id = ?
// The row is NEVER deleted — this is a soft deactivation.
```

---

### EmployeeMapper.java — The Mapper

**3 methods**:

```java
// 1. Entity creation
public Employee toEntity(UUID restaurantId, AssignEmployeeRequest request) {
    return Employee.builder()
            .restaurantId(restaurantId)
            .userId(request.userId())
            .role(request.role())
            .build();
    // isActive is not set — @Builder.Default handles it → true
}

// 2. Entity → DTO
public EmployeeResponse toResponse(Employee employee) {
    return new EmployeeResponse(   // ← constructor, NOT .builder() — EmployeeResponse is a record
            employee.getId(),
            employee.getUserId(),
            employee.getRestaurantId(),
            employee.getRole(),
            employee.isActive(),
            employee.getCreatedAt(),
            employee.getUpdatedAt()
    );
}

// 3. Apply update (used for dirty checking)
public void applyRoleUpdate(UpdateEmployeeRoleRequest request, Employee employee) {
    employee.setRole(request.role());  // ← Employee type, NOT Optional<Employee>
}
```

---

### EmployeeController.java — The HTTP Layer

**`SecurityContextHolder` approach**:
```java
private UUID getCurrentUserId() {
    String principal = (String) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
    return UUID.fromString(principal);
}
// The principal is the userId.toString() — set by JwtAuthenticationFilter
// when it validates the token and builds the Authentication object.
// Cleaner than injecting JwtTokenProvider and manually parsing the header.

private String getCurrentRole() {
    return SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .iterator().next()
            .getAuthority()
            .replace("ROLE_", "");
    // JwtAuthenticationFilter sets authority as "ROLE_OWNER"
    // We strip "ROLE_" → "OWNER" (matches what the service .equals() checks)
}
```

**Why `@PatchMapping` for role update (not `@PutMapping`)?**:
- `PUT` = full resource replacement (you send the entire object)
- `PATCH` = partial update (you send only the fields being changed)
- `UpdateEmployeeRoleRequest` only has one field (`role`) — this is a partial update → `PATCH`

**Why `@ResponseStatus(HttpStatus.NO_CONTENT)` on `deactivateEmployee`?**:
The method returns `void` — there is no data to send back.
`204 No Content` is the correct HTTP status for successful operations with no response body.

---

## Key Concepts Summary

### Why `EmployeeRole` and `UserRole` are separate enums

**`UserRole`** is a platform-level concept. It defines what kind of account this is:
- `SUPER_ADMIN` — manages the whole platform
- `OWNER` — owns restaurants
- `EMPLOYEE` — can be assigned to restaurants

**`EmployeeRole`** is a business-level concept. It defines the job function:
- `MANAGER`, `CHEF`, `WAITER`, `CASHIER`

They answer different questions:
- "Can this person log into the system?" → `UserRole`
- "What do they do in the kitchen?" → `EmployeeRole`

An OWNER account can never be assigned as a `CHEF`. The `assignEmployee` service method
checks `user.getRole() != UserRole.EMPLOYEE` and throws `ValidationException` if violated.

---

### Why soft deactivation instead of hard delete?

```
Hard delete: DELETE FROM employees WHERE id = ?
  → The assignment record is gone.
  → If orders reference this employee later, FK integrity may break.
  → No history — you can't tell who worked here before.

Soft deactivation: UPDATE employees SET is_active = false WHERE id = ?
  → Row stays in the DB permanently.
  → All historical references (orders placed by this employee etc.) still work.
  → Audit trail: you can see every person who ever worked at this restaurant.
  → Recoverable: flip is_active back to true to rehire.
```

The `findByRestaurantIdAndIsActiveTrue()` query filters out deactivated employees automatically.

---

### The multi-layer security model

```
Layer 1: JWT Authentication (JwtAuthenticationFilter)
  → Is this a valid token? Is the user known?
  → Fails: 401 Unauthorized

Layer 2: Role check (@PreAuthorize)
  → Is this user's platform role allowed for this endpoint?
  → Fails: 403 Forbidden

Layer 3: Ownership check (service layer)
  → Does this OWNER own THIS restaurant?
  → restaurant.getOwnerId().equals(ownerId)
  → Fails: 403 Forbidden

Layer 4: Cross-restaurant isolation (repository query)
  → Does this employee belong to THIS restaurant?
  → findByIdAndRestaurantId(employeeId, restaurantId)
  → Fails: 404 Not Found (intentionally — not 403)
```

Layer 4 returns 404, not 403, intentionally. "Access denied" tells an attacker "that employee
exists somewhere." "Not found" gives away nothing.

---

### `@Builder.Default` — Why it's needed

```java
// Without @Builder.Default:
Employee e = Employee.builder()
        .userId(someUUID)
        .build();
e.isActive(); // → false (Java default for boolean)

// With @Builder.Default:
@Builder.Default
private boolean isActive = true;

Employee e = Employee.builder()
        .userId(someUUID)
        .build();
e.isActive(); // → true ✅
```

Lombok's `@Builder` does not use Java field initializers — it sets primitives to 0/false by default.
`@Builder.Default` tells Lombok to use the initializer value (`= true`) when the field
is not explicitly set in the builder chain.

---

## Interview Questions & Answers

---

**Q: Why does the Employee entity use UUID fields for `userId` and `restaurantId` instead of `@ManyToOne` relationships?**

A: Two reasons — performance and simplicity.
With `@ManyToOne User user`, Hibernate would JOIN the users table every time an employee is loaded,
even when only `userId` is needed. For a list of 50 employees, that's 50 extra user queries (N+1 problem).
With `UUID userId`, there's one clean query on the employees table.
The Employee module only needs IDs to enforce business rules — it never needs to display user details.
If user details are needed (like employee name), the frontend makes a separate call to the User API.

---

**Q: What is the difference between `UserRole` and `EmployeeRole`?**

A: `UserRole` is a platform-level account type (`SUPER_ADMIN`, `OWNER`, `EMPLOYEE`).
It determines what the user can do on the Plato platform overall.
`EmployeeRole` is a restaurant job title (`MANAGER`, `CHEF`, `WAITER`, `CASHIER`).
It determines what function the person performs in a specific restaurant.
A user must have `UserRole.EMPLOYEE` to be assigned to a restaurant. Once assigned,
they are given an `EmployeeRole` (job title) for that restaurant.

---

**Q: An OWNER can manage employees of their restaurant. What prevents them from managing employees of another restaurant?**

A: Three layers:
1. `@PreAuthorize("hasRole('OWNER')")` — only OWNERs can access these endpoints.
2. Service ownership check: `restaurant.getOwnerId().equals(ownerId)` — the restaurant must be theirs.
3. `findByIdAndRestaurantId(employeeId, restaurantId)` — even if they guess an employeeId,
   the query requires the employee to belong to the restaurantId in the path. If there's a mismatch, it's 404.

---

**Q: Why does `getEmployees()` have a SUPER_ADMIN bypass?**

A: SUPER_ADMIN is a platform administrator who oversees all restaurants. They need visibility
into any restaurant's staff for administrative purposes (auditing, dispute resolution, support).
Without the bypass, the ownership check `restaurant.getOwnerId().equals(callerId)` would always
fail for SUPER_ADMIN (since they don't own any restaurant). The bypass is:
```java
if (!"SUPER_ADMIN".equals(callerRole) && !restaurant.getOwnerId().equals(callerId)) {
    throw new UnauthorizedAccessException("...");
}
```
SUPER_ADMIN skips the ownership check entirely. OWNER must pass it.

---

**Q: Why is `deactivateEmployee` mapped to `DELETE` if it doesn't delete anything from the DB?**

A: HTTP semantics, not implementation semantics. `DELETE /employees/{id}` means
"remove this employee from the active roster" from the client's perspective.
The implementation detail (soft vs hard delete) is hidden behind the API.
Using `DELETE` is semantically correct — the resource is no longer active from the caller's point of view.
`204 No Content` is returned (no body), which is the standard response for DELETE.

---

**Q: Why does `updateEmployeeRole` use `PATCH` instead of `PUT`?**

A: `PUT` semantics = full replacement. The client sends the complete representation of the resource.
`PATCH` semantics = partial update. The client sends only the fields being changed.
`UpdateEmployeeRoleRequest` contains only `role` — a single field.
This is a partial update, so `PATCH` is semantically correct.
`PUT` would imply the client is replacing the entire employee record (all fields), which is not the case.

---

**Q: How does the `UNIQUE(user_id, restaurant_id)` constraint work and why have a service-level check too?**

A: The DB constraint is the hard guard — it prevents duplicate rows at the database level,
regardless of what application code does. If two concurrent requests try to assign the same
user to the same restaurant simultaneously, one will succeed and the other will get a
`DataIntegrityViolationException` from the constraint violation.

The service-level `existsByUserIdAndRestaurantId` check is the soft guard — it gives a clean,
user-friendly `409 Conflict` response instead of a cryptic DB error message.
In practice, the service check catches the common case; the DB constraint catches race conditions.

---

**Q: What is `@Builder.Default` and why is it needed?**

A: Lombok's `@Builder` does not respect Java field initializers — it initializes all fields
to their Java defaults (0, false, null). `@Builder.Default` tells Lombok to use the specified
initializer value when that field is not set in the builder chain.
In `Employee.java`, `@Builder.Default private boolean isActive = true` ensures that
`Employee.builder().build().isActive()` returns `true`.
Without it, every new employee would have `isActive = false`, which is incorrect.

---

**Q: Why return `404 Not Found` when an employee is found but belongs to a different restaurant, instead of `403 Forbidden`?**

A: Information security principle: don't confirm the existence of resources the caller
shouldn't access. If the response is `403 Forbidden`, the caller knows the employeeId
exists somewhere in the system — they can use this to enumerate valid IDs.
Returning `404 Not Found` reveals nothing — the caller cannot distinguish between
"this employee doesn't exist" and "this employee exists but not in your restaurant."
This is called security through obscurity at the API level, and it's an intentional design choice.
