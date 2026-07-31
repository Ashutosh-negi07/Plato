# Plato — Unified Spring Security & JWT Master Skeleton

> **What this file contains**: It covers **the ENTIRE Security System** — both:
> 1. **Login Flow**: Receiving credentials, checking database password, and generating a signed JWT token.
> 2. **Authenticated Request Flow**: Intercepting client requests, extracting the JWT token, validating signature/expiration, and populating `SecurityContextHolder` so Spring permits API access.
>
> All components are merged into one single Java master skeleton in chronological order, with detailed comments explaining what each section does.

---

```java
package com.miniproject.plato.security.docs;

import com.miniproject.plato.security.SecurityProperties;
import com.miniproject.plato.user.User;
import com.miniproject.plato.user.UserRepository;
import com.miniproject.plato.user.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * MASTER SKELETON: Entire Plato Security System in a Single Java Class.
 * 
 * Execution Order:
 * - SECTION 1: Application Startup (Spring configures filter chains & security rules)
 * - SECTION 2: Authenticated Request Interception (JWT Filter intercepts headers for protected endpoints)
 * - SECTION 3: Login Endpoint (POST /api/v1/auth/login receives raw user credentials)
 * - SECTION 4: Authentication Manager & Service (Delegates credential check to Spring Security)
 * - SECTION 5: Database User Lookup (Queries PostgreSQL user entity & maps roles)
 * - SECTION 6: JWT Engine (Generates signed JWT on login; parses/validates JWT on requests)
 */
public class CompletePlatoSecurityMasterFlow {

    // =========================================================================
    // SECTION 1: APPLICATION STARTUP & SECURITY CONFIGURATION
    // -------------------------------------------------------------------------
    // WHAT THIS PART DOES:
    // 1. Reads 'plato.jwt.secret' and 'plato.jwt.expiration' from application.yml into SecurityProperties.
    // 2. Builds Spring Security's main filter chain (SecurityFilterChain).
    // 3. Disables CSRF (not needed for stateless REST) and sets Session Policy to STATELESS.
    // 4. Defines public endpoints (/api/v1/auth/**, /api/v1/qr/**, Swagger) vs protected endpoints.
    // 5. Plugs JwtAuthenticationFilter BEFORE UsernamePasswordAuthenticationFilter.
    // 6. Exposes AuthenticationManager and DaoAuthenticationProvider Beans for the Login Flow.
    // =========================================================================

    /**
     * Holds JWT configuration values loaded from application.yml.
     * Location: backend/src/main/java/com/miniproject/plato/security/SecurityProperties.java
     */
    @Getter @Setter
    @Component
    @ConfigurationProperties(prefix = "plato.jwt")
    public static class SecurityProperties {
        private String secret = "super-secret-key-must-be-at-least-32-bytes-long!";
        private long expiration = 86400000L; // 24 hours in milliseconds
    }

    /**
     * Master Security Configuration rules.
     * Location: backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java
     */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    public static class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final UserDetailsServiceImpl userDetailsService;
        private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        public SecurityConfig(JwtAuthenticationFilter filter, UserDetailsServiceImpl service) {
            this.jwtAuthenticationFilter = filter;
            this.userDetailsService = service;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/auth/**").permitAll()     // Public Login Endpoint
                    .requestMatchers("/api/v1/qr/**").permitAll()       // Public QR Scan
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()                       // Protected Endpoints
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            return http.build();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
            provider.setUserDetailsService(userDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
            return config.getAuthenticationManager();
        }
    }


    // =========================================================================
    // SECTION 2: AUTHENTICATED REQUEST FLOW (JWT FILTER INTERCEPTION)
    // -------------------------------------------------------------------------
    // WHAT THIS PART DOES:
    // 1. Runs ON EVERY incoming HTTP request before reaching any controller.
    // 2. Extracts "Authorization: Bearer <token>" from HTTP headers.
    // 3. Validates token signature & expiration via JwtTokenProvider (ZERO DB queries).
    // 4. Extracts 'userId' and 'role' directly from JWT claims payload.
    // 5. Constructs a SimpleGrantedAuthority ("ROLE_OWNER") and Authentication token.
    // 6. Populates SecurityContextHolder with the authenticated user ID.
    // 7. Passes request to the next filter/controller via filterChain.doFilter().
    // =========================================================================

    @Component
    public static class JwtAuthenticationFilter {

        private final JwtTokenProvider jwtTokenProvider;

        public JwtAuthenticationFilter(JwtTokenProvider provider) {
            this.jwtTokenProvider = provider;
        }

        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            // Step 2.1: Read Authorization header
            String token = extractTokenFromRequest(request);

            // Step 2.2: Validate token & set SecurityContext (No DB call needed)
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                
                UUID userId = jwtTokenProvider.getUserIdFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId.toString(), // principal (user ID as string)
                        null,              // credentials (null after authentication)
                        List.of(authority) // Granted authorities
                );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Tell Spring Security this thread is authenticated
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            // Step 2.3: Pass request along the filter chain
            filterChain.doFilter(request, response);
        }

        private String extractTokenFromRequest(HttpServletRequest request) {
            String bearerToken = request.getHeader("Authorization");
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7); // Remove "Bearer " prefix
            }
            return null;
        }
    }


    // =========================================================================
    // SECTION 3: LOGIN FLOW — CONTROLLER LAYER
    // -------------------------------------------------------------------------
    // WHAT THIS PART DOES:
    // 1. Exposes POST /api/v1/auth/login endpoint.
    // 2. Bypasses JWT check because /api/v1/auth/** is configured as permitAll().
    // 3. Receives raw LoginRequest DTO { email, password }.
    // 4. Delegates authentication and token creation to AuthServiceImpl.
    // =========================================================================

    @RestController
    public static class AuthController {

        private final AuthServiceImpl authService;

        public AuthController(AuthServiceImpl authService) {
            this.authService = authService;
        }

        @PostMapping("/api/v1/auth/login")
        public LoginResponse login(@RequestBody LoginRequest request) {
            return authService.login(request);
        }
    }

    public record LoginRequest(String email, String password) {}
    public record LoginResponse(String accessToken, String tokenType) {}


    // =========================================================================
    // SECTION 4: LOGIN FLOW — AUTH SERVICE & AUTHENTICATION MANAGER
    // -------------------------------------------------------------------------
    // WHAT THIS PART DOES:
    // 1. Passes incoming email & raw password to Spring's AuthenticationManager.
    // 2. AuthenticationManager triggers DaoAuthenticationProvider:
    //    -> Calls UserDetailsServiceImpl.loadUserByUsername(email) to fetch DB hash.
    //    -> Compares raw password with DB hash using BCryptPasswordEncoder.matches().
    // 3. If password matches, retrieves User Entity from PostgreSQL.
    // 4. Calls JwtTokenProvider.generateToken(user.getId(), user.getRole().name()).
    // 5. Returns new JWT token back to AuthController.
    // =========================================================================

    @Service
    public static class AuthServiceImpl {

        private final AuthenticationManager authenticationManager;
        private final UserRepository userRepository;
        private final JwtTokenProvider jwtTokenProvider;

        public AuthServiceImpl(
                AuthenticationManager authenticationManager,
                UserRepository userRepository,
                JwtTokenProvider jwtTokenProvider
        ) {
            this.authenticationManager = authenticationManager;
            this.userRepository = userRepository;
            this.jwtTokenProvider = jwtTokenProvider;
        }

        public LoginResponse login(LoginRequest request) {

            // Step 4.1: Verify credentials via Spring Security AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            // Step 4.2: Load user entity from DB to obtain UUID and Role
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Step 4.3: Generate signed JWT token
            String token = jwtTokenProvider.generateToken(user.getId(), user.getRole().name());

            return new LoginResponse(token, "Bearer");
        }
    }


    // =========================================================================
    // SECTION 5: LOGIN FLOW — DATABASE USER LOOKUP (UserDetailsService)
    // -------------------------------------------------------------------------
    // WHAT THIS PART DOES:
    // 1. Called BY Spring Security's DaoAuthenticationProvider during login verification.
    // 2. Queries PostgreSQL 'users' table by email address via UserRepository.
    // 3. Maps UserRole enum (OWNER) to GrantedAuthority ("ROLE_OWNER").
    // 4. Wraps user data into Spring Security's UserDetails object (including password hash,
    //    account disabled/locked flags based on UserStatus SUSPENDED/DELETED).
    // =========================================================================

    @Service
    public static class UserDetailsServiceImpl implements UserDetailsService {

        private final UserRepository userRepository;

        public UserDetailsServiceImpl(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
            
            // Step 5.1: Database Query
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

            // Step 5.2: Role Authority Mapping
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

            // Step 5.3: Return Spring Security UserDetails Object
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPasswordHash())
                    .authorities(List.of(authority))
                    .disabled(user.getStatus() == UserStatus.SUSPENDED)
                    .accountLocked(user.getStatus() == UserStatus.DELETED)
                    .build();
        }
    }


    // =========================================================================
    // SECTION 6: JWT CRYPTOGRAPHIC ENGINE (GENERATION, PARSING & VALIDATION)
    // -------------------------------------------------------------------------
    // WHAT THIS PART DOES:
    // 1. Converts raw secret string from SecurityProperties into HMAC-SHA256 SecretKey.
    // 2. generateToken(): Constructs JWT payload (sub=userId, role=OWNER, iat, exp) and signs it.
    // 3. validateToken(): Verifies incoming JWT signature and checks if token is expired.
    // 4. getUserIdFromToken() / getRoleFromToken(): Parses claims from validated JWT payload.
    // =========================================================================

    @Component
    public static class JwtTokenProvider {

        private final SecurityProperties securityProperties;

        public JwtTokenProvider(SecurityProperties securityProperties) {
            this.securityProperties = securityProperties;
        }

        /**
         * Derives HMAC-SHA256 SecretKey from configured secret string.
         */
        private SecretKey getSigningKey() {
            byte[] keyBytes = securityProperties.getSecret().getBytes();
            return Keys.hmacShaKeyFor(keyBytes);
        }

        /**
         * Token Generation (Used during Login Flow)
         */
        public String generateToken(UUID userId, String role) {
            Date now = new Date();
            Date expiry = new Date(now.getTime() + securityProperties.getExpiration());

            return Jwts.builder()
                    .subject(userId.toString())
                    .claim("role", role)
                    .issuedAt(now)
                    .expiration(expiry)
                    .signWith(getSigningKey())
                    .compact();
        }

        /**
         * Token Validation (Used during Authenticated Request Flow)
         */
        public boolean validateToken(String token) {
            try {
                parseClaims(token);
                return true;
            } catch (Exception e) {
                // ExpiredJwtException, SignatureException, MalformedJwtException etc.
                return false;
            }
        }

        /**
         * Extract Subject (userId UUID) from Claims
         */
        public UUID getUserIdFromToken(String token) {
            return UUID.fromString(parseClaims(token).getSubject());
        }

        /**
         * Extract Role Claim from Claims
         */
        public String getRoleFromToken(String token) {
            return parseClaims(token).get("role", String.class);
        }

        /**
         * Parse and verify signature across Header + Payload
         */
        private Claims parseClaims(String token) {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
    }
}
```

---

## Execution Sequence Matrix

| Flow Type | Step | Action | Executing Method | Source File Location |
|-----------|------|--------|------------------|----------------------|
| **Startup** | 1 | App Startup & Security Configuration | `@Bean securityFilterChain(HttpSecurity)` | [`SecurityConfig.java:40`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/config/SecurityConfig.java#L40) |
| **Request Flow** | 2 | Intercept Protected HTTP Request | `doFilterInternal(request, response, chain)` | [`JwtAuthenticationFilter.java:37`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java#L37) |
| **Request Flow** | 3 | Extract `Bearer <token>` Header | `extractTokenFromRequest(request)` | [`JwtAuthenticationFilter.java:120`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java#L120) |
| **Request Flow** | 4 | Validate JWT Signature & Expiration | `validateToken(token)` / `parseClaims()` | [`JwtTokenProvider.java:48`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L48) |
| **Request Flow** | 5 | Extract Claims & Set SecurityContext | `SecurityContextHolder.getContext().setAuthentication(...)` | [`JwtAuthenticationFilter.java:105`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtAuthenticationFilter.java#L105) |
| **Login Flow** | 6 | Client Sends Login Request `POST /login` | `login(loginRequest)` | `AuthServiceImpl.java` (Task 3) |
| **Login Flow** | 7 | Delegate Credential Check to Spring | `authenticationManager.authenticate(...)` | Called in `AuthServiceImpl.java` |
| **Login Flow** | 8 | Load DB User Entity & Compare Password | `loadUserByUsername(email)` | [`UserDetailsServiceImpl.java:42`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/UserDetailsServiceImpl.java#L42) |
| **Login Flow** | 9 | Create HMAC Key & Sign JWT | `generateToken(userId, role)` | [`JwtTokenProvider.java:25`](file:///Users/blue/Downloads/Plato/backend/src/main/java/com/miniproject/plato/security/JwtTokenProvider.java#L25) |

