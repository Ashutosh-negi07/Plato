# Plato — Security Architecture & Data Flow (Guide & Interview Presentation Document)

> **Target Audience**: Evaluators, Guides, Project Teachers, and Technical Interviewers.
> **Scope**: Complete, end-to-end architectural breakdown of Spring Security 3 & JWT in Plato. Covers both runtime data flows (Login vs. Authenticated Requests), component responsibilities, exact source file line mappings, sequence diagrams, and Q&A defense points.

---

## 1. Executive Summary & Architectural Overview

Plato uses a **Stateless, Token-Based Security Model** built on **Spring Security 3.5.5** and **JSON Web Tokens (JWT)** using HMAC-SHA256 signature algorithm.

### Key Architectural Principles:
1. **Stateless Sessions**: The server retains zero session memory (`SessionCreationPolicy.STATELESS`). No HTTP sessions or cookies are created or stored on the server.
2. **Dual-Path Security Architecture**:
   - **Staff / Admin Users** (`SUPER_ADMIN`, `OWNER`, `EMPLOYEE`): Authenticated via Email + Password $\rightarrow$ issued a 24-hour cryptographically signed JWT.
   - **Customers**: Authenticated via unique QR code table tokens (`X-Session-Token`) $\rightarrow$ managed by `CustomerSessionFilter` (Day 11), bypassing JWT.
3. **Zero-DB Authenticated Requests**: Once a staff user receives a valid JWT, every subsequent request is authenticated by cryptographically validating the token signature in memory. **No database lookup occurs during request authentication.**

---

## 2. Global Component Mapping

| Component Class | Source File Location | Primary Responsibility |
|---|---|---|
| `SecurityProperties` | [`backend/src/.../security/SecurityProperties.java`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/SecurityProperties.java) | Loads and validates `plato.jwt.secret` & `expiration` from `application.yml`. |
| `SecurityConfig` | [`backend/src/.../config/SecurityConfig.java`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java) | Central security rule definition: filter chain order, public vs. private path rules, stateless policy, bean exposure. |
| `JwtAuthenticationFilter` | [`backend/src/.../security/JwtAuthenticationFilter.java`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java) | Intercepts HTTP requests (`OncePerRequestFilter`), extracts `Bearer` token, validates it, and sets `SecurityContextHolder`. |
| `JwtTokenProvider` | [`backend/src/.../security/JwtTokenProvider.java`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java) | Cryptographic engine: generates signed JWTs on login; parses/verifies HMAC-SHA256 signature and `exp` claim on requests. |
| `UserDetailsServiceImpl` | [`backend/src/.../security/UserDetailsServiceImpl.java`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/UserDetailsServiceImpl.java) | Bridge between Spring Security and PostgreSQL: queries `users` table by email during login credential verification. |

---

## 3. Flow #1: The Login & JWT Generation Flow

> **Trigger**: Client sends `POST /api/v1/auth/login` with raw JSON `{ "email": "...", "password": "..." }`.

### 3.1 Sequence Diagram

```
Client / Postman             AuthController           AuthServiceImpl        AuthenticationManager    DaoAuthFilter / UserDetailsService      PostgreSQL DB         JwtTokenProvider
     │                             │                        │                         │                            │                           │                        │
     │── 1. POST /auth/login ─────►│                        │                         │                            │                           │                        │
     │   { email, password }       │── 2. login(dto) ──────►│                         │                            │                           │                        │
     │                             │                        │── 3. authenticate() ───►│                            │                           │                        │
     │                             │                        │      (Email + Password) │── 4. loadUserByUsername ──►│                           │                        │
     │                             │                        │                         │       (email)              │── 5. findByEmail() ──────►│                        │
     │                             │                        │                         │                            │◄─ 6. User Entity ─────────│                        │
     │                             │                        │                         │                            │                           │                        │
     │                             │                        │                         │◄─ 7. UserDetails ──────────│                        │                        │
     │                             │                        │                         │    (Hashed Password)       │                        │                        │
     │                             │                        │                         │                            │                        │                        │
     │                             │                        │                         │   [ Compare Passwords via ]│                        │                        │
     │                             │                        │                         │   [ BCrypt.matches()      ]│                        │                        │
     │                             │                        │◄─ 8. Auth Success ──────│                            │                        │                        │
     │                             │                        │                                                                                                           │
     │                             │                        │── 9. generateToken(userId, role) ────────────────────────────────────────────────────────────────────────►│
     │                             │                        │                                                                                                           │
     │                             │                        │◄─ 10. Base64 JWT String ("eyJhbGci...") ──────────────────────────────────────────────────────────────────│
     │                             │◄─ 11. LoginResponse ───│
     │◄── 12. 200 OK (JSON) ───────│
     │    { accessToken, type }
```

### 3.2 Detailed Step-by-Step Code Execution Path

#### Step 1: Endpoint Entry (`AuthController` & `SecurityConfig`)
- `POST /api/v1/auth/login` arrives.
- In [`SecurityConfig.java:65`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java#L65), `.requestMatchers("/api/v1/auth/**").permitAll()` permits access without needing a token.
- `AuthController.login()` receives `LoginRequest(email, password)` and calls `authService.login(request)`.

#### Step 2: Credential Verification via Spring Security (`AuthServiceImpl`)
- Inside `AuthServiceImpl.login()`:
  ```java
  Authentication authentication = authenticationManager.authenticate(
      new UsernamePasswordAuthenticationToken(request.email(), request.password())
  );
  ```
- `AuthenticationManager` (exposed as a Bean in [`SecurityConfig.java:125`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java#L125)) delegates to `DaoAuthenticationProvider` (exposed in [`SecurityConfig.java:106`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java#L106)).

#### Step 3: PostgreSQL Database Lookup (`UserDetailsServiceImpl`)
- `DaoAuthenticationProvider` calls `UserDetailsServiceImpl.loadUserByUsername(email)` in [`UserDetailsServiceImpl.java:43`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/UserDetailsServiceImpl.java#L43):
  ```java
  User user = userRepository.findByEmail(email)
          .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
  ```
- Line 53 maps the entity role to a Spring authority: `new SimpleGrantedAuthority("ROLE_" + user.getRole().name())`.
- Lines 57–63 wrap the entity into a Spring Security `UserDetails` object containing `user.getPasswordHash()`, `disabled` flag (`status == SUSPENDED`), and `accountLocked` flag (`status == DELETED`).

#### Step 4: BCrypt Password Hash Matching
- `DaoAuthenticationProvider` invokes `BCryptPasswordEncoder.matches(rawPassword, userDetails.getPassword())`.
- If passwords do not match $\rightarrow$ throws `BadCredentialsException` (translated to HTTP 401).
- If passwords match $\rightarrow$ returns authenticated `Authentication` object to `AuthServiceImpl`.

#### Step 5: JWT Generation (`JwtTokenProvider`)
- `AuthServiceImpl` loads the `User` entity to retrieve `userId` (`UUID`) and `role` (`String`).
- Calls `jwtTokenProvider.generateToken(userId, role)` in [`JwtTokenProvider.java:25`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L25):
  ```java
  public String generateToken(UUID userId, String role) {
      Date now = new Date();
      Date expiry = new Date(now.getTime() + securityProperties.getExpiration());

      return Jwts.builder()
              .subject(userId.toString())        // 'sub' claim = User UUID
              .claim("role", role)               // 'role' claim = "OWNER" / "SUPER_ADMIN" / "EMPLOYEE"
              .issuedAt(now)                     // 'iat' claim
              .expiration(expiry)                // 'exp' claim (24h)
              .signWith(getSigningKey())          // HMAC-SHA256 signing
              .compact();
  }
  ```
- `getSigningKey()` (line 18) reads `securityProperties.getSecret()` and creates a `javax.crypto.SecretKey` using `Keys.hmacShaKeyFor()`.
- `.compact()` serializes the header, payload, and HMAC signature into a 3-part Base64 URL-safe string: `eyJhbGci...`.

#### Step 6: Response Delivery
- `AuthServiceImpl` wraps token in `LoginResponse(token, "Bearer")`.
- HTTP 200 OK returned to client.

---

## 4. Flow #2: The Authenticated Request Flow

> **Trigger**: Client sends a request to any protected endpoint (e.g. `GET /api/v1/restaurants`) with HTTP Header `Authorization: Bearer <token>`.

### 4.1 Sequence Diagram

```
Client / Frontend              JwtAuthenticationFilter               JwtTokenProvider             SecurityContextHolder          RestaurantController
       │                                  │                                  │                              │                             │
       │── 1. GET /api/v1/restaurants ───►│                                  │                              │                             │
       │   Header: Authorization: Bearer  │                                  │                              │                             │
       │                                  │── 2. extractTokenFromRequest()  │                              │                             │
       │                                  │      (Strips "Bearer " prefix)   │                              │                             │
       │                                  │                                  │                              │                             │
       │                                  │── 3. validateToken(token) ──────►│                              │                             │
       │                                  │                                  │── 4. parseClaims(token)      │                             │
       │                                  │                                  │   (Verify HMAC-SHA256 & exp) │                             │
       │                                  │◄─ 5. Valid (true) ───────────────│                              │                             │
       │                                  │                                                                 │                             │
       │                                  │── 6. getUserIdFromToken() ──────►│                              │                             │
       │                                  │◄─ 7. UUID ("550e8400-...") ──────│                              │                             │
       │                                  │                                                                 │                             │
       │                                  │── 8. getRoleFromToken() ────────►│                              │                             │
       │                                  │◄─ 9. Role ("OWNER") ─────────────│                              │                             │
       │                                  │                                                                 │                             │
       │                                  │── 10. setAuthentication(authToken) ────────────────────────────►│                             │
       │                                  │       (Principal=userId, Authority=ROLE_OWNER)                  │                             │
       │                                  │                                                                 │                             │
       │                                  │── 11. filterChain.doFilter(request, response) ───────────────────────────────────────────────►│
       │                                  │                                                                                               │ [ Pass @PreAuthorize ]
       │                                  │                                                                                               │ [ Return Response   ]
       │◄── 12. 200 OK (JSON Response) ─────────────────────────────────────────────────────────────────────────────────────────────────│
```

### 4.2 Detailed Step-by-Step Code Execution Path

#### Step 1: Interception (`JwtAuthenticationFilter`)
- Request hits Tomcat and enters Spring Security's filter pipeline.
- `JwtAuthenticationFilter` (registered in [`SecurityConfig.java:92`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java#L92) before `UsernamePasswordAuthenticationFilter`) intercepts the request in `doFilterInternal()` ([`JwtAuthenticationFilter.java:37`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java#L37)).

#### Step 2: Header Extraction (`JwtAuthenticationFilter`)
- Line 44 calls `extractTokenFromRequest(request)` ([`JwtAuthenticationFilter.java:120`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java#L120)):
  ```java
  String bearerToken = request.getHeader("Authorization");
  if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7); // Removes "Bearer " prefix (7 chars)
  }
  return null;
  ```

#### Step 3: Cryptographic Verification without DB Access (`JwtTokenProvider`)
- Line 47 calls `jwtTokenProvider.validateToken(token)` ([`JwtTokenProvider.java:48`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L48)).
- `validateToken()` delegates to `parseClaims(token)` ([`JwtTokenProvider.java:31`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L31)):
  ```java
  private Claims parseClaims(String token) {
      return Jwts.parser()
              .verifyWith(getSigningKey())   // Signature check
              .build()
              .parseSignedClaims(token)      // Expiration & structure check
              .getPayload();
  }
  ```
- **Validation Rules**:
  - Re-computes HMAC-SHA256 over Header + Payload using `getSigningKey()`. If signature doesn't match $\rightarrow$ throws `SecurityException`.
  - Checks if `exp` claim timestamp < current time $\rightarrow$ throws `ExpiredJwtException`.
  - Checks if string is malformed $\rightarrow$ throws `MalformedJwtException`.
  - If any exception occurs $\rightarrow$ `validateToken()` catches it, logs a warning, and returns `false`.

#### Step 4: Claim Extraction (`JwtTokenProvider`)
- If `validateToken()` returns `true`:
  - `jwtTokenProvider.getUserIdFromToken(token)` ([`JwtTokenProvider.java:38`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L38)) extracts the `sub` claim and converts it to `UUID`.
  - `jwtTokenProvider.getRoleFromToken(token)` ([`JwtTokenProvider.java:44`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L44)) extracts the `role` claim (`"OWNER"`).

#### Step 5: Security Context Registration (`JwtAuthenticationFilter`)
- Inside [`JwtAuthenticationFilter.java:80–105`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java#L80):
  ```java
  // 1. Map role claim to GrantedAuthority
  SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

  // 2. Build Authentication Token with userId string as principal
  UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          userId.toString(),
          null,
          List.of(authority)
  );

  authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

  // 3. Register authentication into Thread-Local context
  SecurityContextHolder.getContext().setAuthentication(authentication);
  ```

#### Step 6: Filter Continuation & Controller Authorization
- Line 113 calls `filterChain.doFilter(request, response)` to pass the request down the chain.
- Spring Security checks:
  1. `SecurityConfig` rules: path requires `authenticated()` $\rightarrow$ SecurityContext contains authentication $\rightarrow$ PASSED.
  2. Method Security (`@PreAuthorize("hasRole('OWNER')")`): checks if `ROLE_OWNER` is in `authorities` $\rightarrow$ PASSED.
- Target Controller method executes. Inside any service or controller, the authenticated user ID can be retrieved via:
  ```java
  String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  ```

---

## 5. Defense Points & Guide Q&A Strategy

When presenting this architecture to a teacher, guide, or evaluator, use these exact technical points to demonstrate depth:

### Q1: "Why do you store `userId` in the JWT `sub` instead of `email`?"
> **Answer**: `userId` is a UUID primary key. It is immutable—a user's email might change, but their UUID will never change. Furthermore, storing a non-sequential UUID in the token prevents enumeration attacks while allowing downstream microservices or controllers to query data directly by primary key without extra DB joins.

### Q2: "Why do you add `ROLE_` prefix in Java instead of storing it in the database or JWT?"
> **Answer**: In our database, roles are stored clean (`OWNER`, `EMPLOYEE`, `SUPER_ADMIN`) as PostgreSQL ENUMs (`user_role`). Storing `"OWNER"` keeps DB and JWT payloads concise and clean. Spring Security's `hasRole('OWNER')` expression natively expects authorities prefixed with `ROLE_` (`ROLE_OWNER`). Therefore, we prepend `ROLE_` at runtime in `UserDetailsServiceImpl` and `JwtAuthenticationFilter`.

### Q3: "How does your system scale if 10,000 users make requests simultaneously?"
> **Answer**: Extremely well, because our request flow is **100% stateless**. Because `JwtAuthenticationFilter` validates the cryptographic signature in memory using the secret key, zero database queries are executed for authentication. The database is only queried during the initial login request.

### Q4: "What prevents a user from modifying their role inside the JWT payload?"
> **Answer**: The HMAC-SHA256 signature. A JWT payload is Base64 encoded (readable), but the signature is generated by hashing the header and payload together with our secret key (`JWT_SECRET`). If an attacker changes `"role": "EMPLOYEE"` to `"role": "SUPER_ADMIN"`, the signature calculation will fail in `JwtTokenProvider.parseClaims()`, throwing a `SecurityException` and immediately returning 401 Unauthorized.

### Q5: "How are public vs private endpoints managed?"
> **Answer**: Centralized in [`SecurityConfig.java`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java#L63). Auth endpoints (`/api/v1/auth/**`), QR endpoints (`/api/v1/qr/**`), and Swagger docs are marked `.permitAll()`. Everything else defaults to `.anyRequest().authenticated()`.

---

## 6. Exact File & Line Reference Summary

```
Plato Security Architecture Mappings:

1. Configuration & Rules:
   - SecurityProperties.java:18 ──► Secret Key & Expiration fields
   - SecurityConfig.java:40     ──► SecurityFilterChain bean definition
   - SecurityConfig.java:65     ──► permitAll() path matcher rules
   - SecurityConfig.java:92     ──► addFilterBefore(jwtAuthenticationFilter)
   - SecurityConfig.java:106    ──► DaoAuthenticationProvider bean
   - SecurityConfig.java:125    ──► AuthenticationManager bean

2. Request Interception:
   - JwtAuthenticationFilter.java:37  ──► doFilterInternal() filter entry
   - JwtAuthenticationFilter.java:120 ──► extractTokenFromRequest()
   - JwtAuthenticationFilter.java:47  ──► Token validation check
   - JwtAuthenticationFilter.java:80  ──► SimpleGrantedAuthority("ROLE_" + role)
   - JwtAuthenticationFilter.java:105 ──► SecurityContextHolder.getContext().setAuthentication()

3. Database Authentication & Cryptography:
   - UserDetailsServiceImpl.java:43 ──► userRepository.findByEmail(email)
   - UserDetailsServiceImpl.java:57 ──► Building Spring Security UserDetails
   - JwtTokenProvider.java:18       ──► getSigningKey() (HMAC-SHA256)
   - JwtTokenProvider.java:25       ──► generateToken(userId, role)
   - JwtTokenProvider.java:31       ──► parseClaims(token)
   - JwtTokenProvider.java:48       ──► validateToken(token)
```

