package com.securebank.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.auth.dto.AuthResponse;
import com.securebank.auth.dto.LoginRequest;
import com.securebank.auth.dto.RefreshTokenRequest;
import com.securebank.auth.dto.RegisterRequest;
import com.securebank.auth.entity.Role;
import com.securebank.auth.config.SecurityConfig;
import com.securebank.auth.service.AuthService;
import com.securebank.common.exception.GlobalExceptionHandler;
import com.securebank.common.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-long-enough-for-hs256-algorithm-at-least-32-chars!!",
        "jwt.access-token-expiry-ms=900000"
})
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AuthService authService;

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.sample.token")
                .refreshToken(UUID.randomUUID().toString())
                .tokenType("Bearer")
                .expiresIn(900)
                .userId(UUID.randomUUID())
                .email("jane@example.com")
                .role("CUSTOMER")
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - should return 201 with tokens")
    void shouldRegisterUser() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("jane@example.com")
                .password("password123")
                .role(Role.CUSTOMER)
                .build();

        given(authService.register(any(RegisterRequest.class))).willReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - should return 400 for invalid request")
    void shouldReturn400ForInvalidRegister() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("not-an-email")
                .password("short")
                .role(null)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - should return 200 with tokens")
    void shouldLoginUser() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("jane@example.com")
                .password("password123")
                .build();

        given(authService.login(any(LoginRequest.class))).willReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.email").value("jane@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - should return 401 for bad credentials")
    void shouldReturn401ForBadCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("jane@example.com")
                .password("wrongpassword")
                .build();

        given(authService.login(any(LoginRequest.class)))
                .willThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - should return 200 with new tokens")
    void shouldRefreshTokens() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(UUID.randomUUID().toString())
                .build();

        given(authService.refresh(any(RefreshTokenRequest.class))).willReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - should return 204")
    void shouldLogout() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(UUID.randomUUID().toString())
                .build();

        willDoNothing().given(authService).logout(any(RefreshTokenRequest.class));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }
}
