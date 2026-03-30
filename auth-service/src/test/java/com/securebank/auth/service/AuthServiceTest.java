package com.securebank.auth.service;

import com.securebank.auth.dto.AuthResponse;
import com.securebank.auth.dto.CreateUserRequest;
import com.securebank.auth.dto.LoginRequest;
import com.securebank.auth.dto.RefreshTokenRequest;
import com.securebank.auth.dto.RegisterRequest;
import com.securebank.auth.entity.RefreshToken;
import com.securebank.auth.entity.Role;
import com.securebank.auth.entity.User;
import com.securebank.auth.repository.RefreshTokenRepository;
import com.securebank.auth.repository.UserRepository;
import com.securebank.common.exception.DuplicateResourceException;
import com.securebank.common.exception.UnauthorizedException;
import com.securebank.common.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("jane@example.com");
        sampleUser.setPasswordHash("$2a$10$hashed");
        sampleUser.setRole(Role.CUSTOMER);
        sampleUser.setActive(true);

        // Inject @Value fields that Mockito can't inject
        ReflectionTestUtils.setField(authService, "accessTokenExpiryMs", 900000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", 7);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register new user as CUSTOMER regardless of any role input")
        void shouldRegisterNewUser() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("jane@example.com")
                    .password("password123")
                    .build();

            given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
            given(passwordEncoder.encode(request.getPassword())).willReturn("$2a$10$hashed");
            given(userRepository.save(any(User.class))).willReturn(sampleUser);
            given(jwtService.generateAccessToken(any(), anyString(), anyString())).willReturn("access-token");
            given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

            AuthResponse result = authService.register(request);

            assertThat(result.getEmail()).isEqualTo("jane@example.com");
            assertThat(result.getAccessToken()).isEqualTo("access-token");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getRole()).isEqualTo("CUSTOMER");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already exists")
        void shouldThrowOnDuplicateEmail() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("jane@example.com")
                    .password("password123")
                    .build();

            given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user with the specified role")
        void shouldCreateUserWithRole() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("teller@securebank.ca")
                    .password("password123")
                    .role(Role.TELLER)
                    .build();

            User tellerUser = new User();
            tellerUser.setId(UUID.randomUUID());
            tellerUser.setEmail("teller@securebank.ca");
            tellerUser.setPasswordHash("$2a$10$hashed");
            tellerUser.setRole(Role.TELLER);
            tellerUser.setActive(true);

            given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
            given(passwordEncoder.encode(request.getPassword())).willReturn("$2a$10$hashed");
            given(userRepository.save(any(User.class))).willReturn(tellerUser);
            given(jwtService.generateAccessToken(any(), anyString(), anyString())).willReturn("access-token");
            given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

            AuthResponse result = authService.createUser(request);

            assertThat(result.getRole()).isEqualTo("TELLER");
            assertThat(result.getEmail()).isEqualTo("teller@securebank.ca");
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already exists")
        void shouldThrowOnDuplicateEmail() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("jane@example.com")
                    .password("password123")
                    .role(Role.ADMIN)
                    .build();

            given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

            assertThatThrownBy(() -> authService.createUser(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return tokens on valid credentials")
        void shouldLoginWithValidCredentials() {
            LoginRequest request = LoginRequest.builder()
                    .email("jane@example.com")
                    .password("password123")
                    .build();

            given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(sampleUser));
            given(passwordEncoder.matches(request.getPassword(), sampleUser.getPasswordHash())).willReturn(true);
            given(jwtService.generateAccessToken(any(), anyString(), anyString())).willReturn("access-token");
            given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

            AuthResponse result = authService.login(request);

            assertThat(result.getAccessToken()).isEqualTo("access-token");
            assertThat(result.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should throw UnauthorizedException for unknown email")
        void shouldThrowForUnknownEmail() {
            LoginRequest request = LoginRequest.builder()
                    .email("unknown@example.com")
                    .password("password123")
                    .build();

            given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw UnauthorizedException for wrong password")
        void shouldThrowForWrongPassword() {
            LoginRequest request = LoginRequest.builder()
                    .email("jane@example.com")
                    .password("wrongpassword")
                    .build();

            given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(sampleUser));
            given(passwordEncoder.matches(request.getPassword(), sampleUser.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw UnauthorizedException for inactive account")
        void shouldThrowForInactiveAccount() {
            sampleUser.setActive(false);
            LoginRequest request = LoginRequest.builder()
                    .email("jane@example.com")
                    .password("password123")
                    .build();

            given(userRepository.findByEmail(request.getEmail())).willReturn(Optional.of(sampleUser));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("disabled");
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("should issue new tokens and revoke the old refresh token")
        void shouldRotateRefreshToken() {
            RefreshToken storedToken = new RefreshToken();
            storedToken.setToken("old-refresh-token");
            storedToken.setUserId(userId);
            storedToken.setExpiresAt(Instant.now().plusSeconds(3600));
            storedToken.setRevoked(false);

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("old-refresh-token")
                    .build();

            given(refreshTokenRepository.findByToken("old-refresh-token")).willReturn(Optional.of(storedToken));
            given(userRepository.findById(userId)).willReturn(Optional.of(sampleUser));
            given(jwtService.generateAccessToken(any(), anyString(), anyString())).willReturn("new-access-token");
            given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

            AuthResponse result = authService.refresh(request);

            assertThat(result.getAccessToken()).isEqualTo("new-access-token");
            assertThat(storedToken.isRevoked()).isTrue(); // old token was revoked
        }

        @Test
        @DisplayName("should throw UnauthorizedException for revoked refresh token")
        void shouldThrowForRevokedToken() {
            RefreshToken revokedToken = new RefreshToken();
            revokedToken.setToken("revoked-token");
            revokedToken.setRevoked(true);
            revokedToken.setExpiresAt(Instant.now().plusSeconds(3600));

            given(refreshTokenRepository.findByToken("revoked-token")).willReturn(Optional.of(revokedToken));

            assertThatThrownBy(() -> authService.refresh(
                    RefreshTokenRequest.builder().refreshToken("revoked-token").build()))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("revoked");
        }

        @Test
        @DisplayName("should throw UnauthorizedException for expired refresh token")
        void shouldThrowForExpiredToken() {
            RefreshToken expiredToken = new RefreshToken();
            expiredToken.setToken("expired-token");
            expiredToken.setRevoked(false);
            expiredToken.setExpiresAt(Instant.now().minusSeconds(3600)); // already expired

            given(refreshTokenRepository.findByToken("expired-token")).willReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.refresh(
                    RefreshTokenRequest.builder().refreshToken("expired-token").build()))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should revoke the refresh token")
        void shouldRevokeRefreshToken() {
            RefreshToken storedToken = new RefreshToken();
            storedToken.setToken("valid-refresh-token");
            storedToken.setRevoked(false);

            given(refreshTokenRepository.findByToken("valid-refresh-token"))
                    .willReturn(Optional.of(storedToken));
            given(refreshTokenRepository.save(storedToken)).willReturn(storedToken);

            authService.logout(RefreshTokenRequest.builder().refreshToken("valid-refresh-token").build());

            assertThat(storedToken.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(storedToken);
        }
    }
}
