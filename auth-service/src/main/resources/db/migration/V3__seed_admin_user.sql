-- ============================================================
-- V3: Seed default system admin user
-- ============================================================
-- Creates one ADMIN account so staff provisioning can begin
-- immediately on a fresh deployment without manual DB access.
--
-- Credentials (change after first login in any real environment):
--   Email   : admin@securebank.ca
--   Password: Admin@SecureBank1
--
-- The password_hash is a BCrypt(10) hash of the password above.
-- To rotate it: generate a new BCrypt hash and run an UPDATE,
-- or let the admin change their password via the API once built.
-- ============================================================

INSERT INTO users (id, version, created_at, updated_at, email, password_hash, role, active)
VALUES (
    'a0000000-0000-0000-0000-000000000000',
    0,
    NOW(),
    NOW(),
    'admin@securebank.ca',
    '$2a$10$Qf17OHnlfOmGjzh7/SQCouefsEdH6T6l98ohxDDKV8xDp/JvG4Ixq',
    'ADMIN',
    TRUE
)
ON CONFLICT (email) DO NOTHING;
