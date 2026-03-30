-- ============================================================
-- V1: Create users table
-- ============================================================
-- BaseEntity fields: id, version, created_at, updated_at
-- User fields: email, password_hash, role, active
-- ============================================================

CREATE TABLE users (
    id            UUID          NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE,
    updated_at    TIMESTAMP WITH TIME ZONE,

    email         VARCHAR(255)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    role          VARCHAR(20)   NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Index for login lookup by email
CREATE INDEX idx_users_email ON users (email);
