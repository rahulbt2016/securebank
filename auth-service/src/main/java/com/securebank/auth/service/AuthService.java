package com.securebank.auth.service;

import com.securebank.auth.dto.AuthResponse;
import com.securebank.auth.dto.LoginRequest;
import com.securebank.auth.dto.RefreshTokenRequest;
import com.securebank.auth.dto.RegisterRequest;
import com.securebank.auth.entity.RefreshToken;
import com.securebank.auth.entity.User;
import com.securebank.auth.repository.RefreshTokenRepository;
import com.securebank.auth.repository.UserRepository;
import com.securebank.common.exception.DuplicateResourceException;
import com.securebank.common.exception.UnauthorizedException;
import com.securebank.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-days}")
    private int refreshTokenExpiryDays;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());

        User saved = userRepository.save(user);
        log.info("User registered: {} with role {}", saved.getEmail(), saved.getRole());

        return buildAuthResponse(saved);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Validates the refresh token, revokes it, and issues a new access + refresh pair.
     * This is called token rotation — each refresh yields a new refresh token so the
     * old one can never be reused, limiting the window for stolen token abuse.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Revoke the current token (rotation — old token can never be reused)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByToken(request.getRefreshToken())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("User logged out, refresh token revoked");
                });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
