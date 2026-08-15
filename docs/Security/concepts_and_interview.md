# Security & JWT — Concepts, Annotations & Interview Prep

---

## File-by-File Breakdown

---

### SecurityConfig.java — The Master Security Configuration

**What it is**: The single file that defines ALL security rules for the application.
Which paths are public, which need a JWT, how sessions work, where the JWT filter sits.

**Why it exists**: Spring Security has no idea what your routes are or who should access them.
This file tells it everything.

**Key annotations explained**:

```java
@Configuration
// Tells Spring: this class produces Spring beans via @Bean methods.
// The securityFilterChain, authenticationProvider, and authenticationManager
// methods are all @Bean — Spring manages them.

@EnableWebSecurity
// Activates Spring Security's HTTP security support.
// Without this, all the .csrf(), .authorizeHttpRequests() etc. do nothing.

@EnableMethodSecurity
// Activates @PreAuthorize on controller methods.
// Without this, @PreAuthorize("hasRole(...)") is silently ignored —
// every endpoint becomes accessible regardless.
```

**CSRF disabled — why?**:
```java
.csrf(AbstractHttpConfigurer::disable)
// CSRF (Cross-Site Request Forgery) exploits browsers automatically sending
// cookies on cross-origin requests. Since we use JWT in the Authorization header
// (not cookies), a cross-site request can't inject our header.
// Safe to disable for stateless REST APIs.
```

**Stateless sessions — why?**:
```java
.sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
// STATELESS = Spring never creates an HttpSession.
// Every request must be self-contained with its JWT.
// Traditional apps use server-side sessions (Spring creates a JSESSIONID cookie).
// Stateless is required for horizontal scaling — any server can handle any request
// because no session state is stored server-side.
```

**addFilterBefore — why BEFORE?**:
```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
// Our JWT filter must run BEFORE the default authentication filter.
// If it ran after: the SecurityContext would already be checked and possibly
// rejected before our filter gets to set the authentication.
```

---

### JwtAuthenticationFilter.java — The Gatekeeper

**What it is**: A Spring Security filter that runs on every HTTP request.
Reads the JWT from the header, validates it, and sets the authentication
in Spring's SecurityContext so controllers can check it.

**Key annotation/class**:

```java
extends OncePerRequestFilter
// Spring's base class that guarantees doFilterInternal() runs EXACTLY ONCE per request.
// Without this guarantee, some edge cases (forwards, includes) could trigger the filter
// multiple times — causing double authentication processing.

// Why not implement javax.servlet.Filter directly?
// OncePerRequestFilter handles the once-per-request guarantee for you.
// It also handles exceptions properly within the filter chain.
```

**doFilterInternal() — the core method**:
```java
protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
) throws ServletException, IOException {
// @NonNull = Lombok/Spring annotation saying "this will never be null"
// FilterChain = the rest of the filter pipeline — must call filterChain.doFilter()
// to pass the request forward OR the request stops here
```

**The critical last line**:
```java
filterChain.doFilter(request, response);
// ALWAYS called — even if there's no token.
// If no token: SecurityContext stays empty → downstream rules handle it (usually 401)
// If valid token: SecurityContext has auth → downstream allows it
// If you forget this line: EVERY request hangs forever.
```

**Principal is userId, not email — why?**:
```java
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                userId.toString(),  // principal = UUID string
                null,
                List.of(authority)
        );
// The JWT stores userId (UUID) in the "sub" claim, not email.
// We extract it without a DB hit — just parse the token.
// UserController.requireSelfOrAdmin() gets this via:
//   authentication.getPrincipal() → "550e8400-..."
// Then calls userService.findByEmail() using the UserDetails username if needed.
```

---

### JwtTokenProvider.java — The Token Factory

**What it is**: Handles everything JWT — generating, parsing, and validating tokens.

**Why a separate class?**
Token generation is a distinct concern. If you put it in AuthServiceImpl,
AuthServiceImpl would do too many things. Single Responsibility Principle:
`JwtTokenProvider` owns JWT, `AuthServiceImpl` owns login logic.

**JJWT library** (io.jsonwebtoken):
The most widely used JWT library for Java. Provides:
- `Jwts.builder()` — create tokens
- `Jwts.parser()` — parse and verify tokens
- `Keys.hmacShaKeyFor()` — create cryptographic keys

**The signing algorithm — HMAC-SHA256 (HS256)**:
```java
Keys.hmacShaKeyFor(keyBytes)
// HMAC = Hash-based Message Authentication Code
// SHA-256 = the hash function used
// HS256 = symmetric — same key signs AND verifies
//
// Alternative: RS256 (asymmetric — private key signs, public key verifies)
// RS256 is better for microservices (each service can verify without knowing the private key)
// HS256 is simpler for monoliths — one secret, one service
```

**Why 5 catch blocks in validateToken()?**:
```java
} catch (ExpiredJwtException e)     { }  // token is past its expiry time
} catch (UnsupportedJwtException e) { }  // algorithm not supported
} catch (MalformedJwtException e)   { }  // token is not valid JWT format
} catch (SecurityException e)       { }  // signature verification failed
} catch (IllegalArgumentException e){ }  // empty or null token
// Each exception means "this token is invalid" but for different reasons.
// All result in the same outcome: return false → 401.
// Logging them separately helps debug which type of invalid token you're getting.
```

---

### UserDetailsServiceImpl.java — The User Loader

**What it is**: Implements Spring Security's `UserDetailsService` interface.
Spring calls `loadUserByUsername(email)` when it needs to verify credentials.

**Why an interface?**
Spring Security's `DaoAuthenticationProvider` calls `loadUserByUsername()` through
the interface. It doesn't know about your `UserDetailsServiceImpl` directly.
This is the Strategy pattern — Spring's auth system is decoupled from your User logic.

**Two different "User" classes**:
```java
// YOUR entity:
com.miniproject.plato.user.User
// Has: id, email, passwordHash, role, status, lastLogin, etc.
// Hibernate entity — maps to users table

// SPRING SECURITY's User:
org.springframework.security.core.userdetails.User
// Has: username, password, authorities, enabled, accountLocked, etc.
// Spring Security's standard user representation
```

They are completely different classes. The method converts your entity → Spring's object:
```java
return org.springframework.security.core.userdetails.User.builder()
        .username(user.getEmail())
        .password(user.getPasswordHash())
        .authorities(List.of(authority))
        .disabled(user.getStatus() == UserStatus.SUSPENDED)
        .accountLocked(user.getStatus() == UserStatus.DELETED)
        .build();
```

**disabled vs accountLocked**:
```java
.disabled(...)      // DisabledException on login → 401
.accountLocked(...) // LockedException on login → 401
// Both result in failed authentication, but Spring distinguishes them internally.
// Useful if you ever want different error messages or handling for each case.
```

**The "username" confusion**:
Spring Security calls the login identifier "username" everywhere — its interface is
`loadUserByUsername(String username)`. In Plato, the username IS the email.
This is a Spring Security naming convention, not a bug.

---

### SecurityProperties.java — Config Binding

**What it is**: A `@ConfigurationProperties` bean that reads `plato.jwt.*` from
`application.yml` and makes it available as a typed Java object.

**Why not `@Value("${plato.jwt.secret}")` directly in JwtTokenProvider?**
```java
// @Value approach (works but has issues):
@Value("${plato.jwt.secret}")
private String secret;

// @ConfigurationProperties approach (better):
@ConfigurationProperties(prefix = "plato.jwt")
public class SecurityProperties {
    private String secret;
    private long expiration = 86400000L;
}
// Benefits:
// 1. All JWT config in one place — easy to see all related properties
// 2. @Validated can run @NotNull, @Min etc. on the fields — fails fast at startup
// 3. Default values (expiration = 86400000L) are defined in one place
// 4. Easier to test — you can inject a mock SecurityProperties
```

---

## Key Concepts

### What is JWT?

**JWT = JSON Web Token**. A compact, self-contained way to transmit information as a signed string.

Structure: `header.payload.signature`

```
header:    { "alg": "HS256" }         — which algorithm was used
payload:   { "sub": "uuid", "role": "OWNER", "iat": 123, "exp": 456 } — the claims
signature: HMAC-SHA256(header + "." + payload, secret) — proves it wasn't tampered
```

**Self-contained** = the server doesn't need to look up a database to validate a JWT.
The signature proves the claims are authentic. The server just needs the secret key.

**Why JWT instead of sessions?**
- Sessions require server-side storage (memory or Redis) — doesn't scale horizontally
- JWT is stateless — any server can validate it, just needs the secret
- JWT works across domains — mobile apps, third-party APIs, microservices

---

### What is the difference between authentication and authorization?

**Authentication** = "who are you?" — verifying identity
- In Plato: login endpoint verifies email + password → issues JWT

**Authorization** = "what are you allowed to do?" — checking permissions
- In Plato: @PreAuthorize checks JWT role claims before each endpoint

They happen in this order: authenticate first, authorize second.
You can't authorize someone whose identity you haven't verified.

---

### What is the SecurityContextHolder?

A thread-local store that holds the current user's `Authentication` object for the duration of one HTTP request.

```
Request starts → JwtAuthenticationFilter sets Authentication in SecurityContextHolder
Request in progress → @PreAuthorize reads from SecurityContextHolder
Request ends → Spring automatically clears SecurityContextHolder
```

Thread-local = each thread (each request in a thread pool) has its own copy.
No cross-request leakage.

---

### What is the filter chain?

A sequence of filters that every HTTP request passes through before reaching your controller.
Each filter can:
- Inspect/modify the request
- Inspect/modify the response
- Stop the chain (by NOT calling filterChain.doFilter())
- Pass to the next filter (by calling filterChain.doFilter())

Spring Security adds several filters by default. You inserted your JWT filter at a specific position.

---

## Interview Questions & Answers

---

**Q: What is JWT and how does it work?**

A: JWT is a JSON Web Token — a compact, URL-safe string with three base64-encoded parts:
header (algorithm), payload (claims like userId, role, expiry), and signature.
The signature is created by HMAC-SHA256(header + payload, secret). To validate,
the server recomputes the signature and compares — if they match, the token wasn't tampered with.
It's stateless: the server needs no DB lookup to verify a JWT, just the secret key.

---

**Q: What is the difference between `Authentication` and `Authorization`?**

A: Authentication = verifying who the user is (login, JWT validation).
Authorization = deciding what they can do (ROLE_SUPER_ADMIN can create users, OWNER can only see their own restaurant).
Authentication always comes first. In Plato: `JwtAuthenticationFilter` does authentication,
`@PreAuthorize` does authorization.

---

**Q: Why is `SessionCreationPolicy.STATELESS` used?**

A: Stateless means Spring never creates an `HttpSession`. Every request must carry its JWT.
This is required for scalability: in a load-balanced system, request 1 might hit Server A and request 2
might hit Server B. If sessions are stored on Server A, request 2 on Server B would fail.
With JWT: both servers just need the same secret key — no shared session store needed.

---

**Q: Why is CSRF disabled for this API?**

A: CSRF attacks exploit the browser automatically attaching cookies to cross-origin requests.
Since Plato uses JWT in the `Authorization` header (not cookies), a malicious cross-site
request can't inject our Authorization header — browsers prevent cross-origin header manipulation.
Therefore CSRF protection is unnecessary and is disabled. If we used cookie-based sessions,
CSRF protection would be mandatory.

---

**Q: What does `OncePerRequestFilter` guarantee?**

A: It guarantees `doFilterInternal()` is called exactly once per HTTP request.
Without this guarantee, certain mechanisms (like RequestDispatcher forwards)
could cause filters to execute multiple times on the same request.

---

**Q: What is `DaoAuthenticationProvider` and what does it do?**

A: It's Spring Security's standard authentication provider that connects:
- `UserDetailsService` (how to load a user by username/email)
- `PasswordEncoder` (how to verify the password)

When `authenticationManager.authenticate(token)` is called:
1. `DaoAuthenticationProvider` calls `UserDetailsService.loadUserByUsername(email)`
2. Gets the stored BCrypt hash from the returned `UserDetails`
3. Calls `BCrypt.matches(rawPassword, storedHash)`
4. If true → returns authenticated token. If false → throws `BadCredentialsException` → 401.

---

**Q: Why does the JWT store userId (UUID) in the subject, not email?**

A: The subject should be an immutable identifier. Email can change (user updates their email).
UUID never changes — it's the permanent primary key. Using UUID prevents token confusion
if a user updates their email after the token was issued.

---

**Q: What happens if someone tampers with the JWT payload to change their role?**

A: The signature becomes invalid. The signature is `HMAC-SHA256(header + payload, secret)`.
If you change the payload, the signature no longer matches. `JwtTokenProvider.parseClaims()`
calls `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` which recomputes
the signature and compares — mismatch → `SecurityException` → `validateToken()` returns false → 401.
Without the secret key, you cannot produce a valid signature for a modified payload.

---

**Q: What is the difference between `@Component`, `@Service`, and why does `JwtTokenProvider` use `@Component`?**

A: Both are Spring stereotypes that register a class as a Spring bean. The difference is semantic:
`@Service` signals business logic. `@Component` is generic utility.
`JwtTokenProvider` is a utility (token operations), not a service (business logic).
Using `@Service` would be semantically misleading. Both work identically — it's about code clarity.

---

**Q: Why store the role in the JWT instead of loading it from the DB on every request?**

A: Performance. Every protected endpoint would need a DB hit just to get the user's role.
By storing the role in the JWT claim, `JwtAuthenticationFilter` can build the `GrantedAuthority`
from the token directly — zero DB calls per request for role checking.
The trade-off: if a user's role changes, old JWTs still have the old role until they expire.
For this project (24h expiry) that's acceptable. For high-security systems, use shorter expiry
or maintain a token blacklist.

---

**Q: What is the `UsernamePasswordAuthenticationToken` and when does it have 2 vs 3 arguments?**

A: It's Spring Security's standard authentication container.

2-argument constructor `(principal, credentials)`:
→ `isAuthenticated()` returns false — used DURING credential verification
→ `new UsernamePasswordAuthenticationToken(email, password)` — passed to `authenticationManager.authenticate()`

3-argument constructor `(principal, credentials, authorities)`:
→ `isAuthenticated()` returns true — used to SET the authenticated state
→ `new UsernamePasswordAuthenticationToken(userId, null, List.of(authority))` — set in SecurityContext after verification

This distinction matters: setting the 3-arg version tells Spring "this user is authenticated".

---

**Q: What is `SimpleGrantedAuthority` and the "ROLE_" prefix?**

A: `GrantedAuthority` is Spring Security's permission representation.
`SimpleGrantedAuthority("ROLE_OWNER")` creates one permission entry.

The `ROLE_` prefix is a Spring Security convention:
- `hasRole('OWNER')` → Spring internally checks for `ROLE_OWNER`
- `hasAuthority('ROLE_OWNER')` → checks for exactly `ROLE_OWNER`
- `hasAuthority('OWNER')` → checks for `OWNER` (without prefix) — would fail

In our filter we build: `"ROLE_" + role` (e.g. "ROLE_" + "OWNER" = "ROLE_OWNER").
In `@PreAuthorize` we write: `hasRole('ROLE_OWNER')` — this works because
`hasRole()` checks for the exact string you pass.


---

## SecurityConfig.java — Concepts & Interview Prep

---

### What SecurityConfig.java is

The single file that configures ALL of Spring Security for the application.
It defines: which paths are public, which need a JWT, how sessions work, how passwords
are verified, where the JWT filter sits in the chain, and what happens on auth failure.

Without this file, Spring Security either blocks everything (default deny) or nothing.

**Key annotations**:

```java
@Configuration
// This class is a configuration class — Spring scans it for @Bean methods.
// Every @Bean method returns an object that Spring registers and manages.

@EnableWebSecurity
// Activates Spring Security's web security support.
// Replaces the old WebSecurityConfigurerAdapter (removed in Spring Boot 3).
// Without this, all the HttpSecurity configuration is ignored.

@EnableMethodSecurity
// Activates @PreAuthorize, @PostAuthorize, @Secured on controller methods.
// Without this, @PreAuthorize is silently ignored — every endpoint is accessible
// to anyone who passes the URL-level rules (a severe security hole).
// Always use this annotation in a REST API with role-based access.

@RequiredArgsConstructor
// Lombok: generates constructor for final fields.
// Spring uses this constructor to inject:
//   - JwtAuthenticationFilter
//   - UserDetailsServiceImpl
//   - PasswordEncoder
```

---

### The 3 @Bean methods explained

**Bean 1: `securityFilterChain`** — the master routing table
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
// HttpSecurity is a builder API.
// Returns a SecurityFilterChain — the configured chain of filters + rules.
// Spring applies this to EVERY HTTP request automatically.
```

**Bean 2: `authenticationProvider`** — how login verification works
```java
@Bean
public DaoAuthenticationProvider authenticationProvider() {
// Wires UserDetailsService + PasswordEncoder into one "verifier".
// AuthenticationManager calls this when authenticating login credentials.
// This is the bridge between "here's an email+password" and "is it valid?".
```

**Bean 3: `authenticationManager`** — the entry point for authentication
```java
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
// Exposes Spring's AuthenticationManager as an injectable bean.
// AuthServiceImpl injects this to call authenticationManager.authenticate().
// Without this @Bean, AuthServiceImpl cannot inject AuthenticationManager.
```

---

### Why the filter position matters — addFilterBefore

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

Spring Security's default filter chain has ~15 built-in filters. `UsernamePasswordAuthenticationFilter`
handles traditional form login (not used here). By placing `JwtAuthenticationFilter` BEFORE it:

1. JWT filter runs → reads token → sets `SecurityContextHolder`
2. `UsernamePasswordAuthenticationFilter` runs → sees no form login credentials → skips
3. Authorization check → SecurityContext already has auth → passes

If you used `addFilterAfter`:
1. `UsernamePasswordAuthenticationFilter` runs first → no form credentials → skips (SecurityContext empty)
2. Authorization check fires with empty SecurityContext → 401 on every request

---

## Interview Questions & Answers — SecurityConfig

---

**Q: What does `@EnableMethodSecurity` do and what happens without it?**

A: `@EnableMethodSecurity` activates method-level security annotations like `@PreAuthorize`.
Without it, `@PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")` on a controller method is
silently ignored — the annotation compiles and runs but does nothing.
Every authenticated user (regardless of role) can call every endpoint.
This is a critical misconfiguration. Always add `@EnableMethodSecurity` when using `@PreAuthorize`.

---

**Q: What is `DaoAuthenticationProvider` and why do you configure it explicitly?**

A: `DaoAuthenticationProvider` is Spring Security's standard credential verifier.
It takes `UserDetailsService` (how to load a user) and `PasswordEncoder` (how to verify password)
and connects them. When `authenticationManager.authenticate(token)` is called during login:
1. Provider calls `UserDetailsService.loadUserByUsername(email)` → gets stored hash
2. Provider calls `BCryptPasswordEncoder.matches(rawPassword, storedHash)` → true/false
3. Returns authenticated token or throws exception

We configure it explicitly to wire our `UserDetailsServiceImpl` and `BCryptPasswordEncoder`
(not Spring's defaults). If not configured, Spring might use an in-memory user store.

---

**Q: What is the difference between `permitAll()` and `authenticated()`?**

A: `permitAll()` = this endpoint is public — no token required, no auth check.
`authenticated()` = this endpoint requires a valid `Authentication` in the `SecurityContextHolder`.
The JWT filter sets authentication if the token is valid. If no token or invalid token,
SecurityContext is empty → `authenticated()` check fails → Spring returns 401.

Note: `authenticated()` does NOT check the role — it only checks "is this person authenticated at all?"
Role checking is done by `@PreAuthorize` on the controller method.

---

**Q: Why is `SessionCreationPolicy.STATELESS` important for a REST API?**

A: Three reasons:
1. **Scalability**: Stateless APIs can be load-balanced across many servers. No session state
   lives on any server — any server can handle any request.
2. **Security**: No JSESSIONID cookies are created. A browser can't accidentally use a stale
   session from a previous login.
3. **Clarity**: Forces clients to always provide credentials (JWT) on every request.
   There's no "I'm already logged in" magic from the server's perspective.

---

**Q: Walk me through what happens when a request comes in with no Authorization header.**

A:
1. `JwtAuthenticationFilter.doFilterInternal()` runs
2. `extractTokenFromRequest()` reads `null` from the Authorization header
3. `StringUtils.hasText(null)` = false → skip the if block → `SecurityContextHolder` stays empty
4. `filterChain.doFilter()` passes the request forward
5. `authorizeHttpRequests` fires: if the URL is in `permitAll()` → proceed. If under `anyRequest().authenticated()` → `isAuthenticated()` = false → Spring's security interceptor rejects it → `401 Unauthorized`
6. The controller is NEVER invoked

---

**Q: Can you have multiple `SecurityFilterChain` beans?**

A: Yes. Spring Security supports multiple filter chains with different `securityMatcher` conditions.
For example, one chain for `/api/v1/**` (JWT-based, stateless) and another for `/admin/**`
(form login, session-based). The first chain whose `securityMatcher` matches the request wins.
In Plato, we have one chain for simplicity.

---

**Q: What is `WebAuthenticationDetailsSource` used for?**

A: It captures request metadata (IP address, session ID) and attaches it to the
`Authentication` object. This metadata is accessible via `authentication.getDetails()`.
Useful for:
- Audit logging: "User X logged in from IP 192.168.1.1"
- Security monitoring: detecting logins from unusual locations
- It doesn't affect authentication/authorization — it's purely informational

---

**Q: What would happen if you put the JWT filter AFTER `UsernamePasswordAuthenticationFilter`?**

A: Every protected request would return 401. Here's why:
`UsernamePasswordAuthenticationFilter` runs first with an empty SecurityContext.
It sees no form-login credentials → skips (no authentication set).
Then the authorization check fires: `anyRequest().authenticated()` → SecurityContext empty → blocked.
Your JWT filter never gets to run because the request was already rejected.
The filter must run BEFORE the authorization checks to have any effect.

