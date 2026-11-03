package com.stockflow.identity.service;

import com.stockflow.audit.service.AuditService;
import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("User with email '" + normalizedEmail + "' already exists");
        }

        // Prevent arbitrary ADMIN self-registration
        Role targetRole = request.role();
        if (targetRole == Role.ADMIN) {
            throw new ConflictException("Cannot self-register as an administrator");
        }

        User user = new User(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                targetRole
        );
        user = userRepository.save(user);

        auditService.recordAction(
                user.getId(),
                "USER_REGISTERED",
                "User",
                user.getId().toString(),
                "User registered with role: " + user.getRole(),
                null
        );

        log.info("Registered new user with id: {} and role: {}", user.getId(), user.getRole());
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        auditService.recordAction(
                user.getId(),
                "LOGIN_SUCCESS",
                "User",
                user.getId().toString(),
                "User logged in successfully",
                null
        );

        log.info("Successful login for user: {}", user.getEmail());
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenHash = JwtService.hashToken(request.refreshToken());
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        User user = storedToken.getUser();

        // Token reuse detection: if an already revoked token is used, someone might have stolen it!
        // Revoke all tokens for this user immediately!
        if (storedToken.isRevoked()) {
            log.warn("Detected reuse of revoked refresh token for user {}. Invalidating all user sessions.", user.getEmail());
            refreshTokenRepository.deleteByUserId(user.getId());

            auditService.recordAction(
                    user.getId(),
                    "SECURITY_ALERT_TOKEN_REUSE",
                    "RefreshToken",
                    storedToken.getId().toString(),
                    "Security alert: attempted reuse of revoked refresh token for user: " + user.getEmail(),
                    null
            );

            throw new UnauthorizedException("Security violation: Token reuse detected");
        }

        if (storedToken.isExpired()) {
            storedToken.revoke();
            refreshTokenRepository.save(storedToken);
            throw new UnauthorizedException("Refresh token has expired");
        }

        // Rotate token: revoke current token and issue fresh pair
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        return createAuthResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = JwtService.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.save(token);

                auditService.recordAction(
                        token.getUser().getId(),
                        "LOGOUT",
                        "RefreshToken",
                        token.getId().toString(),
                        "User logged out and refresh token revoked",
                        null
                );

                log.info("Revoked refresh token on logout for user: {}", token.getUser().getEmail());
            });
        }
    }

    private AuthResponse createAuthResponse(User user) {
        Map<String, Object> claims = Map.of(
                "userId", user.getId().toString(),
                "role", user.getRole().name()
        );
        String accessToken = jwtService.generateAccessToken(user, claims);

        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String tokenHash = JwtService.hashToken(rawRefreshToken);
        Instant expiry = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                user,
                tokenHash,
                expiry
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}
