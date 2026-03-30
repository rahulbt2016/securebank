package com.securebank.common.exception;

/**
 * Thrown when a request cannot be authenticated — e.g. wrong password,
 * expired refresh token, or a revoked token.
 * Maps to HTTP 401 Unauthorized via GlobalExceptionHandler.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
