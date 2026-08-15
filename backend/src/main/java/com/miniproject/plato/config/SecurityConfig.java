package com.miniproject.plato.config;

import com.miniproject.plato.security.JwtAuthenticationFilter;
import com.miniproject.plato.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Master Spring Security configuration.
 *
 * <p>Defines the security filter chain: which paths are public, which require JWT,
 * session policy, CORS, CSRF, and where to inject the JwtAuthenticationFilter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;   // already defined in AppConfig

    /**
     * The main security filter chain.
     * This is the central security rule — every HTTP request goes through this.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── CSRF ─────────────────────────────────────────────────────────
                // Disable CSRF — not needed for stateless REST APIs.
                // CSRF attacks exploit browser cookie-based sessions. We use JWT in headers —
                // a cross-site request cannot inject our custom header.
                .csrf(AbstractHttpConfigurer::disable)

                // ── CORS ─────────────────────────────────────────────────────────
                // Enable CORS with defaults for now.
                // TODO Day 19: restrict to production frontend domain only.
                .cors(AbstractHttpConfigurer::disable)

                // ── Session Management ───────────────────────────────────────────
                // STATELESS = Spring never creates an HttpSession.
                // Every request must carry its own token — no server-side session memory.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ── Path Authorization Rules ─────────────────────────────────────
                // Order matters: more specific rules first.
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token needed
                        .requestMatchers("/api/v1/auth/**").permitAll()     // login
                        .requestMatchers("/api/v1/qr/**").permitAll()       // customer QR scan
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()                                       // Swagger (dev only)
                        .requestMatchers("/actuator/health").permitAll()    // health check

                        // Customer endpoints — protected by CustomerSessionFilter (Day 11), not JWT
                        // We still permit them here and let the CustomerSessionFilter handle auth
                        .requestMatchers("/api/v1/customer/**").permitAll()

                        // Everything else requires a valid JWT
                        .anyRequest().authenticated()
                )

                // ── Authentication Provider ──────────────────────────────────────
                // Wires our UserDetailsServiceImpl + BCrypt PasswordEncoder into
                // Spring's authentication system. AuthService will use this via
                // AuthenticationManager to verify passwords on login.
                .authenticationProvider(authenticationProvider())

                // ── JWT Filter ───────────────────────────────────────────────────
                // Add our JWT filter BEFORE Spring's default username/password filter.
                // This ensures every request is checked for a JWT before any other processing.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * The DaoAuthenticationProvider wires together:
     * - UserDetailsService (how to load a user)
     * - PasswordEncoder (how to verify a password)
     *
     * AuthServiceImpl will use the AuthenticationManager (below) which internally
     * uses this provider to validate login credentials.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Exposes Spring's AuthenticationManager as a bean.
     *
     * AuthServiceImpl will inject this to perform the actual credential check during login:
     *   authenticationManager.authenticate(
     *       new UsernamePasswordAuthenticationToken(email, password)
     *   )
     *
     * Spring internally calls UserDetailsServiceImpl.loadUserByUsername() and
     * BCrypt.matches() to verify the password.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}

