package com.miniproject.plato.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {
    private final SecurityProperties securityProperties;
    private SecretKey getSigningKey() {
        // Keys.hmacShaKeyFor requires the raw bytes of the secret.
        // We treat the secret as a plain UTF-8 string and get its bytes.
        // For production, use a proper Base64-encoded 256-bit key.
        byte[] keyBytes = securityProperties.getSecret().getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
    public String  generateToken(UUID userId, String role){
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + securityProperties.getExpiration());

        return Jwts.builder().subject(userId.toString()).claim("role",role).issuedAt(now).expiration(expiry).signWith(getSigningKey()).compact();
    }
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())   // set the key to verify signature
                .build()
                .parseSignedClaims(token)      // parse + verify in one step
                .getPayload();                 // return just the claims
    }
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }
    /**
     * Extracts the role claim from a validated token.
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);   // throws if invalid
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }


}
