package com.securebank.auth.entity;

/**
 * Roles used across all SecureBank services.
 *
 * CUSTOMER — end user; can view and manage their own accounts
 * TELLER   — bank staff; can create accounts and view all accounts
 * MANAGER  — senior staff; everything a TELLER can do, plus close accounts
 * ADMIN    — full access including user management
 */
public enum Role {
    CUSTOMER,
    TELLER,
    MANAGER,
    ADMIN
}
