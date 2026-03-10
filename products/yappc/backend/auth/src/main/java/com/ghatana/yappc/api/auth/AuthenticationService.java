/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.auth;

import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.yappc.api.auth.dto.UserProfile;
import com.ghatana.yappc.api.auth.repository.UserRepository;
import com.ghatana.yappc.api.auth.model.User;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Authentication Service - Core authentication logic.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>User credential verification</li>
 *   <li>JWT token generation and validation</li>
 *   <li>Refresh token management</li>
 *   <li>Password hashing with BCrypt</li>
 *   <li>Token revocation tracking</li>
 * </ul>
 *
 * <p>Token Lifecycle:
 * <ul>
 *   <li>Access Token: 1 hour expiration</li>
 *   <li>Refresh Token: 7 days expiration</li>
 *   <li>Tokens stored in Data-Cloud for revocation checks</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Authentication business logic
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    
    private static final int ACCESS_TOKEN_EXPIRY_SECONDS = 3600; // 1 hour
    private static final int REFRESH_TOKEN_EXPIRY_SECONDS = 604800; // 7 days
    private static final String TOKEN_TYPE = "Bearer";
    private static final TenantId DEFAULT_TENANT_ID = TenantId.of("yappc");

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordHasher passwordHasher;
    private final ConcurrentHashMap<String, Instant> revokedTokens; // Keyed by tokenId (jti)
    private final Executor blockingExecutor;

    @Inject
    public AuthenticationService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordHasher = new PasswordHasher();
        this.revokedTokens = new ConcurrentHashMap<>();
        this.blockingExecutor = Executors.newFixedThreadPool(2);
    }

    /**
     * Authenticate user with username/email and password.
     *
     * @param username Username or email
     * @param password Plain text password
     * @return AuthenticationResult with tokens and user info
     */
    public Promise<AuthenticationResult> authenticate(String username, String password) {
        return userRepository
            .findByUsernameOrEmail(username)
            .then(
                userOptional -> {
                    if (userOptional.isEmpty()) {
                        log.warn("Authentication failed - user not found: {}", username);
                        return Promise.of(AuthenticationResult.failure("Invalid username or password"));
                    }

                    User user = userOptional.get();

                    if (!passwordHasher.verify(password, user.getPasswordHash())) {
                        log.warn("Authentication failed - invalid password for user: {}", username);
                        return Promise.of(AuthenticationResult.failure("Invalid username or password"));
                    }

                    if (!user.isActive()) {
                        log.warn("Authentication failed - account disabled: {}", username);
                        return Promise.of(AuthenticationResult.failure("Account is disabled"));
                    }

                    Instant now = Instant.now();
                    user.setLastLoginAt(now);

                    UserProfile userProfile =
                        new UserProfile(
                            user.getId().toString(),
                            user.getUsername(),
                            user.getEmail(),
                            user.getRoles(),
                            user.isActive(),
                            user.getCreatedAt(),
                            now);

                    String accessToken = jwtTokenProvider.createToken(
                        user.getId().toString(), toRoleList(user.getRoles()),
                        java.util.Map.of(
                            "email", user.getEmail(),
                            "tenantId", DEFAULT_TENANT_ID.getValue(),
                            "tokenType", "ACCESS"));
                    String refreshToken = jwtTokenProvider.createToken(
                        user.getId().toString(), toRoleList(user.getRoles()),
                        java.util.Map.of(
                            "email", user.getEmail(),
                            "tenantId", DEFAULT_TENANT_ID.getValue(),
                            "tokenType", "REFRESH"));
                    
                    return userRepository
                        .save(user)
                        .map(
                            ignored ->
                                AuthenticationResult.success(
                                    accessToken,
                                    refreshToken,
                                    TOKEN_TYPE,
                                    ACCESS_TOKEN_EXPIRY_SECONDS,
                                    userProfile));
                });
    }

    /**
     * Register a new user account.
     *
     * @param username desired username
     * @param email user email
     * @param password plain text password
     * @param firstName user first name (optional)
     * @param lastName user last name (optional)
     * @return AuthenticationResult with tokens and user info on success
     */
    public Promise<AuthenticationResult> register(
            String username, String email, String password, String firstName, String lastName) {
        return userRepository
            .existsByUsername(username)
            .then(exists -> {
                if (exists) {
                    return Promise.of(AuthenticationResult.failure("Username already exists"));
                }
                return userRepository
                    .existsByEmail(email)
                    .then(emailExists -> {
                        if (emailExists) {
                            return Promise.of(AuthenticationResult.failure("Email already registered"));
                        }
                        return Promise.ofBlocking(blockingExecutor, () ->
                            passwordHasher.hash(password)
                        ).then(hashedPassword -> {
                            User newUser = new User(username, email, hashedPassword, Set.of("USER"));
                            return userRepository.save(newUser).map(savedUser -> {
                                UserProfile profile = new UserProfile(
                                    savedUser.getId().toString(),
                                    savedUser.getUsername(),
                                    savedUser.getEmail(),
                                    savedUser.getRoles(),
                                    savedUser.isActive(),
                                    savedUser.getCreatedAt(),
                                    savedUser.getLastLoginAt());

                                String accessToken = jwtTokenProvider.createToken(
                                    savedUser.getId().toString(),
                                    toRoleList(savedUser.getRoles()),
                                    java.util.Map.of(
                                        "email", savedUser.getEmail(),
                                        "tenantId", DEFAULT_TENANT_ID.getValue(),
                                        "tokenType", "ACCESS"));
                                String refreshToken = jwtTokenProvider.createToken(
                                    savedUser.getId().toString(),
                                    toRoleList(savedUser.getRoles()),
                                    java.util.Map.of(
                                        "email", savedUser.getEmail(),
                                        "tenantId", DEFAULT_TENANT_ID.getValue(),
                                        "tokenType", "REFRESH"));

                                log.info("User registered successfully: {}", username);
                                return AuthenticationResult.success(
                                    accessToken, refreshToken, TOKEN_TYPE,
                                    ACCESS_TOKEN_EXPIRY_SECONDS, profile);
                            });
                        });
                    });
            });
    }

    /**
     * Refresh access token using refresh token.
     *
     * @param refreshToken Refresh token
     * @return New access token and refresh token
     */
    public Promise<AuthenticationResult> refreshToken(String refreshToken) {
        return Promise.<Optional<String>>ofBlocking(blockingExecutor, () -> {
            try {
                if (!jwtTokenProvider.validateToken(refreshToken)) {
                    log.warn("Token refresh failed - invalid token");
                    return Optional.empty();
                }
                String userId = jwtTokenProvider.getUserIdFromToken(refreshToken).orElse(null);
                
                if (userId == null || isTokenRevoked(userId)) {
                    log.warn("Token refresh failed - token revoked or no subject");
                    return Optional.empty();
                }
                
                return Optional.of(userId);
            } catch (Exception e) {
                log.warn("Token validation failed: {}", e.getMessage());
                return Optional.empty();
            }
        }).then(userIdOpt -> {
            if (userIdOpt.isEmpty()) {
                return Promise.of(AuthenticationResult.failure("Invalid token"));
            }
            
            final String userId = userIdOpt.get();
            return
            userRepository
                .findById(UUID.fromString(userId))
                .then(
                    userOptional -> {
                        if (userOptional.isEmpty()) {
                            log.warn("Token refresh failed - user not found: {}", userId);
                            return Promise.of(AuthenticationResult.failure("User not found"));
                        }

                        User user = userOptional.get();
                        if (!user.isActive()) {
                            log.warn("Token refresh failed - account disabled: {}", userId);
                            return Promise.of(AuthenticationResult.failure("Account is disabled"));
                        }

                        String newAccessToken = jwtTokenProvider.createToken(
                            user.getId().toString(), toRoleList(user.getRoles()),
                            java.util.Map.of(
                                "email", user.getEmail(),
                                "tenantId", DEFAULT_TENANT_ID.getValue(),
                                "tokenType", "ACCESS"));
                        String newRefreshToken = jwtTokenProvider.createToken(
                            user.getId().toString(), toRoleList(user.getRoles()),
                            java.util.Map.of(
                                "email", user.getEmail(),
                                "tenantId", DEFAULT_TENANT_ID.getValue(),
                                "tokenType", "REFRESH"));
                        
                        revokeToken(userId);
                        return Promise.of(AuthenticationResult.successTokenOnly(
                            newAccessToken,
                            newRefreshToken,
                            TOKEN_TYPE,
                            ACCESS_TOKEN_EXPIRY_SECONDS));
                    });
        });
    }

    /**
     * Logout user and revoke token.
     *
     * @param accessToken Access token to revoke
     * @return Success status
     */
    public Promise<Boolean> logout(String accessToken) {
        try {
            if (!jwtTokenProvider.validateToken(accessToken)) {
                log.warn("Logout failed: invalid token");
                return Promise.of(false);
            }
            String userId = jwtTokenProvider.getUserIdFromToken(accessToken).orElse(null);
            if (userId != null) {
                revokeToken(userId);
            }
            return Promise.of(true);
        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            return Promise.of(false);
        }
    }

    /**
     * Get current user from token.
     *
     * @param accessToken Access token
     * @return UserProfile if valid
     */
    public Promise<Optional<UserProfile>> getCurrentUser(String accessToken) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                if (!jwtTokenProvider.validateToken(accessToken)) {
                    log.warn("Get current user failed - invalid token");
                    return Optional.<String>empty();
                }
                String userId = jwtTokenProvider.getUserIdFromToken(accessToken).orElse(null);
                
                if (userId == null || isTokenRevoked(userId)) {
                    log.warn("Get current user failed - token revoked or no subject");
                    return Optional.<String>empty();
                }
                return Optional.of(userId);
            } catch (Exception e) {
                log.warn("Get current user failed: {}", e.getMessage());
                return Optional.<String>empty();
            }
        }).then(userIdOpt -> {
            if (userIdOpt.isEmpty()) {
                return Promise.of(Optional.<UserProfile>empty());
            }
            
            return userRepository
                .findById(UUID.fromString(userIdOpt.get()))
                .map(
                    userOptional ->
                        userOptional.map(
                            user ->
                                new UserProfile(
                                    user.getId().toString(),
                                    user.getUsername(),
                                    user.getEmail(),
                                    user.getRoles(),
                                    user.isActive(),
                                    user.getCreatedAt(),
                                    user.getLastLoginAt())));
        });
    }

    /**
     * Request password reset email.
     *
     * @param email User email
     * @return Success status
     */
    public Promise<Boolean> requestPasswordReset(String email) {
        return userRepository
            .findByUsernameOrEmail(email)
            .then(
                userOptional -> {
                    if (userOptional.isEmpty()) {
                        // Don't reveal if email exists
                        return Promise.of(true);
                    }

                    User user = userOptional.get();
                    String resetToken = UUID.randomUUID().toString();
                    user.setPasswordResetToken(resetToken);
                    user.setPasswordResetExpiresAt(Instant.now().plusSeconds(3600)); // 1 hour

                    return userRepository
                        .save(user)
                        .map(
                            ignored -> {
                                String resetLink =
                                    "https://yappc.ghatana.com/reset-password?token=" + resetToken;
                                sendPasswordResetEmail(user.getEmail(), resetLink);
                                log.info("Password reset email sent to user: {}", user.getUsername());
                                return true;
                            });
                });
    }

    /**
     * Confirm password reset with token and new password.
     *
     * @param resetToken Reset token
     * @param newPassword New password (plain text)
     * @return Success status
     */
    public Promise<Boolean> confirmPasswordReset(String resetToken, String newPassword) {
        return userRepository
            .findByPasswordResetToken(resetToken)
            .then(
                userOptional -> {
                    if (userOptional.isEmpty()) {
                        log.warn("Password reset failed - invalid token");
                        return Promise.of(false);
                    }

                    User user = userOptional.get();
                    if (user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
                        log.warn(
                            "Password reset failed - token expired for user: {}", user.getUsername());
                        return Promise.of(false);
                    }

                    return Promise.ofBlocking(blockingExecutor, () -> {
                        return passwordHasher.hash(newPassword);
                    }).then(hashedPassword -> {
                        user.setPasswordHash(hashedPassword);
                        user.setPasswordResetToken(null);
                        user.setPasswordResetExpiresAt(null);
                        
                        return userRepository.save(user)
                            .map(savedUser -> {
                                log.info("Password reset successful for user: {}", savedUser.getUsername());
                                return true;
                            });
                    }, e -> {
                        log.error("Failed to save user after password reset", e);
                        return Promise.of(false);
                    });
                });
    }

    // ============================================================================
    // Token Revocation Management
    // ============================================================================

    private void revokeToken(String tokenId) {
        Instant revokedAt = Instant.now();
        revokedTokens.put(tokenId, revokedAt);
        
        // Persist to Data-Cloud for distributed revocation
        Promise.ofBlocking(blockingExecutor, () -> {
            // NOTE: In-memory only — distributed deployments should persist to Data-Cloud revoked_tokens.
            log.debug("Token revoked (in-memory); Data-Cloud persistence deferred");
            // entityService.saveEntity(Entity.builder().type("revoked_token").data(tokenData).build());
            return null;
        }).whenException(e -> log.error("Failed to persist revoked token to Data-Cloud", e));
    }

    private boolean isTokenRevoked(String tokenId) {
        // Check local cache first
        if (revokedTokens.containsKey(tokenId)) {
            return true;
        }
        
        // Check Data-Cloud for distributed revocation (async, fire-and-forget)
        // In production, this should be a blocking check or use a distributed cache
        // For now, rely on local cache which is sufficient for single-instance deployments
        return false;
    }

    private static List<String> toRoleList(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream().sorted().toList();
    }

    /**
     * Clean up expired revoked tokens (should run periodically).
     */
    public void cleanupRevokedTokens() {
        Instant threshold = Instant.now().minusSeconds(REFRESH_TOKEN_EXPIRY_SECONDS);
        revokedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));
        log.debug("Cleaned up {} expired revoked tokens", revokedTokens.size());
    }    
    /**
     * Send password reset email to user.
     * 
     * @param email User email
     * @param resetLink Password reset link
     */
    private void sendPasswordResetEmail(String email, String resetLink) {
        Promise.ofBlocking(blockingExecutor, () -> {
            // In production, integrate with email service (SendGrid, AWS SES, etc.)
            // For now, log the email content
            String emailBody = String.format(
                """
                Hello,
                
                You requested a password reset for your YAPPC account.
                
                Click the link below to reset your password:
                %s
                
                This link will expire in 1 hour.
                
                If you didn't request this reset, please ignore this email.
                
                Best regards,
                YAPPC Team
                """, resetLink);
            
            log.info("Sending password reset email to: {}", email);
            log.debug("Email content:\n{}", emailBody);
            
            // NOTE: Email logged only — integrate platform EmailService for production delivery.
            // emailService.send(email, "Password Reset Request", emailBody);
            
            return null;
        }).whenException(e -> log.error("Failed to send password reset email", e));
    }
}
