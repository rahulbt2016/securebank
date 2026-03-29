-- ============================================================
-- V1: Create accounts table
-- ============================================================
-- BaseEntity fields: id, version, created_at, updated_at
-- Account fields:    account_number, customer_id, account_holder_name,
--                    account_type, status, balance, currency, branch_code
-- ============================================================

CREATE TABLE accounts (
    id                  UUID            NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE,

    account_number      VARCHAR(20)     NOT NULL,
    customer_id         UUID            NOT NULL,
    account_holder_name VARCHAR(100)    NOT NULL,
    account_type        VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    balance             DECIMAL(19, 2)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    branch_code         VARCHAR(50),

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number)
);

-- Index for frequent lookup: all accounts belonging to a customer
CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);

-- Index for filtering by status (e.g. get all FROZEN accounts)
CREATE INDEX idx_accounts_status ON accounts (status);

-- Index for filtering by account type
CREATE INDEX idx_accounts_account_type ON accounts (account_type);
