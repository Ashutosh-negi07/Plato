# User Module — Complete Data Flow

> Every HTTP request through the User module traced step by step.
> Line numbers reference the actual files in the codebase.

---

## Architecture overview

```
HTTP Request
     |
     v
UserController.java        — receives HTTP, calls service, returns HTTP
     |
     v
UserService.java           — interface (the contract)
     |
     v
UserServiceImpl.java       — business logic, calls repo, calls mapper
     |
     v
UserMapper.java            — converts User entity <-> UserResponse DTO
     |
     v
UserRepository.java        — speaks to PostgreSQL
     |
     v
users table (PostgreSQL)
```

The reverse journey:
```
PostgreSQL returns data
     |
     v
UserRepository returns Optional<User> or Page<User>
     |
     v
UserServiceImpl receives the entity
     |
     v
UserMapper converts entity → UserResponse
     |
     v
UserServiceImpl returns UserResponse to controller
     |
     v
UserController wraps it in ApiResponse<UserResponse>
     |
     v
Spring serializes to JSON → HTTP Response sent to client
```

---

## Flow 1 — POST /api/v1/users (Create a User)

### Request example
```
POST /api/v1/users
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "fullName": "Alice Owner",
  "email": "alice@restaurant.com",
  "password": "secret123",
  "phone": "9876543210",
  "role": "OWNER"
}
```

---

### Step-by-step trace

**STEP 1 — Spring Security Filter Chain (before controller)**
```
JwtAuthenticationFilter.java (runs on every request)
  Line 1: reads the Authorization header
  Line 2: extracts the JWT token
  Line 3: calls JwtTokenProvider.validateToken(token)
  Line 4: extracts username (email) from token
  Line 5: loads UserDetails via UserDetailsServiceImpl
  Line 6: creates UsernamePasswordAuthenticationToken
  Line 7: sets it in SecurityContextHolder
→ Authentication is now available to the rest of the request
```

**STEP 2 — @PreAuthorize fires (before method body)**
```
UserController.java
  @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
  Spring reads the authentication from SecurityContextHolder
  Checks: does this user's GrantedAuthority list contain "ROLE_SUPER_ADMIN"?
  YES → proceed to method
  NO  → throw AccessDeniedException → GlobalExceptionHandler → 403 Forbidden
```

**STEP 3 — Controller method begins**
```java
// UserController.java
@PostMapping                                         // maps POST /api/v1/users
@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
public ResponseEntity<ApiResponse<UserResponse>> createUser(
        @Valid @RequestBody CreateUserRequest request) {
//      ^^^^^^
//      @Valid tells Spring to run Bean Validation on CreateUserRequest
//      before the method body executes
//
//      @RequestBody tells Spring to deserialize the JSON body
//      into a CreateUserRequest object
```

**STEP 4 — @Valid runs Bean Validation on CreateUserRequest**
```
CreateUserRequest.java fields:
  @NotBlank  String fullName   → fails if null or ""
  @NotBlank @Email String email → fails if not valid email format
  @NotBlank @Size(min=8) String password → fails if < 8 chars
  @NotNull UserRole role       → fails if not provided

IF validation fails:
  Spring throws MethodArgumentNotValidException
  GlobalExceptionHandler catches it → 400 Bad Request
  Body: { success: false, message: "Validation failed: fullName must not be blank" }

IF validation passes:
  CreateUserRequest object is populated:
    request.getFullName()  → "Alice Owner"
    request.getEmail()     → "alice@restaurant.com"
    request.getPassword()  → "secret123"   ← plaintext at this point
    request.getPhone()     → "9876543210"
    request.getRole()      → UserRole.OWNER
```

**STEP 5 — Controller calls service**
```java
// UserController.java
UserResponse created = userService.createUser(request);
//                     ^^^^^^^^^^^
//                     This calls the interface method.
//                     Spring injects UserServiceImpl at runtime.
```

**STEP 6 — UserServiceImpl.createUser() begins**
```java
// UserServiceImpl.java
@Override
@Transactional    // opens a DB transaction here
public UserResponse createUser(CreateUserRequest request) {
```

**STEP 7 — Email conflict check**
```java
// UserServiceImpl.java
if (userRepository.existsByEmail(request.getEmail())) {
//  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//  Sends SQL: SELECT COUNT(*) > 0 FROM users WHERE email = 'alice@restaurant.com'
//
    throw new ConflictException("A user with email '...' already exists");
//  GlobalExceptionHandler catches this → 409 Conflict
}
```

**STEP 8 — Password hashing**
```java
// UserServiceImpl.java
User user = User.builder()
        .fullName(request.getFullName())       // "Alice Owner"
        .email(request.getEmail())             // "alice@restaurant.com"
        .passwordHash(passwordEncoder.encode(request.getPassword()))
//                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                   BCryptPasswordEncoder.encode("secret123")
//                   returns "$2a$10$randomSalt...hashedValue"
//                   The plaintext "secret123" is NEVER stored anywhere
        .phone(request.getPhone())
        .role(request.getRole())               // UserRole.OWNER
        .status(UserStatus.ACTIVE)             // always ACTIVE on creation
        .build();
//  At this point: user is a new Java object, NOT yet in the DB
//  user.getId() is null — Hibernate hasn't generated it yet
```

**STEP 9 — Persist to database**
```java
// UserServiceImpl.java
User saved = userRepository.save(user);
//           ^^^^^^^^^^^^^^^^^^^^^^^
//           JpaRepository.save() sees id == null → INSERT
//
//           Hibernate sends SQL:
//           INSERT INTO users (id, full_name, email, phone,
//                             password_hash, role, status, created_at, updated_at)
//           VALUES (gen_random_uuid(), 'Alice Owner', 'alice@restaurant.com',
//                  '9876543210', '$2a$10$...', 'OWNER', 'ACTIVE', now(), now())
//
//           After save: saved.getId() = UUID that PostgreSQL generated
```

**STEP 10 — Map entity to DTO**
```java
// UserServiceImpl.java
return userMapper.toResponse(saved);
//     ^^^^^^^^^^
//     Calls UserMapper.toResponse(saved)
```

**STEP 11 — UserMapper.toResponse() executes**
```java
// UserMapper.java
public UserResponse toResponse(User user) {
    return UserResponse.builder()
            .id(user.getId())              // UUID from DB
            .fullName(user.getFullName())  // "Alice Owner"
            .email(user.getEmail())        // "alice@restaurant.com"
            .phone(user.getPhone())        // "9876543210"
            .role(user.getRole())          // UserRole.OWNER
            .status(user.getStatus())      // UserStatus.ACTIVE
            .lastLogin(user.getLastLogin()) // null — first time
            .createdAt(user.getCreatedAt()) // now()
            .updatedAt(user.getUpdatedAt()) // now()
            .build();
//  NOTE: passwordHash is NOT copied — this is the security boundary
//  UserResponse has no passwordHash field
}
```

**STEP 12 — Back in Controller**
```java
// UserController.java
UserResponse created = userService.createUser(request);
//  created is now a UserResponse — no entity, no passwordHash

return ResponseEntity.status(HttpStatus.CREATED)
//                   ^^^^^^^^^^^^^^^^^^^^^^^^^^
//                   Sets HTTP status 201 Created

        .body(ApiResponse.ok("User created successfully", created));
//            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//            Wraps UserResponse in:
//            {
//              "success": true,
//              "message": "User created successfully",
//              "data": {
//                "id": "550e8400-e29b...",
//                "fullName": "Alice Owner",
//                "email": "alice@restaurant.com",
//                "phone": "9876543210",
//                "role": "OWNER",
//                "status": "ACTIVE",
//                "lastLogin": null,
//                "createdAt": "2026-08-15T11:42:00",
//                "updatedAt": "2026-08-15T11:42:00"
//              }
//            }
```

**STEP 13 — @Transactional commits**
```
The @Transactional on createUser() commits the DB transaction here.
The INSERT is now permanent in the users table.
```

**STEP 14 — Spring serializes to JSON and sends HTTP response**
```
HTTP/1.1 201 Created
Content-Type: application/json

{
  "success": true,
  "message": "User created successfully",
  "data": { ... }
}
```

---

## Flow 2 — GET /api/v1/users/{id} (Get one user — self or admin)

### Request example
```
GET /api/v1/users/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer eyJhbGci...
```

---

### Step-by-step trace

**STEP 1 — JwtAuthenticationFilter** *(same as Flow 1 STEP 1)*

**STEP 2 — Controller method begins (no @PreAuthorize — handled manually)**
```java
// UserController.java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUserById(
        @PathVariable UUID id,
//      ^^^^^^^^^^^^ Spring extracts "550e8400..." from the URL and converts to UUID
        Authentication authentication) {
//      ^^^^^^^^^^^^^ Spring injects the current caller's auth from SecurityContextHolder
```

**STEP 3 — requireSelfOrAdmin() check**
```java
// UserController.java — private helper
private void requireSelfOrAdmin(Authentication authentication, UUID targetId) {

    boolean isSuperAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
//          Checks if the JWT contained ROLE_SUPER_ADMIN in its claims

    if (!isSuperAdmin) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
//      Gets the UserDetails loaded during JWT validation
//      principal.getUsername() returns the email from the JWT subject

        User currentUser = userService.findByEmail(principal.getUsername());
//      Loads the caller's User entity from DB to get their UUID

        if (!currentUser.getId().equals(targetId)) {
//          Compares caller's UUID to the {id} in the URL
            throw new UnauthorizedAccessException("...");
//          GlobalExceptionHandler → 403 Forbidden
        }
    }
}
// If passed: caller is either SUPER_ADMIN or is requesting their own data
```

**STEP 4 — Service call**
```java
// UserController.java
UserResponse user = userService.getUserById(id);
```

**STEP 5 — UserServiceImpl.getUserById()**
```java
// UserServiceImpl.java
public UserResponse getUserById(UUID id) {
    User user = findById(id);
//              ^^^^^^^^^^^
//              userRepository.findById(id)
//              SQL: SELECT * FROM users WHERE id = '550e8400...'
//              Returns Optional<User>
//              .orElseThrow() → ResourceNotFoundException → 404 if not found

    return userMapper.toResponse(user);
//  Maps entity to DTO (same as Flow 1 STEP 11)
}
```

**STEP 6 — Response**
```java
return ResponseEntity.ok(ApiResponse.ok("User fetched successfully", user));
// HTTP 200 OK
```

---

## Flow 3 — PATCH /api/v1/users/{id} (Partial update)

### Request example
```
PATCH /api/v1/users/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "fullName": "Alice Updated"
}
```

*(email and phone are NOT sent — only fullName changes)*

---

### Step-by-step trace

**STEP 1–2** — JWT filter + self/admin check (same pattern)

**STEP 3 — Controller**
```java
// UserController.java
UserResponse updated = userService.updateUser(id, request);
// request.getFullName() = "Alice Updated"
// request.getEmail()    = null (not sent)
// request.getPhone()    = null (not sent)
```

**STEP 4 — UserServiceImpl.updateUser()**
```java
@Transactional    // opens transaction — enables dirty checking
public UserResponse updateUser(UUID id, UpdateUserRequest request) {
    User user = findById(id);
//  Loads the managed entity from DB
//  "Managed" = Hibernate is tracking every field of this object

    if (request.getFullName() != null) {
        user.setFullName(request.getFullName());
//      Changes "Alice Owner" to "Alice Updated" on the managed object
//      Hibernate marks this field as dirty
    }

    if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
//  null → skip (email not sent in request)
    }

    if (request.getPhone() != null) {
//  null → skip
    }

    // NO explicit userRepository.save() call here
    // Hibernate dirty checking: at end of @Transactional method,
    // Hibernate sees fullName changed → auto issues:
    // UPDATE users SET full_name = 'Alice Updated', updated_at = now()
    // WHERE id = '550e8400...'

    return userMapper.toResponse(user);
//  Maps the UPDATED entity to UserResponse
}
```

**STEP 5 — Response**
```
HTTP 200 OK
{
  "success": true,
  "message": "User updated successfully",
  "data": {
    "fullName": "Alice Updated",
    ...
  }
}
```

---

## Flow 4 — DELETE /api/v1/users/{id} (Soft delete)

### Request example
```
DELETE /api/v1/users/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer eyJhbGci...
```

---

### Step-by-step trace

**STEP 1 — @PreAuthorize**
```java
@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
// Only SUPER_ADMIN can delete
```

**STEP 2 — UserServiceImpl.deleteUser()**
```java
@Transactional
public void deleteUser(UUID id) {
    User user = findById(id);
//  Loads entity — throws 404 if not found

    user.setStatus(UserStatus.DELETED);
//  Sets status field on managed entity
//  Hibernate dirty check at end of transaction:
//  UPDATE users SET status = 'DELETED', updated_at = now() WHERE id = '...'
//
//  The row is NEVER physically deleted from the database.
//  The data stays. The audit trail stays. FK references stay intact.
}
```

**STEP 3 — Response**
```java
return ResponseEntity.noContent().build();
// HTTP 204 No Content — no body, no ApiResponse wrapper
// The client knows it worked because of the 204 status
```

---

## Flow 5 — GET /api/v1/users (Paginated list)

### Request example
```
GET /api/v1/users?page=0&size=10&sort=createdAt,desc
Authorization: Bearer eyJhbGci...
```

---

### Step-by-step trace

**STEP 1 — @PageableDefault**
```java
@GetMapping
@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//      Spring reads ?page=0&size=10&sort=createdAt,desc from URL
//      and builds a Pageable object with those values.
//      If not provided, defaults: size=20, sort=createdAt ASC
```

**STEP 2 — UserServiceImpl.getAllUsers()**
```java
public Page<UserResponse> getAllUsers(Pageable pageable) {
    return userRepository.findAll(pageable)
//         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//         SQL: SELECT * FROM users ORDER BY created_at DESC LIMIT 10 OFFSET 0
//         Returns Page<User> — contains the 10 user entities + pagination metadata
//         (totalElements, totalPages, currentPage, etc.)

            .map(userMapper::toResponse);
//           ^^^^^^^^^^^^^^^^^^^^^^^^^^^
//           Page.map() applies userMapper.toResponse() to EACH User in the page
//           Returns Page<UserResponse> — same metadata, but entities replaced by DTOs
}
```

**STEP 3 — Response**
```json
{
  "success": true,
  "message": "Users fetched successfully",
  "data": {
    "content": [ { "id": "...", "fullName": "..." }, ... ],
    "pageable": { "pageNumber": 0, "pageSize": 10 },
    "totalElements": 42,
    "totalPages": 5,
    "last": false,
    "first": true
  }
}
```

---

## Data objects summary

| Object | Type | Direction | Contains passwordHash? |
|--------|------|-----------|----------------------|
| `CreateUserRequest` | DTO | IN (client → service) | Yes (plaintext password field) |
| `UpdateUserRequest` | DTO | IN (client → service) | No |
| `User` | Entity | Internal only | Yes (BCrypt hash) |
| `UserResponse` | DTO | OUT (service → client) | **Never** |

---

## Security boundary — where passwordHash stops

```
Client sends password (plaintext) in CreateUserRequest
       |
       v
UserServiceImpl.createUser()
  passwordEncoder.encode(password)  ← converts to BCrypt hash
  User.builder().passwordHash(hash) ← stored in entity/DB
       |
       v
userMapper.toResponse(user)         ← UserResponse has NO passwordHash field
       |
       v
Client receives UserResponse        ← password never visible again
```

