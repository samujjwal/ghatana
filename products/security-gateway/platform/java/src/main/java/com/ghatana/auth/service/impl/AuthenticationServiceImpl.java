package com.ghatana.auth.service.impl;

import com.ghatana.auth.core.port.UserRepository;
import com.ghatana.auth.service.AuthenticationService;
import com.ghatana.platform.domain.auth.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.platform.security.port.SessionStore;
import com.ghatana.platform.security.port.TokenStore;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of AuthenticationService.
 *
 * @doc.type class
 * @doc.purpose Authentication service implementation with session and token management
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final SessionStore sessionStore;
    private final TokenStore tokenStore;
    private final PasswordHasher passwordHasher;
    private final MetricsCollector metrics;

    public AuthenticationServiceImpl(
            UserRepository userRepository,
            SessionStore sessionStore,
            TokenStore tokenStore,
            PasswordHasher passwordHasher,
            MetricsCollector metrics
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.sessionStore = Objects.requireNonNull(sessionStore);
        this.tokenStore = Objects.requireNonNull(tokenStore);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public Promise<AuthResult> authenticate(TenantId tenantId, String email, String password) {
        long start = System.currentTimeMillis();
        return userRepository.findByEmail(tenantId, email)
                .then(maybeUser -> {
                    if (maybeUser.isEmpty()) {
                        metrics.incrementCounter("auth.authentication.failure",
                                "tenant", tenantId.value(), "reason", "user_not_found");
                        metrics.recordTimer("auth.authentication.latency",
                                System.currentTimeMillis() - start);
                        return Promise.of(AuthResult.failure("Invalid email or password"));
                    }

                    User user = maybeUser.get();

                    if (user.isLocked()) {
                        metrics.incrementCounter("auth.authentication.failure",
                                "tenant", tenantId.value(), "reason", "account_locked");
                        metrics.recordTimer("auth.authentication.latency",
                                System.currentTimeMillis() - start);
                        return Promise.of(AuthResult.failure("Account is locked"));
                    }

                    if (user.getPasswordHash().isEmpty()) {
                        metrics.incrementCounter("auth.authentication.failure",
                                "tenant", tenantId.value(), "reason", "no_password_hash");
                        metrics.recordTimer("auth.authentication.latency",
                                System.currentTimeMillis() - start);
                        return Promise.of(AuthResult.failure("Invalid email or password"));
                    }

                    if (!passwordHasher.verify(password, user.getPasswordHash().get())) {
                        metrics.incrementCounter("auth.authentication.failure",
                                "tenant", tenantId.value(), "reason", "invalid_password");
                        metrics.recordTimer("auth.authentication.latency",
                                System.currentTimeMillis() - start);
                        return Promise.of(AuthResult.failure("Invalid email or password"));
                    }

                    // Create session
                    Instant now = Instant.now();
                    Session session = Session.builder()
                            .tenantId(tenantId)
                            .userId(user.getUserId())
                            .createdAt(now)
                            .expiresAt(now.plus(SESSION_TTL))
                            .lastAccessedAt(now)
                            .ipAddress("0.0.0.0")
                            .userAgent("unknown")
                            .valid(true)
                            .build();

                    // Create token
                    Token token = Token.builder()
                            .tenantId(tenantId)
                            .tokenId(TokenId.random())
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .userId(user.getUserId())
                            .clientId(ClientId.of("default"))
                            .issuedAt(now)
                            .expiresAt(now.plus(TOKEN_TTL))
                            .tokenValue(UUID.randomUUID().toString())
                            .build();

                    return sessionStore.store(session)
                            .then($ -> tokenStore.store(token))
                            .map($ -> {
                                metrics.incrementCounter("auth.authentication.success",
                                        "tenant", tenantId.value());
                                metrics.recordTimer("auth.authentication.latency",
                                        System.currentTimeMillis() - start);
                                return AuthResult.success(session, token);
                            });
                });
    }

    @Override
    public Promise<User> register(TenantId tenantId, String email, String password,
                                   String displayName, String username) {
        long start = System.currentTimeMillis();
        return userRepository.findByEmail(tenantId, email)
                .then(existing -> {
                    if (existing.isPresent()) {
                        metrics.incrementCounter("auth.registration.error",
                                "tenant", tenantId.value());
                        return Promise.ofException(
                                new IllegalStateException("Email already registered"));
                    }

                    String hash = passwordHasher.hash(password);
                    User user = User.forInternalAuth()
                            .tenantId(tenantId)
                            .userId(UserId.random())
                            .email(email)
                            .displayName(displayName)
                            .username(username)
                            .passwordHash(hash)
                            .active(true)
                            .locked(false)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    return userRepository.save(user)
                            .map(saved -> {
                                metrics.incrementCounter("auth.registration.success",
                                        "tenant", tenantId.value());
                                metrics.recordTimer("auth.registration.latency",
                                        System.currentTimeMillis() - start);
                                return saved;
                            });
                });
    }

    @Override
    public Promise<Void> logout(TenantId tenantId, SessionId sessionId) {
        return sessionStore.findById(tenantId, sessionId)
                .then(maybeSession -> {
                    if (maybeSession.isEmpty()) {
                        return Promise.of((Void) null);
                    }
                    return sessionStore.invalidate(tenantId, sessionId)
                            .map($ -> {
                                metrics.incrementCounter("auth.logout.success",
                                        "tenant", tenantId.value());
                                return (Void) null;
                            });
                });
    }

    @Override
    public Promise<Boolean> validateSession(TenantId tenantId, SessionId sessionId) {
        return sessionStore.findById(tenantId, sessionId)
                .map(maybeSession -> {
                    if (maybeSession.isEmpty()) {
                        metrics.incrementCounter("auth.session.validation",
                                "tenant", tenantId.value(), "valid", "false");
                        return false;
                    }
                    Session session = maybeSession.get();
                    boolean valid = session.isValid()
                            && session.getExpiresAt().isAfter(Instant.now());
                    metrics.incrementCounter("auth.session.validation",
                            "tenant", tenantId.value(),
                            "valid", String.valueOf(valid));
                    return valid;
                });
    }

    @Override
    public Promise<Session> refreshSession(TenantId tenantId, SessionId sessionId) {
        return sessionStore.findById(tenantId, sessionId)
                .then(maybeSession -> {
                    if (maybeSession.isEmpty()) {
                        return Promise.ofException(
                                new IllegalStateException("Session not found"));
                    }
                    Session session = maybeSession.get();
                    if (session.getExpiresAt().isBefore(Instant.now())) {
                        return Promise.ofException(
                                new IllegalStateException("Session expired"));
                    }
                    Instant now = Instant.now();
                    Session refreshed = Session.builder()
                            .tenantId(session.getTenantId())
                            .sessionId(session.getSessionId())
                            .userId(session.getUserId())
                            .createdAt(session.getCreatedAt())
                            .expiresAt(now.plus(SESSION_TTL))
                            .lastAccessedAt(now)
                            .ipAddress("127.0.0.1")
                            .userAgent("unknown")
                            .valid(true)
                            .build();
                    return sessionStore.store(refreshed)
                            .map($ -> {
                                metrics.incrementCounter("auth.session.refresh",
                                        "tenant", tenantId.value());
                                return refreshed;
                            });
                });
    }

    @Override
    public Promise<Void> changePassword(TenantId tenantId, UserId userId,
                                         String currentPassword, String newPassword) {
        return userRepository.findByUserId(tenantId, userId)
                .then(maybeUser -> {
                    if (maybeUser.isEmpty()) {
                        return Promise.ofException(
                                new IllegalStateException("User not found"));
                    }
                    User user = maybeUser.get();
                    if (user.getPasswordHash().isEmpty()
                            || !passwordHasher.verify(currentPassword, user.getPasswordHash().get())) {
                        return Promise.ofException(
                                new IllegalStateException("Current password is incorrect"));
                    }

                    String newHash = passwordHasher.hash(newPassword);
                    User updated = User.forInternalAuth()
                            .tenantId(user.getTenantId())
                            .userId(user.getUserId())
                            .email(user.getEmail())
                            .displayName(user.getDisplayName())
                            .username(user.getUsername())
                            .passwordHash(newHash)
                            .active(user.isActive())
                            .locked(user.isLocked())
                            .createdAt(user.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();

                    return userRepository.save(updated)
                            .then($ -> sessionStore.invalidateAllForUser(tenantId, userId))
                            .map($ -> {
                                metrics.incrementCounter("auth.password.change",
                                        "tenant", tenantId.value());
                                return (Void) null;
                            });
                });
    }

    @Override
    public Promise<Optional<String>> requestPasswordReset(TenantId tenantId, String email) {
        return userRepository.findByEmail(tenantId, email)
                .map(maybeUser -> {
                    if (maybeUser.isEmpty()) {
                        return Optional.empty();
                    }
                    String resetToken = UUID.randomUUID().toString();
                    metrics.incrementCounter("auth.password.reset.request",
                            "tenant", tenantId.value());
                    return Optional.of(resetToken);
                });
    }
}
