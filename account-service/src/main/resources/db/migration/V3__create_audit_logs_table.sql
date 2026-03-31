CREATE TABLE audit_logs (
    id                 UUID         NOT NULL PRIMARY KEY,
    entity_type        VARCHAR(50)  NOT NULL,
    entity_id          UUID         NOT NULL,
    action             VARCHAR(20)  NOT NULL,
    performed_by       UUID,
    performed_by_email VARCHAR(255),
    occurred_at        TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT NOW(),
    details            TEXT
);

-- Look up all audit events for a specific account
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);

-- Look up all actions performed by a specific user
CREATE INDEX idx_audit_performed_by ON audit_logs (performed_by);

-- Time-range queries (most recent first)
CREATE INDEX idx_audit_occurred_at ON audit_logs (occurred_at DESC);
