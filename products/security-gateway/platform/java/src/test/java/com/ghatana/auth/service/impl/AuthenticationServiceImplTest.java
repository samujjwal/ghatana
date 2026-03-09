package com.ghatana.auth.service.impl;

import com.ghatana.platform.security.port.SessionStore;
import com.ghatana.platform.security.port.TokenStore;
import com.ghatana.auth.core.port.UserRepository;
import com.ghatana.platform.domain.auth.AuthResult;
import com.ghatana.auth.service.AuthenticationService;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.platform.domain.auth.Session;
import com.ghatana.platform.domain.auth.SessionId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.TokenType;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationServiceImpl.
 * 
 * <p>Tests validate:
 * - Authentication with valid/invalid credentials
 * - User registration with duplicate checks
 * - Session lifecycle (creation, validation, refresh, logout)
 * - Password management (change, reset request)
 * - Account security (locked accounts, OAuth users)
 * - Metrics collection for all operations
 * - Tenant isolation
 * 
 * <p><b>GIVEN-WHEN-THEN Structure</b><br>
 * All tests follow GIVEN-WHEN-THEN pattern with clear documentation.
 * 
 * <p><b>ActiveJ Promise Testing</b><br>
 * This test class extends EventloopTestBase and uses runPromise() 
 * for all async operations (MANDATORY for ActiveJ Promises).
 * 
 * @see AuthenticationServiceImpl
 * @see EventloopTestBase
 */
@DisplayName("Authentication Service Implementation Tests")
class AuthenticationServiceImplTest extends EventloopTestBase {

    private AuthenticationService authService;
    private UserRepository userRepository;
    private SessionStore sessionStore;
    private TokenStore tokenStore;
    private PasswordHasher passwordHasher;
    private MetricsCollector metrics;

    private static final TenantId TENANT_ID = TenantId.of("tenant-test");
    private static final String TEST_EMAIL = "alice@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_DISPLAY_NAME = "Alice Smith";
    private static final String TEST_USERNAME = "alice";

    @BeforeEach
    void setUp() {
        // GIVEN: Mocked dependencies
        userRepository = mock(UserRepository.class);
        sessionStore = mock(SessionStore.class);
        tokenStore = mock(TokenStore.class);
        passwordHasher = new PasswordHasher(); // Uses default cost
        metrics = mock(MetricsCollector.class);

        authService = new AuthenticationServiceImpl(
            userRepository,
            sessionStore,
            tokenStore,
            passwordHasher,
            metrics
        );
    }

    // ==================== AUTHENTICATE TESTS ====================

    /**
     * Verifies successful authentication with valid credentials.
     * 
     * GIVEN: User with valid password hash exists
     * WHEN: authenticate() called with correct password
     * THEN: Returns successful AuthResult with session and token
     *       AND metrics recorded (success, latency)
     */
    @Test
    @DisplayName("Should authenticate successfully with valid credentials")
    void shouldAuthenticateWithValidCredentials() {
        // GIVEN: User with valid password
        String passwordHash = passwordHasher.hash(TEST_PASSWORD);
        User user = createTestUser(passwordHash, true, false);

        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.of(user)));
        when(sessionStore.store(any(Session.class)))
            .thenReturn(Promise.of((Void) null));
        when(tokenStore.store(any(Token.class)))
            .thenReturn(Promise.of((Void) null));

        // WHEN: Authenticate with correct credentials
        AuthResult result = runPromise(() -> 
            authService.authenticate(TENANT_ID, TEST_EMAIL, TEST_PASSWORD)
        );

        // THEN: Authentication succeeds
        assertThat(result.isSuccess())
            .as("Authentication should succeed with valid credentials")
            .isTrue();
        assertThat(result.getSession())
            .as("Result should contain session")
            .isNotNull();
        assertThat(result.getSession().getUserId())
            .as("Session should be for authenticated user")
            .isEqualTo(user.getUserId());
        assertThat(result.getToken())
            .as("Result should contain access token")
            .isNotNull();
        assertThat(result.getToken().getUserId())
            .as("Token should be for authenticated user")
            .isEqualTo(user.getUserId());

        // THEN: Metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.authentication.success"),
            eq("tenant"), eq(TENANT_ID.value())
        );
        verify(metrics).recordTimer(
            eq("auth.authentication.latency"),
            anyLong()
        );
    }

    /**
     * Verifies authentication failure with invalid password.
     * 
     * GIVEN: User exists with different password
     * WHEN: authenticate() called with wrong password
     * THEN: Returns failure AuthResult with generic error message
     *       AND failure metrics recorded with reason
     */
    @Test
    @DisplayName("Should fail authentication with invalid password")
    void shouldFailAuthenticationWithInvalidPassword() {
        // GIVEN: User exists but password is wrong
        String correctPasswordHash = passwordHasher.hash("correctPassword");
        User user = createTestUser(correctPasswordHash, true, false);

        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.of(user)));

        // WHEN: Authenticate with wrong password
        AuthResult result = runPromise(() -> 
            authService.authenticate(TENANT_ID, TEST_EMAIL, "wrongPassword")
        );

        // THEN: Authentication fails
        assertThat(result.isFailure())
            .as("Authentication should fail with invalid password")
            .isTrue();
        assertThat(result.getErrorMessage())
            .as("Error message should be generic to prevent enumeration")
            .isEqualTo("Invalid email or password");

        // THEN: Failure metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.authentication.failure"),
            eq("tenant"), eq(TENANT_ID.value()),
            eq("reason"), eq("invalid_password")
        );
    }

    /**
     * Verifies authentication failure with non-existent email.
     * 
     * GIVEN: Email does not exist in database
     * WHEN: authenticate() called
     * THEN: Returns failure with generic error (prevent enumeration)
     *       AND failure metrics recorded
     */
    @Test
    @DisplayName("Should fail authentication with non-existent email")
    void shouldFailAuthenticationWithNonExistentEmail() {
        // GIVEN: Email does not exist
        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.empty()));

        // WHEN: Authenticate with non-existent email
        AuthResult result = runPromise(() -> 
            authService.authenticate(TENANT_ID, TEST_EMAIL, TEST_PASSWORD)
        );

        // THEN: Authentication fails with generic message
        assertThat(result.isFailure())
            .as("Authentication should fail with non-existent email")
            .isTrue();
        assertThat(result.getErrorMessage())
            .as("Error message should not reveal email existence")
            .isEqualTo("Invalid email or password");

        // THEN: Failure metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.authentication.failure"),
            eq("tenant"), eq(TENANT_ID.value()),
            eq("reason"), eq("user_not_found")
        );
    }

    /**
     * Verifies authentication failure when account is locked.
     * 
     * GIVEN: User account is locked
     * WHEN: authenticate() called
     * THEN: Returns failure with account locked message
     *       AND failure metrics recorded
     */
    @Test
    @DisplayName("Should fail authentication when account is locked")
    void shouldFailAuthenticationWhenAccountLocked() {
        // GIVEN: User account is locked
        String passwordHash = passwordHasher.hash(TEST_PASSWORD);
        User user = createTestUser(passwordHash, true, true); // locked=true

        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.of(user)));

        // WHEN: Authenticate with locked account
        AuthResult result = runPromise(() -> 
            authService.authenticate(TENANT_ID, TEST_EMAIL, TEST_PASSWORD)
        );

        // THEN: Authentication fails
        assertThat(result.isFailure())
            .as("Authentication should fail for locked account")
            .isTrue();
        assertThat(result.getErrorMessage())
            .as("Error message should indicate account locked")
            .isEqualTo("Account is locked");

        // THEN: Failure metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.authentication.failure"),
            eq("tenant"), eq(TENANT_ID.value()),
            eq("reason"), eq("account_locked")
        );
    }

    /**
     * Verifies authentication failure for OAuth users (no password hash).
     * 
     * GIVEN: User has no password hash (OAuth user)
     * WHEN: authenticate() called with password
     * THEN: Returns failure with generic error
     *       AND failure metrics recorded
     */
    @Test
    @DisplayName("Should fail authentication for OAuth users without password")
    void shouldFailAuthenticationForOAuthUsers() {
        // GIVEN: OAuth user with no password hash
        User user = User.forOAuth()
            .tenantId(TENANT_ID)
            .userId(UserId.random())
            .email(TEST_EMAIL)
            .displayName(TEST_DISPLAY_NAME)
            .username(TEST_USERNAME)
            // No passwordHash - OAuth user
            .active(true)
            .locked(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.of(user)));

        // WHEN: Authenticate OAuth user with password
        AuthResult result = runPromise(() -> 
            authService.authenticate(TENANT_ID, TEST_EMAIL, TEST_PASSWORD)
        );

        // THEN: Authentication fails
        assertThat(result.isFailure())
            .as("Authentication should fail for OAuth users")
            .isTrue();
        assertThat(result.getErrorMessage())
            .as("Error message should be generic")
            .isEqualTo("Invalid email or password");

        // THEN: Failure metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.authentication.failure"),
            eq("tenant"), eq(TENANT_ID.value()),
            eq("reason"), eq("no_password_hash")
        );
    }

    // ==================== REGISTER TESTS ====================

    /**
     * Verifies successful user registration.
     * 
     * GIVEN: Email does not exist
     * WHEN: register() called with valid data
     * THEN: User created with hashed password
     *       AND registration metrics recorded
     */
    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUserSuccessfully() {
        // GIVEN: Email does not exist
        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.empty()));
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(0)));

        // WHEN: Register new user
        User registered = runPromise(() -> 
            authService.register(
                TENANT_ID,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_DISPLAY_NAME,
                TEST_USERNAME
            )
        );

        // THEN: User created
        assertThat(registered)
            .as("Registration should return user")
            .isNotNull();
        assertThat(registered.getEmail())
            .as("Email should match")
            .isEqualTo(TEST_EMAIL);
        assertThat(registered.getPasswordHash())
            .as("Password should be hashed")
            .isPresent();
        assertThat(passwordHasher.verify(TEST_PASSWORD, registered.getPasswordHash().get()))
            .as("Password hash should be verifiable")
            .isTrue();

        // THEN: Registration metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.registration.success"),
            eq("tenant"), eq(TENANT_ID.value())
        );
        verify(metrics).recordTimer(
            eq("auth.registration.latency"),
            anyLong()
        );
    }

    /**
     * Verifies registration failure with duplicate email.
     * 
     * GIVEN: Email already exists
     * WHEN: register() called
     * THEN: Exception thrown
     *       AND error metrics recorded
     */
    @Test
    @DisplayName("Should fail registration with duplicate email")
    void shouldFailRegistrationWithDuplicateEmail() {
        // GIVEN: Email already exists
        User existingUser = createTestUser(passwordHasher.hash("existing"), true, false);
        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.of(existingUser)));

        // WHEN/THEN: Registration fails
        assertThatThrownBy(() -> 
            runPromise(() -> authService.register(
                TENANT_ID,
                TEST_EMAIL,
                TEST_PASSWORD,
                TEST_DISPLAY_NAME,
                TEST_USERNAME
            ))
        )
        .as("Should throw exception for duplicate email")
    .hasMessageContaining("already registered");

        // THEN: Error metrics recorded (tenant-only)
        verify(metrics).incrementCounter(
            eq("auth.registration.error"),
            eq("tenant"), eq(TENANT_ID.value())
        );
    }

    // ==================== LOGOUT TESTS ====================

    /**
     * Verifies successful logout with session revocation.
     * 
     * GIVEN: Valid session exists
     * WHEN: logout() called
     * THEN: Session revoked
     *       AND logout metrics recorded
     */
    @Test
    @DisplayName("Should logout successfully and revoke session")
    void shouldLogoutSuccessfully() {
        // GIVEN: Valid session exists
        SessionId sessionId = SessionId.random();
        Session session = createTestSession(UserId.random(), sessionId);
        
        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.of(session)));
        when(sessionStore.invalidate(TENANT_ID, sessionId))
            .thenReturn(Promise.complete());

        // WHEN: Logout
        runPromise(() -> authService.logout(TENANT_ID, sessionId));

        // THEN: Session revoked
        verify(sessionStore).invalidate(TENANT_ID, sessionId);

        // THEN: Logout metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.logout.success"),
            eq("tenant"), eq(TENANT_ID.value())
        );
    }

    /**
     * Verifies logout handles non-existent session gracefully.
     * 
     * GIVEN: Session does not exist
     * WHEN: logout() called
     * THEN: No exception thrown (no-op)
     */
    @Test
    @DisplayName("Should handle logout with non-existent session gracefully")
    void shouldHandleLogoutWithNonExistentSession() {
        // GIVEN: Session does not exist
        SessionId sessionId = SessionId.random();
        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.empty()));

        // WHEN: Logout with non-existent session
        runPromise(() -> authService.logout(TENANT_ID, sessionId));

        // THEN: No revocation attempted
        verify(sessionStore, never()).invalidate(any(), any());
    }

    // ==================== VALIDATE SESSION TESTS ====================

    /**
     * Verifies session validation for valid active session.
     * 
     * GIVEN: Valid active session exists
     * WHEN: validateSession() called
     * THEN: Returns true
     *       AND validation metrics recorded
     */
    @Test
    @DisplayName("Should validate active session successfully")
    void shouldValidateActiveSession() {
        // GIVEN: Valid active session
        SessionId sessionId = SessionId.random();
        Session session = Session.builder()
            .tenantId(TENANT_ID)
            .sessionId(sessionId)
            .userId(UserId.random())
            .createdAt(Instant.now().minus(Duration.ofHours(1)))
            .expiresAt(Instant.now().plus(Duration.ofHours(7)))
            .lastAccessedAt(Instant.now())
            // Provide deterministic ip/userAgent to satisfy Session invariants
            .ipAddress("127.0.0.1")
            .userAgent("test-agent")
            .valid(true)
            .build();

        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.of(session)));

        // WHEN: Validate session
        boolean isValid = runPromise(() -> 
            authService.validateSession(TENANT_ID, sessionId)
        );

        // THEN: Session is valid
        assertThat(isValid)
            .as("Active non-expired session should be valid")
            .isTrue();

        // THEN: Validation metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.session.validation"),
            eq("tenant"), eq(TENANT_ID.value()),
            eq("valid"), eq("true")
        );
    }

    /**
     * Verifies session validation fails for expired session.
     * 
     * GIVEN: Expired session exists
     * WHEN: validateSession() called
     * THEN: Returns false
     *       AND validation metrics recorded
     */
    @Test
    @DisplayName("Should fail validation for expired session")
    void shouldFailValidationForExpiredSession() {
        // GIVEN: Expired session
        SessionId sessionId = SessionId.random();
        Session session = Session.builder()
            .tenantId(TENANT_ID)
            .sessionId(sessionId)
            .userId(UserId.random())
            .createdAt(Instant.now().minus(Duration.ofHours(10)))
            .expiresAt(Instant.now().minus(Duration.ofHours(1))) // Expired
            .lastAccessedAt(Instant.now().minus(Duration.ofHours(2)))
            // Provide deterministic ip/userAgent to satisfy Session invariants
            .ipAddress("127.0.0.1")
            .userAgent("test-agent")
            .valid(true)
            .build();

        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.of(session)));

        // WHEN: Validate expired session
        boolean isValid = runPromise(() -> 
            authService.validateSession(TENANT_ID, sessionId)
        );

        // THEN: Session is invalid
        assertThat(isValid)
            .as("Expired session should be invalid")
            .isFalse();

        // THEN: Validation metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.session.validation"),
            eq("tenant"), eq(TENANT_ID.value()),
            eq("valid"), eq("false")
        );
    }

    /**
     * Verifies session validation fails for non-existent session.
     * 
     * GIVEN: Session does not exist
     * WHEN: validateSession() called
     * THEN: Returns false
     */
    @Test
    @DisplayName("Should fail validation for non-existent session")
    void shouldFailValidationForNonExistentSession() {
        // GIVEN: Session does not exist
        SessionId sessionId = SessionId.random();
        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.empty()));

        // WHEN: Validate non-existent session
        boolean isValid = runPromise(() -> 
            authService.validateSession(TENANT_ID, sessionId)
        );

        // THEN: Session is invalid
        assertThat(isValid)
            .as("Non-existent session should be invalid")
            .isFalse();
    }

    // ==================== REFRESH SESSION TESTS ====================

    /**
     * Verifies session refresh extends TTL.
     * 
     * GIVEN: Valid session exists
     * WHEN: refreshSession() called
     * THEN: New session with extended expiry returned
     *       AND refresh metrics recorded
     */
    @Test
    @DisplayName("Should refresh session and extend TTL")
    void shouldRefreshSessionAndExtendTtl() {
        // GIVEN: Valid session
        SessionId sessionId = SessionId.random();
        Instant originalExpiry = Instant.now().plus(Duration.ofHours(2));
        Session session = Session.builder()
            .tenantId(TENANT_ID)
            .sessionId(sessionId)
            .userId(UserId.random())
            .createdAt(Instant.now().minus(Duration.ofHours(6)))
            .expiresAt(originalExpiry)
            .lastAccessedAt(Instant.now().minus(Duration.ofMinutes(30)))
            // Provide deterministic ip/userAgent to satisfy Session invariants
            .ipAddress("127.0.0.1")
            .userAgent("test-agent")
            .valid(true)
            .build();

        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.of(session)));
        when(sessionStore.store(any(Session.class)))
            .thenReturn(Promise.of((Void) null));

        // WHEN: Refresh session
        Session refreshed = runPromise(() -> 
            authService.refreshSession(TENANT_ID, sessionId)
        );

        // THEN: Session refreshed with extended TTL
        assertThat(refreshed)
            .as("Refreshed session should be returned")
            .isNotNull();
        assertThat(refreshed.getExpiresAt())
            .as("Expiry should be extended beyond original")
            .isAfter(originalExpiry);
        assertThat(refreshed.getLastAccessedAt())
            .as("Last accessed time should be updated")
            .isPresent();
        assertThat(refreshed.getLastAccessedAt().get())
            .as("Last accessed time should be after original")
            .isAfter(session.getLastAccessedAt().orElse(Instant.MIN));

        // THEN: Refresh metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.session.refresh"),
            eq("tenant"), eq(TENANT_ID.value())
        );
    }

    /**
     * Verifies refresh fails for expired session.
     * 
     * GIVEN: Expired session exists
     * WHEN: refreshSession() called
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should fail to refresh expired session")
    void shouldFailToRefreshExpiredSession() {
        // GIVEN: Expired session
        SessionId sessionId = SessionId.random();
        Session session = Session.builder()
            .tenantId(TENANT_ID)
            .sessionId(sessionId)
            .userId(UserId.random())
            .createdAt(Instant.now().minus(Duration.ofHours(10)))
            .expiresAt(Instant.now().minus(Duration.ofHours(1))) // Expired
            .lastAccessedAt(Instant.now().minus(Duration.ofHours(2)))
            // Provide deterministic ip/userAgent to satisfy Session invariants
            .ipAddress("127.0.0.1")
            .userAgent("test-agent")
            .valid(true)
            .build();

        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.of(session)));

        // WHEN/THEN: Refresh fails
        assertThatThrownBy(() -> 
            runPromise(() -> authService.refreshSession(TENANT_ID, sessionId))
        )
        .as("Should throw exception for expired session")
        .hasMessageContaining("expired");
    }

    /**
     * Verifies refresh fails for non-existent session.
     * 
     * GIVEN: Session does not exist
     * WHEN: refreshSession() called
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should fail to refresh non-existent session")
    void shouldFailToRefreshNonExistentSession() {
        // GIVEN: Session does not exist
        SessionId sessionId = SessionId.random();
        when(sessionStore.findById(TENANT_ID, sessionId))
            .thenReturn(Promise.of(Optional.empty()));

        // WHEN/THEN: Refresh fails
        assertThatThrownBy(() -> 
            runPromise(() -> authService.refreshSession(TENANT_ID, sessionId))
        )
        .as("Should throw exception for non-existent session")
        .hasMessageContaining("not found");
    }

    // ==================== CHANGE PASSWORD TESTS ====================

    /**
     * Verifies password change with correct current password.
     * 
     * GIVEN: User with valid current password
     * WHEN: changePassword() called with correct current password
     * THEN: Password updated
     *       AND all user sessions revoked
     *       AND password change metrics recorded
     */
    @Test
    @DisplayName("Should change password and revoke all sessions")
    void shouldChangePasswordAndRevokeAllSessions() {
        // GIVEN: User with current password
        String currentPassword = "oldPassword123";
        String newPassword = "newPassword456";
        String currentPasswordHash = passwordHasher.hash(currentPassword);
        UserId userId = UserId.random();
        User user = createTestUserWithId(userId, currentPasswordHash, true, false);

        when(userRepository.findByUserId(TENANT_ID, userId))
            .thenReturn(Promise.of(Optional.of(user)));
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(0)));
        when(sessionStore.invalidateAllForUser(TENANT_ID, userId))
            .thenReturn(Promise.of(0));

        // WHEN: Change password
        runPromise(() -> authService.changePassword(
            TENANT_ID,
            userId,
            currentPassword,
            newPassword
        ));

        // THEN: Password updated
        verify(userRepository).save(argThat(savedUser -> {
            assertThat(savedUser.getPasswordHash())
                .as("Password hash should be updated")
                .isPresent();
            assertThat(passwordHasher.verify(newPassword, savedUser.getPasswordHash().get()))
                .as("New password should be verifiable")
                .isTrue();
            return true;
        }));

        // THEN: All sessions revoked
        verify(sessionStore).invalidateAllForUser(TENANT_ID, userId);

        // THEN: Password change metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.password.change"),
            eq("tenant"), eq(TENANT_ID.value())
        );
    }

    /**
     * Verifies password change fails with incorrect current password.
     * 
     * GIVEN: User with valid current password
     * WHEN: changePassword() called with wrong current password
     * THEN: Exception thrown
     *       AND password not updated
     */
    @Test
    @DisplayName("Should fail password change with incorrect current password")
    void shouldFailPasswordChangeWithIncorrectCurrentPassword() {
        // GIVEN: User with current password
        String currentPassword = "correctPassword";
        String wrongPassword = "wrongPassword";
        String newPassword = "newPassword456";
        String currentPasswordHash = passwordHasher.hash(currentPassword);
        UserId userId = UserId.random();
        User user = createTestUserWithId(userId, currentPasswordHash, true, false);

        when(userRepository.findByUserId(TENANT_ID, userId))
            .thenReturn(Promise.of(Optional.of(user)));

        // WHEN/THEN: Change password fails
        assertThatThrownBy(() -> 
            runPromise(() -> authService.changePassword(
                TENANT_ID,
                userId,
                wrongPassword,
                newPassword
            ))
        )
        .as("Should throw exception for incorrect current password")
        .hasMessageContaining("incorrect");

        // THEN: Password not saved
        verify(userRepository, never()).save(any());
    }

    // ==================== REQUEST PASSWORD RESET TESTS ====================

    /**
     * Verifies password reset request for existing email.
     * 
     * GIVEN: User with email exists
     * WHEN: requestPasswordReset() called
     * THEN: Reset token generated and returned
     *       AND reset request metrics recorded
     */
    @Test
    @DisplayName("Should generate reset token for existing email")
    void shouldGenerateResetTokenForExistingEmail() {
        // GIVEN: User exists
        User user = createTestUser(passwordHasher.hash("password"), true, false);
        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.of(user)));

        // WHEN: Request password reset
        Optional<String> resetToken = runPromise(() -> 
            authService.requestPasswordReset(TENANT_ID, TEST_EMAIL)
        );

        // THEN: Reset token returned
        assertThat(resetToken)
            .as("Reset token should be present for existing email")
            .isPresent();
        assertThat(resetToken.get())
            .as("Reset token should not be empty")
            .isNotBlank();

        // THEN: Reset request metrics recorded
        verify(metrics).incrementCounter(
            eq("auth.password.reset.request"),
            eq("tenant"), eq(TENANT_ID.value())
        );
    }

    /**
     * Verifies password reset request returns empty for non-existent email.
     * 
     * GIVEN: Email does not exist
     * WHEN: requestPasswordReset() called
     * THEN: Empty Optional returned (security - don't reveal existence)
     */
    @Test
    @DisplayName("Should return empty for non-existent email (security)")
    void shouldReturnEmptyForNonExistentEmail() {
        // GIVEN: Email does not exist
        when(userRepository.findByEmail(TENANT_ID, TEST_EMAIL))
            .thenReturn(Promise.of(Optional.empty()));

        // WHEN: Request password reset
        Optional<String> resetToken = runPromise(() -> 
            authService.requestPasswordReset(TENANT_ID, TEST_EMAIL)
        );

        // THEN: Empty returned (security)
        assertThat(resetToken)
            .as("Should return empty to prevent email enumeration")
            .isEmpty();
    }

    // ==================== HELPER METHODS ====================

    private User createTestUser(String passwordHash, boolean active, boolean locked) {
        return User.forInternalAuth()
            .tenantId(TENANT_ID)
            .userId(UserId.random())
            .email(TEST_EMAIL)
            .displayName(TEST_DISPLAY_NAME)
            .username(TEST_USERNAME)
            .passwordHash(passwordHash)
            .active(active)
            .locked(locked)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private User createTestUserWithId(UserId userId, String passwordHash, boolean active, boolean locked) {
        return User.forInternalAuth()
            .tenantId(TENANT_ID)
            .userId(userId)
            .email(TEST_EMAIL)
            .displayName(TEST_DISPLAY_NAME)
            .username(TEST_USERNAME)
            .passwordHash(passwordHash)
            .active(active)
            .locked(locked)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private Session createTestSession(UserId userId, SessionId sessionId) {
        return Session.builder()
            .tenantId(TENANT_ID)
            .sessionId(sessionId)
            .userId(userId)
            .createdAt(Instant.now().minus(Duration.ofHours(1)))
            .expiresAt(Instant.now().plus(Duration.ofHours(7)))
            .lastAccessedAt(Instant.now())
            // Tests should supply ipAddress and userAgent to satisfy Session
            // invariants. Use deterministic values for assertions.
            .ipAddress("127.0.0.1")
            .userAgent("test-agent")
            .valid(true)
            .build();
    }
}
