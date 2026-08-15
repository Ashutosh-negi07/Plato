package com.miniproject.plato.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * JWT authentication filter — runs once per request.
 *
 * <p>Reads the Authorization header, validates the JWT, and if valid,
 * sets the authenticated user in Spring Security's SecurityContext.
 * Controllers can then use @PreAuthorize, SecurityContextHolder.getContext(), etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Step 1: Extract token from the Authorization header
        String token = extractTokenFromRequest(request);

        // Step 2: Validate token and set SecurityContext
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

            // Step 3: Get userId from the token (no DB hit yet)
            UUID userId = jwtTokenProvider.getUserIdFromToken(token);

            /**
             * Extracts the role claim from a validated token.
             */



            // Step 4: Load full user details from DB using email
            // But wait — the token has userId, not email. We need to load by ID.
            // We use UserDetailsService which loads by email (username).
            // So we get the email from a different approach — see note below.
            String role = jwtTokenProvider.getRoleFromToken(token);

            // Load UserDetails by userId (we need to load the user to get email)
            // UserDetailsService.loadUserByUsername takes email, but we have UUID.
            // Solution: load user directly from the token claims and build auth manually.

            // We create an Authentication token with the userId as principal.
            // The UserDetails are loaded via username (email from DB lookup by userId).
            // For this filter, we can use the userId string as the "username" key
            // and load the user using a custom method — OR we store email in the JWT.
            //
            // ── DESIGN DECISION ──
            // Store the USER'S EMAIL in the JWT as well, alongside the userId.
            // That way: token → email → UserDetails (via loadUserByUsername).
            // The token already has sub=userId + role. We load UserDetails by email
            // to get the full authority set.
            //
            // But since we only stored userId in subject, we need to load by UUID.
            // The cleanest approach here: load UserDetails using userId as principal.
            // We'll build the Authentication directly without re-querying DB since
            // we already have the role from the token.

            // Build GrantedAuthority from token claim (avoid DB hit)
            org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "ROLE_" + role
                    );

            // Create the Authentication object
            // principal = userId string (our services can call getPrincipal() to get it)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),          // principal — who is authenticated
                            null,                       // credentials — null (we don't need the password here)
                            java.util.List.of(authority) // authorities — from token
                    );

            // Attach request metadata (IP address, session info) — useful for auditing
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // Step 5: Tell Spring Security this user is authenticated
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT auth: userId={}, role={}, path={}",
                    userId, role, request.getRequestURI());
        }

        // Step 6: Always pass the request to the next filter
        // If no token → SecurityContext is empty → SecurityConfig rules handle it
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT string from the Authorization header.
     * Returns null if the header is missing or not in "Bearer <token>" format.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // Check header exists and starts with "Bearer "
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // "Bearer " is 7 characters — everything after that is the token
            return bearerToken.substring(7);
        }
        return null;
    }
}

