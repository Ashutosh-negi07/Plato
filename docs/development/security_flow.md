# Plato — Security & JWT Data Flow

> **Scope**: This document traces exactly how data moves through the five security-related files on every request — what goes in, what comes out, and what each class is responsible for.
>
> Files covered:
> - `security/SecurityProperties.java`
> - `security/JwtTokenProvider.java`
> - `security/UserDetailsServiceImpl.java`
> - `security/JwtAuthenticationFilter.java`
> - `config/SecurityConfig.java`

---

## The Two Distinct Security Flows

The security system handles **two completely different situations**. Understanding this distinction is fundamental.

| Flow | Triggered By | What happens |
|------|-------------|-------------|
| **Login Flow** | `POST /api/v1/auth/login` with email + password | Verify password → generate JWT → return token to client |
| **Request Flow** | Any subsequent request with `Authorization: Bearer <token>` | Validate JWT → extract identity → set SecurityContext → allow/deny |

These two flows share the same infrastructure but use it differently. The document covers both.

---

## 0. Application Startup — Before Any Request Arrives

Before the first HTTP request, Spring Boot wires everything together.

```
application.yml
    plato:
      jwt:
        secret: ${JWT_SECRET}      ← environment variable
        expiration: 86400000
            │
            │  @ConfigurationProperties(prefix = "plato.jwt")
            ▼
    SecurityProperties (Bean)
        .secret   = "<value from JWT_SECRET env var>"
        .expiration = 86400000L  (24 hours in ms)
```

**What happens if `JWT_SECRET` is not set?**
`@Validated` on `SecurityProperties` triggers Bean Validation on startup. If `secret` is blank, Spring throws a `BindValidationException` and the application **refuses to start entirely**. No silent failure.

**What SecurityConfig registers at startup:**

```
SecurityConfig builds:

  DaoAuthenticationProvider
      │ uses → UserDetailsServiceImpl  (how to load a user from DB)
      │ uses → BCryptPasswordEncoder   (how to compare passwords)
      └─ registered into Spring's auth system

  SecurityFilterChain  (ordered list of rules for every request)
      │
      ├─ CSRF disabled
      ├─ Sessions: STATELESS (no HttpSession ever created)
      ├─ Path rules:
      │     /api/v1/auth/**      → permitAll
      │     /api/v1/qr/**        → permitAll
      │     /api/v1/customer/**  → permitAll
      │     /swagger-ui/**       → permitAll
      │     /actuator/health     → permitAll
      │     everything else      → authenticated()
      │
      └─ JwtAuthenticationFilter added BEFORE UsernamePasswordAuthenticationFilter
```

This filter chain runs on **every single HTTP request** before it reaches a controller.

---

## 1. Login Flow — Generating a JWT

> This flow runs when `AuthService` (Day 3 Task 3) handles `POST /api/v1/auth/login`.

```
Client
  │
  │  POST /api/v1/auth/login
  │  Body: { "email": "owner@pizza.com", "password": "secret123" }
  │
  ▼
SecurityConfig path rules
  → /api/v1/auth/** is permitAll → request passes through without JWT check
  │
  ▼
AuthController  →  AuthService.login(email, password)
                        │
                        │  authenticationManager.authenticate(
                        │      new UsernamePasswordAuthenticationToken(email, password)
                        │  )
                        │
                        ▼
              DaoAuthenticationProvider  (wired in SecurityConfig)
                        │
                        ├─ calls UserDetailsServiceImpl.loadUserByUsername(email)
                        │         │
                        │         │  userRepository.findByEmail(email)
                        │         │         │
                        │         │         ▼
                        │         │    SELECT * FROM users WHERE email = ?
                        │         │         │
                        │         │         ▼  Returns our User entity
                        │         │
                        │         │  Wraps entity into Spring Security User:
                        │         │    .username(user.getEmail())
                        │         │    .password(user.getPasswordHash())   ← BCrypt hash
                        │         │    .authorities(["ROLE_OWNER"])
                        │         │    .disabled(status == SUSPENDED)
                        │         │    .accountLocked(status == DELETED)
                        │         │
                        │         └─ returns UserDetails object
                        │
                        └─ BCryptPasswordEncoder.matches(rawPassword, hash)
                                  │
                                  ├─ MATCH  → authentication succeeds
                                  └─ NO MATCH → throws BadCredentialsException → 401
                        │
                        ▼  (on success)
              AuthService receives authenticated principal
                        │
                        │  loads the User entity to get userId + role
                        │
                        ▼
              JwtTokenProvider.generateToken(userId, role)
```

### Inside `JwtTokenProvider.generateToken(userId, role)`

```
Input:
    userId = UUID  ("550e8400-e29b-41d4-a716-446655440000")
    role   = String ("OWNER")

Step 1: Build the signing key
    secret.getBytes()  →  byte[]
    Keys.hmacShaKeyFor(bytes)  →  SecretKey (HMAC-SHA256)

Step 2: Assemble the JWT
    Jwts.builder()
        .subject("550e8400-e29b-41d4-a716-446655440000")   ← "sub" claim
        .claim("role", "OWNER")                             ← custom claim
        .issuedAt(new Date())                               ← "iat" claim
        .expiration(new Date(now + 86400000))               ← "exp" claim (now + 24h)
        .signWith(signingKey)                               ← HMAC-SHA256 signature
        .compact()                                          ← serialise to String

Output (3 base64 parts joined by dots):
    eyJhbGciOiJIUzI1NiJ9
    .eyJzdWIiOiI1NTBlODQwMC4uLiIsInJvbGUiOiJPV05FUiIsImlhdCI6Li4uLCJleHAiOi4uLn0
    .SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
         ▲                ▲                           ▲
       Header           Payload                   Signature
    (algorithm)    (claims, readable)        (HMAC of header+payload)
```

The token is returned to the client. The client stores it (localStorage / memory) and sends it on every future request in the `Authorization` header.

---

## 2. Request Flow — Validating a JWT

> This flow runs on **every** request that arrives after login.

### 2.1 — The Full Path of a Request

```
Client
  │
  │  GET /api/v1/restaurants
  │  Headers:
  │    Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1N...SflKx
  │
  ▼
Spring's Filter Chain (registered in SecurityConfig)
  │
  ├─ [ other Spring filters... ]
  │
  ├─ JwtAuthenticationFilter  ← OUR FILTER (runs before UsernamePasswordAuthenticationFilter)
  │         │
  │         │  doFilterInternal(request, response, filterChain)
  │         │
  │         ▼  extractTokenFromRequest(request)
  │
  │    request.getHeader("Authorization")
  │         → "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIi..."
  │
  │    Does it start with "Bearer "?  YES
  │         → substring(7)
  │         → "eyJhbGciOiJIUzI1NiJ9.eyJzdWIi..."   (raw JWT string)
  │
  ▼
  JwtTokenProvider.validateToken(token)
  │
  │    parseClaims(token):
  │        Jwts.parser()
  │            .verifyWith(getSigningKey())     ← recomputes HMAC-SHA256 from secret
  │            .build()
  │            .parseSignedClaims(token)        ← parses + verifies signature in one step
  │            .getPayload()
  │
  │    Possible outcomes:
  │        ✅ token valid, not expired          → returns Claims object
  │        ❌ ExpiredJwtException               → log.warn + return false
  │        ❌ MalformedJwtException             → log.warn + return false
  │        ❌ SecurityException (bad sig)       → log.warn + return false
  │        ❌ UnsupportedJwtException           → log.warn + return false
  │        ❌ IllegalArgumentException          → log.warn + return false
  │
  ▼  (valid = true)
  JwtTokenProvider.getUserIdFromToken(token)
  │
  │    parseClaims(token).getSubject()
  │        → "550e8400-e29b-41d4-a716-446655440000"
  │
  │    UUID.fromString(...)
  │        → UUID object
  │
  ▼
  JwtTokenProvider.getRoleFromToken(token)
  │
  │    parseClaims(token).get("role", String.class)
  │        → "OWNER"
  │
  ▼
  Build authority from role string (no DB hit)
  │
  │    new SimpleGrantedAuthority("ROLE_" + "OWNER")
  │        → SimpleGrantedAuthority("ROLE_OWNER")
  │
  ▼
  Build Authentication object
  │
  │    new UsernamePasswordAuthenticationToken(
  │        "550e8400-e29b-41d4-a716-446655440000",   // principal (userId string)
  │        null,                                      // credentials (not needed)
  │        List.of(authority)                         // ["ROLE_OWNER"]
  │    )
  │
  │    authentication.setDetails(
  │        new WebAuthenticationDetailsSource().buildDetails(request)
  │        // attaches IP address, session ID etc. for auditing
  │    )
  │
  ▼
  SecurityContextHolder.getContext().setAuthentication(authentication)
  │
  │    Spring Security now "knows" who is making this request.
  │    Any code in this thread can call:
  │        SecurityContextHolder.getContext().getAuthentication().getPrincipal()
  │        → "550e8400-..."  (the userId)
  │
  ▼
  filterChain.doFilter(request, response)  ← ALWAYS called; passes to next filter
  │
  ▼
  UsernamePasswordAuthenticationFilter  ← sees SecurityContext is already set, skips
  │
  ▼
  SecurityConfig path rules evaluated
  │
  │    Is /api/v1/restaurants in the permitAll list?  NO
  │    Is there a valid Authentication in SecurityContext?  YES
  │        → request allowed through
  │
  ▼
  @PreAuthorize check (if present on controller method)
  │
  │    e.g. @PreAuthorize("hasRole('OWNER')")
  │         SecurityContext has ["ROLE_OWNER"]
  │         "ROLE_OWNER".contains("OWNER")  → true
  │         → method executes
  │
  ▼
  Controller method runs
```

---

## 3. What Happens When There Is No Token

```
Client
  │
  │  GET /api/v1/restaurants
  │  (no Authorization header)
  │
  ▼
JwtAuthenticationFilter
  │
  │    extractTokenFromRequest(request)
  │        → header is null
  │        → returns null
  │
  │    StringUtils.hasText(null)  →  false
  │    → the if-block is SKIPPED entirely
  │    → SecurityContext stays EMPTY (no authentication set)
  │
  │    filterChain.doFilter(...)  ← still called; request continues
  │
  ▼
SecurityConfig path rules
  │
  │    Is /api/v1/restaurants in permitAll?  NO
  │    Is there an Authentication in SecurityContext?  NO
  │        → 401 Unauthorized returned
  │        → request does NOT reach the controller
```

---

## 4. What Happens When the Token Is Expired or Tampered

```
Client
  │
  │  GET /api/v1/restaurants
  │  Authorization: Bearer <expired or tampered token>
  │
  ▼
JwtAuthenticationFilter
  │
  │    extractTokenFromRequest(request)  →  raw token string
  │
  │    JwtTokenProvider.validateToken(token)
  │        parseClaims(token)
  │            Jwts.parser().verifyWith(key).parseSignedClaims(token)
  │                │
  │                ├─ expired  → throws ExpiredJwtException
  │                │              caught → log.warn("JWT token is expired")
  │                │              returns false
  │                │
  │                └─ tampered → recomputed signature does NOT match stored signature
  │                              throws SecurityException
  │                              caught → log.warn("JWT signature validation failed")
  │                              returns false
  │
  │    validateToken returns false
  │    → if-block is skipped
  │    → SecurityContext stays EMPTY
  │
  │    filterChain.doFilter(...)  ← still called
  │
  ▼
SecurityConfig path rules  →  401 Unauthorized
```

---

## 5. The Login Path — Why It Bypasses the Filter

A common question: *if `JwtAuthenticationFilter` runs on every request, how does `/api/v1/auth/login` work without a token?*

```
POST /api/v1/auth/login (no token)
  │
  ▼
JwtAuthenticationFilter
  │
  │    extractTokenFromRequest → null
  │    StringUtils.hasText(null) → false
  │    → if-block skipped, SecurityContext empty
  │    → filterChain.doFilter(...) called
  │
  ▼
SecurityConfig path rules
  │
  │    /api/v1/auth/** → permitAll
  │    → request is ALLOWED even with empty SecurityContext
  │
  ▼
AuthController.login() executes
```

`permitAll()` means "do not require an Authentication object in the SecurityContext." That is why the login endpoint works without a token — the path rule explicitly bypasses the authentication requirement.

---

## 6. `UserDetailsServiceImpl` — When Is It Actually Called?

`UserDetailsServiceImpl.loadUserByUsername` is called in two situations:

### During login (via `AuthenticationManager`)
```
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken("owner@pizza.com", "secret123")
)
    │
    ▼
DaoAuthenticationProvider
    ├─ calls loadUserByUsername("owner@pizza.com")
    │       → SELECT * FROM users WHERE email = 'owner@pizza.com'
    │       → wraps result into UserDetails
    │
    └─ BCryptPasswordEncoder.matches("secret123", storedHash)
          ├─ true  → auth succeeds
          └─ false → BadCredentialsException → 401
```

### NOT during normal JWT request processing

Notice that `JwtAuthenticationFilter` does **not** call `UserDetailsServiceImpl`. It reads the role directly from the JWT claims and builds the `Authentication` object itself — no database hit. This is intentional:

```
Normal JWT request:
    token → parseClaims → userId + role → Authentication
    (0 DB queries for auth)

Login:
    email + password → loadUserByUsername → DB query → BCrypt check
    (1 DB query for auth)
```

This is one of the core performance benefits of JWT: authenticated requests are stateless and require no DB lookup in the auth layer.

---

## 7. Data Transformations Summary

| Stage | Input | Output | Class responsible |
|-------|-------|--------|------------------|
| App startup | `application.yml` / env var | `SecurityProperties` bean with `.secret` and `.expiration` | `SecurityProperties` + Spring |
| Login: load user | email `String` | `UserDetails` (Spring Security object) | `UserDetailsServiceImpl` |
| Login: verify password | raw password + BCrypt hash | `Authentication` or exception | `DaoAuthenticationProvider` |
| Login: generate token | `UUID userId` + `String role` | JWT `String` (`"eyJ..."`) | `JwtTokenProvider.generateToken` |
| Request: extract token | `HttpServletRequest` | raw JWT `String` or `null` | `JwtAuthenticationFilter.extractTokenFromRequest` |
| Request: validate token | JWT `String` | `boolean` (true/false) + log entry if invalid | `JwtTokenProvider.validateToken` |
| Request: parse userId | JWT `String` | `UUID` | `JwtTokenProvider.getUserIdFromToken` |
| Request: parse role | JWT `String` | `String` (e.g. `"OWNER"`) | `JwtTokenProvider.getRoleFromToken` |
| Request: build authority | `String` role | `SimpleGrantedAuthority("ROLE_OWNER")` | `JwtAuthenticationFilter` |
| Request: build auth token | `userId` + authority | `UsernamePasswordAuthenticationToken` | `JwtAuthenticationFilter` |
| Request: store in context | `UsernamePasswordAuthenticationToken` | Security context populated for this thread | `SecurityContextHolder` |
| Request: path check | `SecurityContext` + path | allow or 401 | `SecurityConfig` filter chain |
| Request: role check | `SecurityContext` authorities | allow or 403 | `@PreAuthorize` + `@EnableMethodSecurity` |

---

## 8. What Each File Owns — Responsibility Boundaries

```
SecurityProperties.java
    OWNS:  reading and holding JWT config (secret, expiration)
    USED BY: JwtTokenProvider (injected)
    KNOWS NOTHING ABOUT: requests, users, Spring Security internals

JwtTokenProvider.java
    OWNS:  all jjwt library calls — generate, validate, parse
    USED BY: JwtAuthenticationFilter (validate + parse), AuthService (generate)
    KNOWS NOTHING ABOUT: HTTP, users, Spring Security filters

UserDetailsServiceImpl.java
    OWNS:  the DB lookup that translates an email into a Spring Security UserDetails
    USED BY: DaoAuthenticationProvider (during login only)
    KNOWS NOTHING ABOUT: JWT, tokens, HTTP requests

JwtAuthenticationFilter.java
    OWNS:  reading the Authorization header; orchestrating validation + context population
    USED BY: SecurityConfig (registered in filter chain)
    DELEGATES TO: JwtTokenProvider (token work), SecurityContextHolder (context storage)
    KNOWS NOTHING ABOUT: DB, passwords, login logic

SecurityConfig.java
    OWNS:  the master rule set — which paths need auth, session policy, filter ordering
    REGISTERS: JwtAuthenticationFilter into the chain, DaoAuthenticationProvider
    EXPOSES: AuthenticationManager as a bean (so AuthService can use it)
    KNOWS NOTHING ABOUT: JWT format, token parsing, DB queries
```

---

## 9. Class Interaction Map

```
                    application.yml
                         │
                         │ @ConfigurationProperties
                         ▼
                 SecurityProperties
                 (.secret, .expiration)
                         │
                         │ injected into
                         ▼
                 JwtTokenProvider
                 (generate / validate / parse)
                    │           │
          used by   │           │  used by (generate only)
                    │           │
                    ▼           ▼
       JwtAuthenticationFilter  AuthService  (Day 3 Task 3)
                    │
                    │ builds Authentication and stores in
                    ▼
            SecurityContextHolder
                    │
                    │ read by
                    ▼
           Controller / Service
           (getPrincipal() → userId)


        UserRepository
              │
              │ called by
              ▼
       UserDetailsServiceImpl
              │
              │ used by
              ▼
       DaoAuthenticationProvider
              │
              │ wired into
              ▼
       AuthenticationManager  (exposed as Bean by SecurityConfig)
              │
              │ injected into
              ▼
           AuthService (login)
```

---

## 10. The `ROLE_` Prefix — Full Trace

This trips up many developers. Here is the complete journey of a role value:

```
Database:
    users.role = 'OWNER'   (user_role enum)

User entity (Java):
    UserRole.OWNER   (enum value)

UserDetailsServiceImpl:
    "ROLE_" + user.getRole().name()
     = "ROLE_" + "OWNER"
     = "ROLE_OWNER"
    → new SimpleGrantedAuthority("ROLE_OWNER")

JWT token (custom claim):
    { "role": "OWNER" }   ← stored WITHOUT the prefix

JwtAuthenticationFilter (reading from token):
    getRoleFromToken(token)  →  "OWNER"
    "ROLE_" + "OWNER"  →  "ROLE_OWNER"
    → new SimpleGrantedAuthority("ROLE_OWNER")

@PreAuthorize on controller:
    @PreAuthorize("hasRole('OWNER')")
    Spring internally prepends "ROLE_": checks for "ROLE_OWNER"
    SecurityContext has "ROLE_OWNER"  →  MATCH  →  allowed
```

The `ROLE_` prefix is **added in two places**: `UserDetailsServiceImpl` (for login-time auth) and `JwtAuthenticationFilter` (for token-based auth). The JWT itself stores just `"OWNER"` — the prefix is always added at the Java layer.

---

## 11. Detailed Token Generation Flow (Line-by-Line Code Reference)

This section traces exactly how a JWT token is generated after a successful login verification, referencing exact files, methods, and line numbers in the codebase.

### 11.1 Classes Involved
- `SecurityProperties.java`: `backend/src/main/java/com/miniproject/plato/security/SecurityProperties.java`
- `JwtTokenProvider.java`: `backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java`
- `UserDetailsServiceImpl.java`: `backend/src/main/java/com/miniproject/plato/security/UserDetailsServiceImpl.java`
- `SecurityConfig.java`: `backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java`

### 11.2 Step 1: Authentication Verification
1. `AuthenticationManager` is exposed as a Spring Bean in `SecurityConfig.java` (Lines 125–129):
   ```java
   @Bean
   public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
       return config.getAuthenticationManager();
   }
   ```
2. During login, `AuthenticationManager` calls `loadUserByUsername` in `UserDetailsServiceImpl.java` (Lines 42–64):
   - **Line 43**: `userRepository.findByEmail(email)` queries PostgreSQL.
   - **Line 52–53**: `new SimpleGrantedAuthority("ROLE_" + user.getRole().name())` maps the role.
   - **Lines 58–63**: Returns Spring Security's `UserDetails` object containing the stored BCrypt hash.
   - Spring Security's `BCryptPasswordEncoder` verifies the raw password against the hash.

### 11.3 Step 2: Generating the Cryptographic Key
Inside `JwtTokenProvider.java` (Lines 18–24):
```java
private SecretKey getSigningKey() {
    byte[] keyBytes = securityProperties.getSecret().getBytes();
    return Keys.hmacShaKeyFor(keyBytes);
}
```
- **Line 22**: `securityProperties.getSecret()` reads the `secret` field from `SecurityProperties.java` (Line 18), populated from `application.yml` (`JWT_SECRET`).
- **Line 22**: `.getBytes()` converts the UTF-8 string to a raw byte array.
- **Line 23**: `Keys.hmacShaKeyFor(keyBytes)` returns a `javax.crypto.SecretKey` configured for HMAC-SHA256.

### 11.4 Step 3: Assembling and Signing the JWT
Inside `JwtTokenProvider.java` (Lines 25–30):
```java
public String generateToken(UUID userId, String role){
    Date now    = new Date();
    Date expiry = new Date(now.getTime() + securityProperties.getExpiration());

    return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact();
}
```
- **Line 26**: `Date now = new Date()` captures creation timestamp (`iat`).
- **Line 27**: `Date expiry = new Date(now.getTime() + securityProperties.getExpiration())` adds `86400000L` (24h from `SecurityProperties.java:19`).
- **Line 29 (`.subject(...)`)**: Sets standard JWT claim `sub` to the user's UUID string.
- **Line 29 (`.claim("role", role)`)**: Embeds custom claim `"role"` (e.g. `"OWNER"`).
- **Line 29 (`.issuedAt(now)`)**: Sets `iat` claim.
- **Line 29 (`.expiration(expiry)`)**: Sets `exp` claim.
- **Line 29 (`.signWith(getSigningKey())`)**: Signs the header + payload using HMAC-SHA256.
- **Line 29 (`.compact()`)**: Serializes into Base64-URL string format: `header.payload.signature`.

---

## 12. Detailed Client Request JWT Authentication Flow (Line-by-Line Code Reference)

This section details how every subsequent HTTP request from a client is intercepted, verified, and authenticated without touching the database.

### 12.1 Classes Involved
- `JwtAuthenticationFilter.java`: `backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java`
- `JwtTokenProvider.java`: `backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java`
- `SecurityConfig.java`: `backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java`

### 12.2 Step 1: Interception & Header Extraction
1. Intercepted by `JwtAuthenticationFilter.java` (Lines 37–44):
   ```java
   @Override
   protected void doFilterInternal(...) {
       String token = extractTokenFromRequest(request);
   ```
2. Token extraction method in `JwtAuthenticationFilter.java` (Lines 120–129):
   ```java
   private String extractTokenFromRequest(HttpServletRequest request) {
       String bearerToken = request.getHeader("Authorization");
       if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
           return bearerToken.substring(7);
       }
       return null;
   }
   ```
   - **Line 121**: Retrieves `"Authorization"` header value (e.g. `"Bearer eyJhbGci..."`).
   - **Line 126**: `bearerToken.substring(7)` strips off `"Bearer "` (7 characters) to return only the raw JWT string.

### 12.3 Step 2: Token Validation & Parsing
1. Validation call in `JwtAuthenticationFilter.java` (Line 47):
   `if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token))`
2. Method `validateToken` in `JwtTokenProvider.java` (Lines 48–64):
   ```java
   public boolean validateToken(String token) {
       try {
           parseClaims(token);
           return true;
       } catch (ExpiredJwtException | UnsupportedJwtException | MalformedJwtException | SecurityException | IllegalArgumentException e) {
           log.warn(...);
       }
       return false;
   }
   ```
3. Method `parseClaims` in `JwtTokenProvider.java` (Lines 31–37):
   ```java
   private Claims parseClaims(String token) {
       return Jwts.parser()
               .verifyWith(getSigningKey())
               .build()
               .parseSignedClaims(token)
               .getPayload();
   }
   ```
   - **Line 33**: Verifies HMAC-SHA256 signature using `getSigningKey()`.
   - **Line 35**: Parses claims and verifies expiration time (`exp`).
   - **Line 36**: Returns `Claims` payload map.

### 12.4 Step 3: Extracting Claims (Zero DB Hits)
In `JwtAuthenticationFilter.java`:
- **Line 50**: `UUID userId = jwtTokenProvider.getUserIdFromToken(token);`
  - Calls `JwtTokenProvider.java` (Lines 38–40):
    `UUID.fromString(parseClaims(token).getSubject());`
- **Line 62**: `String role = jwtTokenProvider.getRoleFromToken(token);`
  - Calls `JwtTokenProvider.java` (Lines 44–46):
    `parseClaims(token).get("role", String.class);`

### 12.5 Step 4: SecurityContext Registration
In `JwtAuthenticationFilter.java` (Lines 80–105):
```java
SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role); // Line 80

UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        userId.toString(),           // principal (Line 93)
        null,                        // credentials (Line 95)
        java.util.List.of(authority)  // authorities (Line 96)
);

authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // Line 99-101

SecurityContextHolder.getContext().setAuthentication(authentication); // Line 105
```
- **Lines 80–81**: Prepend `"ROLE_"` to create `ROLE_OWNER` authority required by `@PreAuthorize`.
- **Line 93**: Set principal to `userId.toString()`.
- **Line 105**: Store `authentication` object into Spring Security's `SecurityContext`.

### 12.6 Step 5: Filter Chain Continuation
In `JwtAuthenticationFilter.java` (Line 113):
`filterChain.doFilter(request, response);`
- Request continues to `SecurityConfig.java` authorization rules and `@PreAuthorize` method level security.

