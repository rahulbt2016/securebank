package com.securebank.auth.entity;

import com.securebank.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted refresh token.
 *
 * Why store refresh tokens in the DB?
 * Unlike access tokens (stateless JWTs validated by signature alone), refresh
 * tokens need to be revocable. Persisting them lets us mark a token as revoked
 * on logout, and rotate them on each use (old token revoked, new token issued).
 * This limits the blast radius if a refresh token is ever stolen.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    /** Random UUID string — opaque to the client */
    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
