package com.securebank.common.security;

import java.util.UUID;

/**
 * Represents the authenticated user extracted from a validated JWT.
 * Stored as the principal in UsernamePasswordAuthenticationToken so controllers
 * and services can access identity without re-parsing the token.
 */
public record AuthenticatedUser(UUID userId, String email, String role) {

    public boolean isStaff() {
        return "TELLER".equals(role) || "MANAGER".equals(role) || "ADMIN".equals(role);
    }
}
