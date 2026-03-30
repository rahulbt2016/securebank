package com.securebank.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Handles JWT access token creation and validation.
 *
 * Why a plain class (not @Component)?
 * This lives in common-lib so it can be shared by auth-service (which creates
 * tokens) and every other service (which only validates them). Each service
 * instantiates it as a @Bean in its own SecurityConfig, injecting the secret
 * and expiry values from that service's application.yml. This keeps configuration
 * explicit and avoids Spring Security auto-configuration bleeding across modules.
 *
 * Token structure (HS256):
 *   sub   → userId (UUID)
 *   email → user's email address
 *   role  → role name e.g. "TELLER"
 *   iat   → issued-at timestamp
 *   exp   → expiry timestamp
 */
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE  = "role";

    private final SecretKey key;
    private final long accessTokenExpiryMs;

    /**
     * @param secret            Plain-text secret (≥ 32 chars recommended for HS256).
     * @param accessTokenExpiryMs  Access token lifetime in milliseconds.
     */
    public JwtService(String secret, long accessTokenExpiryMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    // ── Token generation ─────────────────────────────────────────────────────

    public String generateAccessToken(UUID userId, String email, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpiryMs))
                .signWith(key)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Returns true only if the token is well-formed, correctly signed, and not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get(CLAIM_EMAIL, String.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get(CLAIM_ROLE, String.class);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
