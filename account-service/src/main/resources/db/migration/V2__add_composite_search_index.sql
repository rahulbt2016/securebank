-- ============================================================
-- V2: Add composite index to optimise account search queries
-- ============================================================
-- The searchAccounts JPQL query filters on any combination of:
--   customer_id, status, account_type, balance
--
-- A single composite index on (customer_id, status, account_type)
-- covers the most frequent access patterns:
--   • "all accounts for customer X"                  → prefix (customer_id)
--   • "all ACTIVE accounts for customer X"           → prefix (customer_id, status)
--   • "all ACTIVE CHEQUING accounts for customer X"  → full key
--
-- PostgreSQL can use any left-prefix of a composite index, so this
-- single index replaces the need for separate customer_id and status
-- indexes for query-style access. The existing idx_accounts_status
-- index is still useful for admin queries that filter by status alone
-- (e.g. "list all FROZEN accounts across all customers").
-- ============================================================

CREATE INDEX idx_accounts_customer_status_type
    ON accounts (customer_id, status, account_type);
