package com.securebank.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    /** Always "Bearer" — tells clients how to send the access token */
    private String tokenType;

    /** Access token lifetime in seconds */
    private long expiresIn;

    private UUID userId;
    private String email;
    private String role;
}
