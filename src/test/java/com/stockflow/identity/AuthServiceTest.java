package com.stockflow.identity;

import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.identity.domain.RefreshToken;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.identity.dto.AuthResponse;
import com.stockflow.identity.dto.LoginRequest;
import com.stockflow.identity.dto.RefreshTokenRequest;
import com.stockflow.identity.dto.RegisterRequest;
import com.stockflow.identity.repository.RefreshTokenRepository;
import com.stockflow.identity.repository.UserRepository;
import com.stockflow.identity.service.AuthService;
import com.stockflow.identity.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Should successfully register a new customer")
    void shouldRegisterNewCustomer() {
        RegisterRequest request = new RegisterRequest("john@example.com", "Password123!", "John Doe", Role.CUSTOMER);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class), any())).thenReturn("mock-access-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.accessToken()).isEqualTo("mock-access-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should reject self-registration as ADMIN")
    void shouldRejectAdminSelfRegistration() {
        RegisterRequest request = new RegisterRequest("admin@example.com", "Password123!", "Admin User", Role.ADMIN);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot self-register as an administrator");
    }

    @Test
    @DisplayName("Should reject login with invalid credentials")
    void shouldRejectInvalidCredentials() {
        LoginRequest request = new LoginRequest("john@example.com", "WrongPassword");
        User user = new User(UUID.randomUUID(), "john@example.com", "hashed-pwd", "John Doe", Role.CUSTOMER);

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-pwd")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should detect token reuse and revoke all user tokens")
    void shouldDetectTokenReuseAndRevokeAllSessions() {
        String rawToken = "stolen-refresh-token";
        String tokenHash = JwtService.hashToken(rawToken);
        User user = new User(UUID.randomUUID(), "victim@example.com", "hashed", "Victim", Role.CUSTOMER);
        RefreshToken revokedToken = new RefreshToken(UUID.randomUUID(), user, tokenHash, Instant.now().plusSeconds(3600));
        revokedToken.revoke();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(rawToken)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Token reuse detected");

        verify(refreshTokenRepository).deleteByUserId(user.getId());
    }
}
