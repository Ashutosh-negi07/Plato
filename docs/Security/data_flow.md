# Security & JWT — Complete Data Flow

> Every request that touches the security layer traced line by line.
> References the actual files in the codebase.

---

## The Big Picture — Two Separate Security Flows

```
FLOW A: Login
  Client sends { email, password }
      → AuthController
      → AuthServiceImpl (verifies credentials via AuthenticationManager)
      → JwtTokenProvider.generateToken()
      → Returns a signed JWT to the client

FLOW B: Every subsequent request
  Client sends Authorization: Bearer <JWT>
      → JwtAuthenticationFilter runs (before any controller)
      → Validates JWT
      → Reads role from JWT claims (no DB hit needed)
      → Sets Authentication in SecurityContextHolder
      → Request reaches controller
      → @PreAuthorize checks the SecurityContext
```

---

## The Filter Chain — How every HTTP request is processed

Spring Security wraps your app in a chain of filters. Every request passes through ALL of them:

```
HTTP Request arrives
       |
       v
[Filter 1] CorsFilter
[Filter 2] ...
[Filter N] JwtAuthenticationFilter        ← YOUR custom filter
[Filter N+1] UsernamePasswordAuthenticationFilter  ← Spring default (login form — unused here)
       |
       v
DispatcherServlet → Controller
```

`SecurityConfig.java line 92`:
```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
// YOUR filter runs BEFORE the default login filter
// This ensures JWT is processed early enough to set the SecurityContext
// before Spring checks authorization rules
```

---

## FLOW A — Login: POST /api/v1/auth/login

### Request
```
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "alice@restaurant.com",
  "password": "secret123"
}
```

---

### Step-by-step trace

**STEP 1 — SecurityConfig allows /auth/** without a token**
```java
// SecurityConfig.java line 65
.requestMatchers("/api/v1/auth/**").permitAll()
// This path is in the "permit all" list
// JwtAuthenticationFilter still runs, but finds no token
// It just calls filterChain.doFilter() and moves on
// The request reaches the controller without being blocked
```

**STEP 2 — AuthController receives the request**
```java
// AuthController.java
@PostMapping("/login")
public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request) {
    // request.email()    = "alice@restaurant.com"
    // request.password() = "secret123"  ← still plaintext
```

**STEP 3 — AuthServiceImpl.login() begins**
```java
// AuthServiceImpl.java line 35
authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
                request.email(),    // "alice@restaurant.com"
                request.password()  // "secret123"
        )
);
// UsernamePasswordAuthenticationToken is a container for credentials.
// Passing it to authenticate() triggers Spring Security's verification chain.
```

**STEP 4 — AuthenticationManager calls DaoAuthenticationProvider**
```java
// This is wired in SecurityConfig.java lines 106-110:
// provider.setUserDetailsService(userDetailsService);
// provider.setPasswordEncoder(passwordEncoder);
//
// DaoAuthenticationProvider does two things:
//   A. Calls UserDetailsServiceImpl.loadUserByUsername("alice@restaurant.com")
//   B. Calls BCrypt.matches("secret123", storedHash)
```

**STEP 4A — UserDetailsServiceImpl.loadUserByUsername()**
```java
// UserDetailsServiceImpl.java line 46
User user = userRepository.findByEmail(email)
//          SQL: SELECT * FROM users WHERE email = 'alice@restaurant.com'
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
// If user not found → UsernameNotFoundException → AuthenticationException → 401

// Line 55: converts our UserRole to Spring Security format
SimpleGrantedAuthority authority =
        new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
//  user.getRole() = UserRole.OWNER
//  "ROLE_" + "OWNER" = "ROLE_OWNER"
//  Spring Security's hasRole('OWNER') checks for "ROLE_OWNER"

// Lines 59-65: builds Spring's UserDetails object
return org.springframework.security.core.userdetails.User.builder()
        .username(user.getEmail())          // "alice@restaurant.com"
        .password(user.getPasswordHash())   // "$2a$10$storedBCryptHash"
        .authorities(List.of(authority))    // ["ROLE_OWNER"]
        .disabled(user.getStatus() == UserStatus.SUSPENDED)
//      If suspended → disabled = true → Spring throws DisabledException → 401
        .accountLocked(user.getStatus() == UserStatus.DELETED)
//      If deleted → locked = true → Spring throws LockedException → 401
        .build();
```

**STEP 4B — BCrypt password verification**
```java
// DaoAuthenticationProvider internally calls:
passwordEncoder.matches("secret123", "$2a$10$storedBCryptHash")
// BCrypt:
//   1. Extracts salt from the stored hash
//   2. Hashes "secret123" with that salt
//   3. Compares result to the stored hash
// Returns true → credentials valid
// Returns false → BadCredentialsException → 401
```

**STEP 5 — Back in AuthServiceImpl: credentials verified**
```java
// AuthServiceImpl.java line 47
User user = userRepository.findByEmail(request.email()).orElseThrow();
// Second DB hit — needed to get the User entity with id and role for the token
// (the UserDetails object from Step 4A doesn't expose our UUID — it uses email as "username")

// Line 51-52: update last_login
user.setLastLogin(LocalDateTime.now());
userRepository.save(user);
// SQL: UPDATE users SET last_login = now() WHERE id = '...'
```

**STEP 6 — JWT generation**
```java
// AuthServiceImpl.java line 55
String token = jwtTokenProvider.generateToken(
        user.getId(),           // UUID: "550e8400-..."
        user.getRole().name()   // "OWNER"
);
```

**Inside JwtTokenProvider.generateToken()**:
```java
// JwtTokenProvider.java line 25
public String generateToken(UUID userId, String role) {
    Date now    = new Date();
    Date expiry = new Date(now.getTime() + securityProperties.getExpiration());
    //  now + 86400000ms = now + 24 hours

    return Jwts.builder()
            .subject(userId.toString())  // sub claim = "550e8400-..."
            .claim("role", role)         // custom claim: role = "OWNER"
            .issuedAt(now)               // iat claim = current timestamp
            .expiration(expiry)          // exp claim = 24h from now
            .signWith(getSigningKey())   // signs with HMAC-SHA256 using the secret key
            .compact();
    // Output: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1NTBlODQwMC4uLiIsInJvbGUiOiJPV05FUiIsImlhdCI6Li4uLCJleHAiOi4uLn0.SIGNATURE"
    //          ^^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^
    //          Header (base64)       Payload (base64)                                                         Signature
}
```

**Inside getSigningKey()**:
```java
// JwtTokenProvider.java line 18
private SecretKey getSigningKey() {
    byte[] keyBytes = securityProperties.getSecret().getBytes();
    //  Reads "plato.jwt.secret" from application.yml
    //  Converts the string secret to raw bytes
    return Keys.hmacShaKeyFor(keyBytes);
    //  Creates an HMAC-SHA256 signing key from those bytes
}
```

**STEP 7 — AuthServiceImpl returns LoginResponse**
```java
// AuthServiceImpl.java line 62
return new LoginResponse(token, "Bearer", user.getRole().name(), user.getFullName());
// token     = "eyJhbGciOi..."
// tokenType = "Bearer" — HTTP standard prefix for JWT tokens
// role      = "OWNER"
// name      = "Alice Owner"
```

**STEP 8 — Response sent to client**
```json
HTTP 200 OK
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "role": "OWNER",
    "name": "Alice Owner"
  }
}
```

---

## FLOW B — Protected Request: GET /api/v1/users

### Request
```
GET /api/v1/users
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

### Step-by-step trace

**STEP 1 — JwtAuthenticationFilter.doFilterInternal() begins (line 37)**
```java
// JwtAuthenticationFilter.java line 44
String token = extractTokenFromRequest(request);
// calls extractTokenFromRequest() — see below
```

**Inside extractTokenFromRequest():**
```java
// JwtAuthenticationFilter.java line 120
String bearerToken = request.getHeader("Authorization");
//  reads: "Bearer eyJhbGciOiJIUzI1NiJ9..."

// Line 124
if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
//  StringUtils.hasText() = not null AND not empty AND not only whitespace
//  bearerToken.startsWith("Bearer ") = has the prefix

    return bearerToken.substring(7);
    //  "Bearer " is 7 characters — strips it off
    //  returns just: "eyJhbGciOiJIUzI1NiJ9..."
}
return null;  // no Authorization header → no token
```

**STEP 2 — Token validation**
```java
// JwtAuthenticationFilter.java line 47
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
//  token not null/empty AND valid?
```

**Inside JwtTokenProvider.validateToken():**
```java
// JwtTokenProvider.java line 48
public boolean validateToken(String token) {
    try {
        parseClaims(token);  // throws if anything is wrong
        return true;
    } catch (ExpiredJwtException e)     { log.warn("expired");    }
    catch (MalformedJwtException e)     { log.warn("malformed");  }
    catch (SecurityException e)         { log.warn("bad sig");    }
    catch (IllegalArgumentException e)  { log.warn("empty");      }
    return false;
}
```

**Inside parseClaims():**
```java
// JwtTokenProvider.java line 31
private Claims parseClaims(String token) {
    return Jwts.parser()
            .verifyWith(getSigningKey())   // uses same secret to verify HMAC signature
            .build()
            .parseSignedClaims(token)      // parses all 3 parts of the JWT
            .getPayload();                 // returns the claims (sub, role, iat, exp)
    // If signature doesn't match → SecurityException
    // If exp is in the past → ExpiredJwtException
    // If format is wrong → MalformedJwtException
}
```

**STEP 3 — Extract userId from token (no DB hit)**
```java
// JwtAuthenticationFilter.java line 50
UUID userId = jwtTokenProvider.getUserIdFromToken(token);
// Inside getUserIdFromToken():
//   parseClaims(token).getSubject()  → "550e8400-..."
//   UUID.fromString("550e8400-...")  → UUID object
```

**STEP 4 — Extract role from token (no DB hit)**
```java
// JwtAuthenticationFilter.java line 62
String role = jwtTokenProvider.getRoleFromToken(token);
// Inside getRoleFromToken():
//   parseClaims(token).get("role", String.class)  → "OWNER"
```

**STEP 5 — Build GrantedAuthority**
```java
// JwtAuthenticationFilter.java line 85
SimpleGrantedAuthority authority =
        new SimpleGrantedAuthority("ROLE_" + role);
//  "ROLE_" + "OWNER" = "ROLE_OWNER"
//  This is what @PreAuthorize("hasRole('ROLE_OWNER')") checks for
```

**STEP 6 — Build Authentication object**
```java
// JwtAuthenticationFilter.java line 92
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                userId.toString(),           // principal = "550e8400-..."
                null,                        // credentials = null (no password needed)
                java.util.List.of(authority) // authorities = ["ROLE_OWNER"]
        );
// This 3-argument constructor creates a FULLY authenticated token
// (isAuthenticated() returns true)
// The 2-argument constructor (no authorities) creates an unauthenticated token
// — used only during credential verification
```

**STEP 7 — Attach request metadata**
```java
// JwtAuthenticationFilter.java line 100
authentication.setDetails(
        new WebAuthenticationDetailsSource().buildDetails(request)
);
// Stores IP address, session ID — useful for audit logs
```

**STEP 8 — Set in SecurityContext**
```java
// JwtAuthenticationFilter.java line 105
SecurityContextHolder.getContext().setAuthentication(authentication);
// SecurityContextHolder = thread-local storage for the current request
// Everything downstream (controllers, @PreAuthorize) reads from here
// At end of request, Spring clears it automatically
```

**STEP 9 — Pass to next filter**
```java
// JwtAuthenticationFilter.java line 113
filterChain.doFilter(request, response);
// Filter chain continues
// Eventually reaches the controller
```

**STEP 10 — SecurityConfig authorization rules fire**
```java
// SecurityConfig.java line 80
.anyRequest().authenticated()
// Since SecurityContext now has a valid Authentication object,
// isAuthenticated() = true → rule passes → request reaches controller
```

**STEP 11 — @PreAuthorize runs on the controller method**
```java
// UserController.java
@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
// Spring reads authentication from SecurityContextHolder
// Checks: does List.of("ROLE_OWNER") contain "ROLE_SUPER_ADMIN"?
// NO → AccessDeniedException → GlobalExceptionHandler → 403
```

---

## FLOW C — Invalid or Missing Token

### Case 1: No Authorization header
```
JwtAuthenticationFilter.extractTokenFromRequest() → returns null
StringUtils.hasText(null) = false
→ skip the if block
→ filterChain.doFilter() called
→ SecurityContext is empty (no authentication set)
→ SecurityConfig: .anyRequest().authenticated() → fails
→ Spring returns 401 Unauthorized automatically
```

### Case 2: Expired token
```
validateToken(token) calls parseClaims()
parseClaims() → Jwts.parser throws ExpiredJwtException
validateToken catches it → logs warning → returns false
→ skip the if block
→ SecurityContext is empty → 401
```

### Case 3: Tampered token (signature mismatch)
```
Someone changes the payload (e.g. role: "OWNER" → "SUPER_ADMIN")
parseClaims() recomputes the signature with the secret key
New payload → different HMAC → doesn't match the original signature
→ SecurityException
→ validateToken returns false → 401
```

---

## JWT Structure — What's inside the token

A JWT has 3 parts separated by dots, all base64-encoded:

```
eyJhbGciOiJIUzI1NiJ9  .  eyJzdWIiOiI1NTBlODQwMC4uLiIsInJvbGUiOiJPV05FUiJ9  .  SIGNATURE
^^^^^^^^^^^^^^^^^^^^^     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^
Header                    Payload (Claims)                                          Signature

Header decoded:
{
  "alg": "HS256"    ← HMAC-SHA256 algorithm
}

Payload decoded:
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  ← userId
  "role": "OWNER",                                 ← role claim
  "iat": 1723718400,                               ← issued at (unix timestamp)
  "exp": 1723804800                                ← expires at (iat + 86400s)
}

Signature:
  HMAC-SHA256(base64(header) + "." + base64(payload), secretKey)
  This is what you CANNOT fake without knowing the secretKey
```

---

## SecurityProperties — Where config values come from

```java
// SecurityProperties.java
@ConfigurationProperties(prefix = "plato.jwt")
// Reads all "plato.jwt.*" from application.yml or environment variables

private String secret;       // plato.jwt.secret = "your-secret-key"
private long expiration = 86400000L;  // plato.jwt.expiration (default 24h in ms)
```

```yaml
# application-local.yml (git-ignored)
plato:
  jwt:
    secret: "super-secret-key-at-least-32-chars"
    expiration: 86400000   # 24 hours in milliseconds
```

---

## Full call graph

```
POST /api/v1/auth/login
  AuthController.login()
    AuthServiceImpl.login()
      authenticationManager.authenticate()
        DaoAuthenticationProvider.authenticate()
          UserDetailsServiceImpl.loadUserByUsername()   ← DB hit 1: SELECT by email
          BCrypt.matches()                               ← CPU: hash comparison
      UserRepository.findByEmail()                       ← DB hit 2: get entity
      UserRepository.save()                             ← DB hit 3: update last_login
      JwtTokenProvider.generateToken()                  ← CPU: sign JWT
    → LoginResponse { token, role, name }

GET /api/v1/users (with JWT)
  JwtAuthenticationFilter.doFilterInternal()            ← runs before controller
    extractTokenFromRequest()                            ← read Authorization header
    JwtTokenProvider.validateToken()                     ← CPU: verify signature
    JwtTokenProvider.getUserIdFromToken()               ← parse sub claim
    JwtTokenProvider.getRoleFromToken()                 ← parse role claim
    SecurityContextHolder.setAuthentication()           ← set auth in thread
  SecurityConfig authorization rules                    ← check isAuthenticated()
  @PreAuthorize on controller                           ← check ROLE_*
  UserController.getAllUsers()
    UserServiceImpl.getAllUsers()
      UserRepository.findAll(pageable)                  ← DB hit: SELECT with LIMIT
      UserMapper.toResponse()                           ← entity → DTO
    → Page<UserResponse>
```


---

## SecurityConfig.java — How every request is routed

> This file runs BEFORE any controller, BEFORE your JWT filter even starts.
> It is the bouncer at the door of the entire application.

---

### What SecurityConfig actually builds

`securityFilterChain()` returns a `SecurityFilterChain` — a configured pipeline
of rules. Spring registers it as a `@Bean` and applies it to every incoming request.

```java
// SecurityConfig.java line 41
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
// HttpSecurity is a builder — you chain configuration methods on it.
// http.csrf(...).cors(...).sessionManagement(...).authorizeHttpRequests(...)
// Each method configures one aspect of the security pipeline.
// .build() at the end constructs the actual filter chain.
```

---

### Step-by-step: what happens when a request arrives

**Every single HTTP request — no exceptions — passes through this chain in order:**

```
Incoming HTTP Request
        |
        v
[1] CSRF filter — disabled (AbstractHttpConfigurer::disable line 47)
        |
        v
[2] CORS filter — disabled for now (line 52)
        |
        v
[3] Session filter — checks SessionCreationPolicy.STATELESS (line 57)
     → Spring will NEVER create or read an HttpSession
     → Every request is treated as brand new
        |
        v
[4] JwtAuthenticationFilter (line 92 — addFilterBefore)
     → YOUR filter, runs here
     → Reads Authorization header, validates JWT, sets SecurityContextHolder
        |
        v
[5] UsernamePasswordAuthenticationFilter (Spring default — login form)
     → Our JWT filter runs BEFORE this one
     → We bypass this entirely (we have our own login endpoint)
        |
        v
[6] Authorization check: authorizeHttpRequests rules (lines 63-80)
     → Matches the request URL against the rules, in order
     → First match wins
        |
        v
DispatcherServlet → Controller
```

---

### authorizeHttpRequests — how URL matching works

```java
// SecurityConfig.java lines 63-80
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**").permitAll()
        .requestMatchers("/api/v1/qr/**").permitAll()
        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/api/v1/customer/**").permitAll()
        .anyRequest().authenticated()
)
```

**Rule: first match wins. Order matters.**

| Request URL | Matches rule | Decision |
|-------------|-------------|---------|
| `POST /api/v1/auth/login` | `.requestMatchers("/api/v1/auth/**")` | `permitAll()` — no token needed |
| `GET /api/v1/qr/scan` | `.requestMatchers("/api/v1/qr/**")` | `permitAll()` — no token needed |
| `GET /swagger-ui/index.html` | `.requestMatchers("/swagger-ui/**")` | `permitAll()` — no token needed |
| `GET /actuator/health` | `.requestMatchers("/actuator/health")` | `permitAll()` — no token needed |
| `GET /api/v1/users` | `.anyRequest()` | `authenticated()` — JWT required |
| `POST /api/v1/restaurants` | `.anyRequest()` | `authenticated()` — JWT required |

**`**` means any path segment:**
- `/api/v1/auth/**` matches `/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/auth/refresh`, etc.

**`anyRequest().authenticated()` is the catch-all:**
- Any URL not matched by an earlier rule lands here
- `authenticated()` = SecurityContext must have a valid `Authentication` object
- JwtAuthenticationFilter sets this (Step 4 above) if token is valid
- If no token or invalid token → SecurityContext is empty → 401

---

### CSRF — disabled at line 47

```java
.csrf(AbstractHttpConfigurer::disable)
// CSRF (Cross-Site Request Forgery):
//   A malicious site tricks your browser into making a request to our API
//   while your session cookie is attached automatically by the browser.
//
// Why we're safe without it:
//   We use JWT in the "Authorization: Bearer <token>" header.
//   Browsers CANNOT attach custom headers cross-origin — it's a browser security rule.
//   So a cross-site request to our API will have no Authorization header → no JWT → 401.
//   CSRF tokens would be redundant overhead.
//
// When you SHOULD keep CSRF enabled:
//   If you ever use cookie-based sessions (e.g. a Thymeleaf web app).
//   Browsers DO attach cookies cross-origin → CSRF token needed to verify intent.
```

---

### CORS — disabled at line 52

```java
.cors(AbstractHttpConfigurer::disable)
// CORS (Cross-Origin Resource Sharing):
//   Browser security prevents a page on domain-a.com from making API calls
//   to domain-b.com unless domain-b.com explicitly allows it via CORS headers.
//
// Currently disabled = no CORS headers added to responses.
// This means: requests from a browser on a different origin will be blocked
// by the browser (server still processes them, but browser hides the response).
//
// Fine for development (Postman ignores CORS — it's not a browser).
// Must be properly configured before the frontend connects in production:
//   .cors(cors -> cors.configurationSource(corsConfigurationSource()))
// Where corsConfigurationSource() specifies allowed origins, methods, headers.
```

---

### SessionCreationPolicy.STATELESS — line 57

```java
.sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
// Spring has 4 session policies:
//   ALWAYS     — create a session if one doesn't exist
//   IF_REQUIRED — create a session if something needs one (default)
//   NEVER      — never create one, but use one if it exists
//   STATELESS  — never create, never use — treat every request as new
//
// STATELESS is mandatory for JWT-based APIs:
// 1. Scalability: any server in a load-balanced cluster can handle any request
//    because no session state lives on any server
// 2. No JSESSIONID cookie is ever set — clients can't accidentally rely on it
// 3. Forces the client to always send the JWT — no "phantom authentication"
//    from a lingering browser session
```

---

### authenticationProvider() — line 106

```java
@Bean
public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);   // how to load user by email
    provider.setPasswordEncoder(passwordEncoder);         // how to verify password (BCrypt)
    return provider;
}
// DaoAuthenticationProvider is the glue between:
//   - "load user from DB"  → UserDetailsServiceImpl.loadUserByUsername()
//   - "verify password"    → BCryptPasswordEncoder.matches()
//
// When AuthServiceImpl calls:
//   authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, pass))
//
// Internally Spring calls:
//   1. provider.loadUserDetails(token)    → UserDetailsServiceImpl.loadUserByUsername(email)
//   2. provider.additionalAuthenticationChecks(userDetails, token)
//      → BCrypt.matches(rawPassword, storedHash)
//   3a. match → return authenticated token
//   3b. no match → throw BadCredentialsException → GlobalExceptionHandler → 401
```

---

### authenticationManager() — line 124

```java
@Bean
public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
// AuthenticationManager is the entry point for triggering authentication.
// It receives an unauthenticated token and returns an authenticated one (or throws).
//
// Why expose it as a @Bean?
//   Spring doesn't expose it as a bean by default in Spring Security 5.7+.
//   AuthServiceImpl needs to inject it:
//     private final AuthenticationManager authenticationManager;
//   Without this @Bean, injection fails at startup.
//
// getAuthenticationManager() returns the manager that uses all registered
// AuthenticationProviders — in our case, the DaoAuthenticationProvider above.
```

---

### Full SecurityConfig request routing diagram

```
Request: POST /api/v1/auth/login (no token)
  CSRF disabled → pass
  CORS disabled → pass
  STATELESS → no session created
  JwtAuthenticationFilter:
    extractToken() → null (no Authorization header)
    if (token != null && valid) → false → skip
    filterChain.doFilter() → pass through
  authorizeHttpRequests:
    "/api/v1/auth/**" → permitAll() → PASS
  Controller reached → AuthController.login()

---

Request: GET /api/v1/users (with valid JWT)
  CSRF disabled → pass
  CORS disabled → pass
  STATELESS → no session
  JwtAuthenticationFilter:
    extractToken() → "eyJhbGci..."
    validateToken() → true
    getUserIdFromToken() → UUID
    getRoleFromToken() → "SUPER_ADMIN"
    SimpleGrantedAuthority → "ROLE_SUPER_ADMIN"
    SecurityContextHolder.setAuthentication() ← AUTHENTICATED
    filterChain.doFilter() → pass through
  authorizeHttpRequests:
    anyRequest().authenticated() → isAuthenticated() = true → PASS
  @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')"):
    "ROLE_SUPER_ADMIN" in authorities → true → PASS
  Controller reached → UserController.getAllUsers()

---

Request: GET /api/v1/users (no token)
  JwtAuthenticationFilter:
    extractToken() → null
    skip → SecurityContext EMPTY
  authorizeHttpRequests:
    anyRequest().authenticated() → isAuthenticated() = false → BLOCKED
  Spring returns 401 Unauthorized automatically
  Controller NEVER reached

---

Request: GET /api/v1/users (OWNER token, not SUPER_ADMIN)
  JwtAuthenticationFilter:
    role → "OWNER"
    authorities → ["ROLE_OWNER"]
    SecurityContextHolder.setAuthentication() ← AUTHENTICATED as OWNER
  authorizeHttpRequests:
    anyRequest().authenticated() → true → PASS (just checks if authenticated, not role)
  @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')"):
    "ROLE_OWNER" ≠ "ROLE_SUPER_ADMIN" → false → AccessDeniedException
  GlobalExceptionHandler → 403 Forbidden
  Controller NEVER reached
```

