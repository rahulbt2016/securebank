-- ============================================================
-- V2: Create refresh_tokens table
-- ============================================================
-- Refresh tokens are persisted so they can be revoked (logout,
-- password change) and rotated (new token on each /refresh call).
-- ============================================================

CREATE TABLE refresh_tokens (
    id         UUID      NOT NULL,
    version    BIGINT    NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,

    token      VARCHAR(255) NOT NULL,
    user_id    UUID         NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Fast lookup when validating an incoming refresh token
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens (token);

-- Fast bulk-revoke all tokens for a user (on logout / password change)
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
