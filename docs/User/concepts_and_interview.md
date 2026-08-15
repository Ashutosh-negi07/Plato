# User Module — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### User.java — The Entity

**What it is**: A JPA entity — a Java class that maps directly to the `users` table in PostgreSQL.
Every field maps to a column. Every instance represents one row.

**Why it exists**: Hibernate needs a class to know the shape of your table. Without this,
Spring Data JPA can't generate SQL or manage objects for you.

**Key annotations explained**:

```java
@Entity
// Tells Hibernate: "This class is a table."
// Without this, Hibernate ignores the class entirely.

@Table(name = "users")
// Tells Hibernate which table to map to.
// Without this, it defaults to the class name ("User") — but our table is "users".

@Getter @Setter
// Lombok: generates getters and setters for all fields.
// Without these, Hibernate can't read or write field values.

@NoArgsConstructor
// Lombok: generates an empty constructor.
// JPA REQUIRES a no-arg constructor to instantiate entities via reflection.
// Without this, you get an error at startup.

@AllArgsConstructor
// Lombok: generates a constructor with all fields.
// Required by @Builder to work correctly.

@Builder
// Lombok: enables the builder pattern.
// User.builder().fullName("Alice").email("a@b.com").build()
// Safer than constructors — you can't accidentally put email where name goes.

@Column(name = "full_name", nullable = false, length = 100)
// Maps field to a specific DB column.
// nullable = false → Hibernate validates before INSERT.
// length = 100 → used in schema generation (irrelevant since Flyway owns the schema).

@Enumerated(EnumType.STRING)
// Stores enum as its string name ("OWNER") not its ordinal (1).
// NEVER use EnumType.ORDINAL — if you add an enum value in the middle, all ordinals shift.

@JdbcTypeCode(SqlTypes.NAMED_ENUM)
// Hibernate 6 fix specifically for PostgreSQL custom enum types.
// Without this: Hibernate sends VARCHAR, PostgreSQL expects the named type → SQLGrammarException.
// This tells the JDBC driver to bind the value as the named enum type.
```

---

### UserRole.java and UserStatus.java — Enums

**What they are**: Type-safe constants. Instead of storing magic strings like "SUPER_ADMIN"
with no validation, you define exactly what's allowed.

**Why enums instead of a String field**:
- `String role = "SUPER_ADMON"` — typo compiles, breaks at runtime
- `UserRole role = UserRole.SUPER_ADMIN` — typo = compile error, caught immediately

---

### V2__create_users.sql — Flyway Migration

**What it is**: The actual SQL that creates the `users` table. Flyway runs this once,
records it in `flyway_schema_history`, and never runs it again.

**Why Flyway and not `ddl-auto: create`**:
- `ddl-auto: create` drops and recreates the table on every restart → you lose all data
- `ddl-auto: update` silently drops columns if you remove a field → data loss in production
- Flyway gives you explicit, version-controlled SQL that runs exactly once

**`ddl-auto: validate`** (what this project uses):
- Hibernate compares entity fields to actual DB columns at startup
- If they don't match → startup fails with a clear error
- Hibernate never modifies the DB — Flyway does that

---

### UserRepository.java — The Repository

**What it is**: A Spring Data JPA repository. Extends `JpaRepository<User, UUID>` which
gives you ~20 CRUD methods for free: `findById`, `findAll`, `save`, `delete`, `count`, etc.

**Why an interface and not a class**:
Spring Data generates the implementation at runtime using proxies. You declare the method
signature — Spring writes the SQL. `findByEmail(String email)` becomes:
```sql
SELECT * FROM users WHERE email = ?
```

**Custom methods**:
```java
Optional<User> findByEmail(String email);
// Spring parses "findBy" + "Email" → WHERE email = ?

boolean existsByEmail(String email);
// Spring parses "existsBy" + "Email" → SELECT COUNT(*) > 0 WHERE email = ?
// More efficient than findByEmail + null check — just a count query

boolean existsByRole(UserRole role);
// Used only by DataInitializer to check if SUPER_ADMIN exists before seeding
```

---

### UserService.java — The Interface

**What it is**: A Java interface declaring what the user service CAN do.
No implementation, no logic — just method signatures.

**Why an interface instead of just the impl class**:
1. **Testability**: In tests, you can mock `UserService` without loading Spring or the DB
2. **Loose coupling**: `UserController` depends on `UserService` (interface), not `UserServiceImpl`
   (concrete class). You can swap implementations without changing the controller.
3. **Spring best practice**: Spring's `@Autowired` / `@RequiredArgsConstructor` inject by type.
   If you have an interface, Spring can inject any implementation of it.

---

### UserServiceImpl.java — The Implementation

**What it is**: The actual business logic. Implements `UserService`.

**Key annotations**:

```java
@Service
// Tells Spring: register this as a Spring bean.
// Allows it to be injected wherever UserService is needed.

@RequiredArgsConstructor
// Lombok: generates a constructor for all final fields.
// Spring sees this constructor and uses it for dependency injection.
// Cleaner than @Autowired on each field.

@Transactional(readOnly = true)
// Applied at CLASS level — all methods are read-only transactions by default.
// readOnly = true: tells the DB driver no writes are coming.
// Allows performance optimisations (no need to track dirty objects, skip flush).

@Transactional  // on individual write methods
// Overrides the class-level readOnly = true.
// Starts a writable transaction for this method.
// If an exception is thrown, the entire transaction is rolled back.
// If no exception, it commits at the end of the method.
```

**Dirty checking explained**:
When a method is `@Transactional`, Hibernate tracks every change to a "managed" entity.
A managed entity is one loaded from the DB within the current transaction.

```java
User user = findById(id);        // now "managed" by Hibernate
user.setFullName("New Name");    // Hibernate notes: fullName changed
// end of @Transactional method  // Hibernate auto-issues UPDATE SQL
```

You don't need to call `save()` again. Hibernate does it automatically at transaction commit.

---

### UserMapper.java — The Mapper

**What it is**: A Spring `@Component` that converts `User` entities to `UserResponse` DTOs.

**Why a separate class**:
- If you put mapping code inside `UserServiceImpl`, and two services need to map Users,
  you duplicate the code. The mapper is one place to change.
- Single Responsibility Principle: the mapper's only job is field conversion.

**Why @Component and not @Service**:
- `@Service` is semantically for business logic
- `@Component` is for generic Spring beans
- A mapper is a utility, not a service

**Why not MapStruct or ModelMapper**:
For learning: explicit mapping lets you see exactly what happens.
For production: MapStruct generates the mapping code at compile time (no reflection = faster).

---

### DataInitializer.java — The Seed Runner

**What it is**: A `CommandLineRunner` that runs once after Spring Boot fully starts.
Seeds the first Super Admin account so the system has an admin on first boot.

**Why `CommandLineRunner`**:
Spring Boot calls `run()` on all `CommandLineRunner` beans after the application context
loads — after Flyway, after all beans are ready. It's the right hook for one-time setup.

**Why not a Flyway migration (like `V2.5__seed_admin.sql`)**:
The admin password comes from environment variables, not hardcoded SQL.
Flyway migrations must be static — they can't read runtime environment variables.
`DataInitializer` can use `@Value("${plato.seed.admin.password}")` to read from env.

**Idempotency**:
```java
if (userRepository.existsByRole(UserRole.SUPER_ADMIN)) {
    return;  // already seeded — do nothing
}
```
Restarting the server never creates a duplicate admin. Safe to restart anytime.

---

### UserController.java — The HTTP Layer

**What it is**: A `@RestController` — handles HTTP requests and returns HTTP responses.
Zero business logic. Just: receive → call service → return.

**Key annotations**:

```java
@RestController
// = @Controller + @ResponseBody
// @Controller: register as a Spring MVC controller (handles HTTP)
// @ResponseBody: auto-serialize the return value to JSON
// Without @ResponseBody, Spring would try to resolve a view (template) instead

@RequestMapping("/api/v1/users")
// All methods in this class are under /api/v1/users
// /api = this is an API (not a page)
// /v1  = version 1 (so you can release /v2 later without breaking clients)
// /users = the resource

@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
// Spring Security expression evaluated BEFORE the method runs.
// If it returns false → AccessDeniedException → GlobalExceptionHandler → 403.
// Must have @EnableMethodSecurity in SecurityConfig for this to work.

@Valid
// Triggers Bean Validation (JSR 380) on the @RequestBody.
// If any @NotBlank, @Email, @Size etc. fail → MethodArgumentNotValidException → 400.

@PathVariable UUID id
// Extracts {id} from the URL and converts String → UUID automatically.
// If the UUID format is invalid → 400 Bad Request.

@PageableDefault(size = 20, sort = "createdAt")
// Tells Spring to use size=20, sort=createdAt as defaults for pagination
// if the client doesn't provide ?page=0&size=...&sort=... query params.
```

---

## Key Concepts Summary

### What is a DTO and why does it exist?
**DTO = Data Transfer Object**. A simple class with only fields — no business logic.
Used to decouple the API shape from the DB shape.

Without DTOs: you return `User` entity → API exposes `passwordHash` → security breach.
With DTOs: you return `UserResponse` → only the fields you explicitly add are visible.

### What is the difference between @Component, @Service, @Repository?
All three are Spring stereotypes — they all register the class as a Spring bean.
The difference is semantic:
- `@Component` — generic bean
- `@Service` — business logic layer
- `@Repository` — data access layer (also enables JPA exception translation)

Spring treats them identically for injection purposes.

### What is `@Transactional` and why does it matter?
A transaction is a unit of work that either completes entirely or not at all.
`@Transactional` wraps the method in a transaction:
- All DB operations inside are part of the same transaction
- If any exception is thrown → all operations roll back
- If no exception → all operations commit at the end

Without `@Transactional` on `createUser()`: if the save() succeeds but something else fails
after it, the user is left in the DB in a broken state.

### What is dirty checking?
When you load an entity inside a `@Transactional` method, Hibernate takes a snapshot of it.
At the end of the transaction, Hibernate compares the current state to the snapshot.
For any changed fields, it automatically issues UPDATE SQL.
You don't need to call `save()` again for updates — just change the field.

### What is soft delete and why not hard delete?
**Hard delete**: `DELETE FROM users WHERE id = ?` — the row is gone permanently.
**Soft delete**: `UPDATE users SET status = 'DELETED' WHERE id = ?` — row stays, flagged as deleted.

Why soft delete:
1. Audit trail — you can see who existed and when they were removed
2. Foreign keys — if other tables reference this user (employees, orders), hard delete
   would either fail (FK violation) or cascade-delete related records unintentionally
3. Recovery — if a user was deleted by mistake, you can restore with a status update

---

## Interview Questions & Answers

---

**Q: What is the difference between `@Controller` and `@RestController`?**

A: `@RestController` is a shortcut for `@Controller + @ResponseBody`.
`@Controller` alone returns view names (for server-side rendering with Thymeleaf etc.).
`@ResponseBody` tells Spring to serialize the return value to JSON and write it directly
to the HTTP response body. Since we're building a REST API (not rendering HTML), we use
`@RestController` on every controller.

---

**Q: Why do you use `@Transactional(readOnly = true)` at class level?**

A: Two reasons:
1. Performance: the DB driver and Hibernate both optimize for read-only transactions.
   Hibernate skips dirty checking (no need to track changes), which saves CPU on large result sets.
2. Intent: it documents that by default, this service only reads data.
   Write methods explicitly override with `@Transactional` to show intent.

---

**Q: You don't call `userRepository.save()` in your `updateUser()` method. How does the update persist?**

A: JPA dirty checking. When `findById()` is called inside a `@Transactional` method,
the returned entity is "managed" by Hibernate — Hibernate tracks its state.
When the transaction commits (at the end of the method), Hibernate compares the current
state of the entity to the original snapshot it took when the entity was loaded.
For any changed fields, it automatically generates and executes an UPDATE SQL statement.
No explicit `save()` is needed.

---

**Q: What is the difference between `findById()` returning `Optional<User>` vs throwing an exception?**

A: `findById()` returns `Optional<User>` — it forces the caller to handle the "not found" case.
We use `.orElseThrow(() -> new ResourceNotFoundException(...))` to convert the empty Optional
into a 404 exception. This is cleaner than returning null, which callers could forget to check,
leading to NullPointerExceptions at unexpected points in the code.

---

**Q: Why is BCrypt used for password hashing? Why not MD5 or SHA-256?**

A: BCrypt is specifically designed for password hashing:
1. **Slow by design**: it has a configurable cost factor. BCrypt takes ~100ms to compute,
   MD5 takes microseconds. For legitimate login this doesn't matter, but for brute-force
   attacks, BCrypt makes trying millions of passwords impractical.
2. **Built-in salt**: BCrypt automatically generates and stores a random salt per password.
   MD5/SHA-256 without salting is vulnerable to rainbow table attacks.
3. **Not reversible**: like all hash functions, BCrypt can't be reversed. You can only verify.

---

**Q: What is the difference between authentication and authorization?**

A: Authentication = "who are you?" — verified by JWT token (the user proves their identity).
Authorization = "what can you do?" — enforced by `@PreAuthorize` and `requireSelfOrAdmin()`.
In this project: the JWT filter (authentication) runs first on every request.
Then `@PreAuthorize` (authorization) checks if the authenticated user has permission.

---

**Q: Why does `UserService` have an interface instead of just `UserServiceImpl`?**

A: Three reasons:
1. **Testability**: you can mock `UserService` in unit tests. Spring can inject a mock
   implementation without starting a real database.
2. **Loose coupling**: `UserController` depends on `UserService` (interface).
   If you change `UserServiceImpl`, the controller doesn't care.
3. **Open/Closed Principle**: you can add a second implementation (e.g. `CachedUserServiceImpl`)
   without changing any callers.

---

**Q: What does `Page<UserResponse>` return and what is in it?**

A: `Page<T>` is Spring Data's pagination wrapper. It contains:
- `content`: the list of items for the current page
- `totalElements`: total number of matching rows in DB
- `totalPages`: total number of pages
- `pageNumber`: current page (0-indexed)
- `pageSize`: items per page
- `first` / `last`: booleans for navigation

The client can use `totalPages` to know how many pages exist and request the next one
with `?page=1&size=20`.

---

**Q: What happens if `@Valid` fails on a request DTO?**

A: Spring throws `MethodArgumentNotValidException` before the controller method even runs.
`GlobalExceptionHandler` catches it and returns:
```json
HTTP 400 Bad Request
{
  "success": false,
  "message": "Validation failed: password size must be between 8 and 2147483647"
}
```

---

**Q: What is `@PreAuthorize` and where does it get the role information from?**

A: `@PreAuthorize` evaluates a Spring Security Expression Language (SpEL) expression
before the method runs. `hasRole('ROLE_SUPER_ADMIN')` checks if the current
`Authentication` object (from `SecurityContextHolder`) has a `GrantedAuthority` with
the value `ROLE_SUPER_ADMIN`. This authority was set when the JWT filter loaded the user
via `UserDetailsServiceImpl` and built the `UsernamePasswordAuthenticationToken`.

---

**Q: Why is soft delete preferred in a production system?**

A: Foreign key integrity, audit trail, and recoverability:
- If `users` is referenced by `employees`, `orders`, `sessions`, a hard DELETE would
  violate FK constraints (or cascade-delete related data unintentionally).
- Soft delete preserves history. You can answer: "When was this user deleted?"
- Soft delete is recoverable. A hard delete is not.
- GDPR note: if a user requests data erasure, you anonymise their PII fields and mark
  status DELETED — keeping the referential structure intact.

---

**Q: What is the builder pattern and why use it over constructors?**

A: The builder pattern allows you to create an object by setting named fields:
```java
User.builder()
    .fullName("Alice")
    .email("alice@example.com")
    .role(UserRole.OWNER)
    .build();
```
vs constructor:
```java
new User(null, "Alice", "alice@example.com", null, "hash", UserRole.OWNER, UserStatus.ACTIVE, null);
```
With a constructor: if you add a field, every call site breaks. And you can't tell which
argument is which without counting. With a builder: named fields, any order, and adding
a field doesn't break existing call sites (unused fields get their default value).

---

**Q: What is `CommandLineRunner` and when do you use it?**

A: `CommandLineRunner` is a Spring Boot interface with one method: `run(String... args)`.
Spring Boot calls `run()` on all `CommandLineRunner` beans after the application context
is fully loaded — after Flyway, after all beans are initialized.
Use it for: seeding initial data, warming up caches, running startup checks.
The `DataInitializer` uses it to seed the Super Admin only if none exists yet.

---

**Q: What is the difference between `@NotNull`, `@NotEmpty`, and `@NotBlank`?**

A:
- `@NotNull`: value must not be null. `""` (empty string) passes.
- `@NotEmpty`: value must not be null or empty. `"  "` (whitespace) passes.
- `@NotBlank`: value must not be null, empty, or whitespace-only. Strictest of the three.

For API fields like `fullName` and `email`, use `@NotBlank` — you don't want
a user named `"   "` (three spaces).

