package com.securebank.common.exception;

/**
 * Thrown when a business rule is violated (e.g., insufficient funds, account frozen).
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
