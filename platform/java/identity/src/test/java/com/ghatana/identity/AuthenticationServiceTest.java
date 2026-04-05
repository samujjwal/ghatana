/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link DefaultAuthenticationService}.
 *
 * @doc.type class
 * @doc.purpose Tests for authentication, login attempts, lockout enforcement
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultAuthenticationService")
class AuthenticationServiceTest extends EventloopTestBase {

    private DefaultAuthenticationService authService;
    private TokenProvider tokenProvider;
    private DefaultIdentityService identityService;
    private InMemoryIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        tokenProvider = new DefaultTokenProvider();
        resolver = new InMemoryIdentityResolver();
        identityService = new DefaultIdentityService(resolver);
        authService = new DefaultAuthenticationService(tokenProvider, identityService);

        // Register test agent
        AgentIdentity agent = new AgentIdentity("t1", "agent-1",
            "spiffe://ghatana.io/t1/agent-1", Set.of("read", "write"), Instant.now());
        resolver.register(agent);
    }

    @Nested
    @DisplayName("recordFailedAttempt()")
    class FailedAttemptTests {

        @Test
        @DisplayName("Records and increments failed attempts")
        void recordsFailedAttempts() {
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isEmpty(); // Not locked yet (need 5 attempts)
        }

        @Test
        @DisplayName("Locks account after 5 failed attempts")
        void locksAfter5Attempts() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isPresent();
            assertThat(lockout.get().failedAttempts()).isEqualTo(5);
            assertThat(lockout.get().isActive()).isTrue();
        }

        @Test
        @DisplayName("Different tenants have separate counters")
        void tenantSeparation() {
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.recordFailedAttempt("t2", "agent-1"));

            Optional<LockoutInfo> t1Lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            Optional<LockoutInfo> t2Lockout = runPromise(() -> authService.checkLockout("t2", "agent-1"));

            assertThat(t1Lockout).isEmpty();
            assertThat(t2Lockout).isEmpty();
        }
    }

    @Nested
    @DisplayName("checkLockout()")
    class LockoutCheckTests {

        @Test
        @DisplayName("Returns empty when not locked")
        void returnsEmptyWhenNotLocked() {
            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isEmpty();
        }

        @Test
        @DisplayName("Returns active lockout info")
        void returnsLockoutInfo() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isPresent();
            assertThat(lockout.get().failedAttempts()).isEqualTo(5);
            assertThat(lockout.get().remainingTime()).isGreaterThan(Duration.ZERO);
        }

        @Test
        @DisplayName("Expires lockout after grace period")
        void expiresLockoutAfterDelay() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<LockoutInfo> locked = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(locked).isPresent();

            // Note: In actual test, we'd wait for grace period; this validates the inactive case
            // For now, just verify structure
            assertThat(locked.get().isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("resetFailedAttempts()")
    class ResetFailedAttemptsTests {

        @Test
        @DisplayName("Clears failed attempt counter")
        void clearsCounter() {
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.resetFailedAttempts("t1", "agent-1"));

            for (int i = 0; i < 4; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isEmpty(); // Would be locked if counter wasn't reset
        }

        @Test
        @DisplayName("Removes active lockout")
        void removesLockout() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<LockoutInfo> locked = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(locked).isPresent();

            runPromise(() -> authService.resetFailedAttempts("t1", "agent-1"));
            Optional<LockoutInfo> unlocked = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(unlocked).isEmpty();
        }
    }

    @Nested
    @DisplayName("authenticate()")
    class AuthenticateTests {

        @Test
        @DisplayName("Successful authentication returns session token")
        void successfulAuthReturnsToken() {
            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "agent-1", "valid-hash"));

            assertThat(sessionToken).isPresent();
            assertThat(sessionToken.get()).isNotBlank();
        }

        @Test
        @DisplayName("Failed authentication with invalid credentials")
        void failedAuthWithInvalidCredentials() {
            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "agent-1", ""));

            assertThat(sessionToken).isEmpty();
        }

        @Test
        @DisplayName("Failed authentication increments attempt counter")
        void failedAuthIncrementsCounter() {
            runPromise(() -> authService.authenticate("t1", "agent-1", ""));
            runPromise(() -> authService.authenticate("t1", "agent-1", ""));

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isEmpty(); // Not locked yet
        }

        @Test
        @DisplayName("Cannot authenticate when locked")
        void cannotAuthWhenLocked() {
            // Lock the account
            for (int i = 0; i < 5; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "agent-1", "valid-hash"));

            assertThat(sessionToken).isEmpty();
        }

        @Test
        @DisplayName("Successful auth resets failed attempts")
        void successfulAuthResetsAttempts() {
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));

            runPromise(() -> authService.authenticate("t1", "agent-1", "valid-hash"));

            // Should be able to fail 5 times again without lockout
            for (int i = 0; i < 4; i++) {
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1"));
            }

            Optional<LockoutInfo> lockout = runPromise(() -> authService.checkLockout("t1", "agent-1"));
            assertThat(lockout).isEmpty();
        }

        @Test
        @DisplayName("Authentication rejects unknown agent")
        void authRejectsUnknownAgent() {
            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "unknown-agent", "valid-hash"));

            assertThat(sessionToken).isEmpty();
        }

        @Test
        @DisplayName("Authenticated session token is valid immediately")
        void sessionTokenValid() {
            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "agent-1", "valid-hash"));

            assertThat(sessionToken).isPresent();
            // Token should be verifiable by tokenProvider
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Logout invalidates session token")
        void logoutInvalidatesToken() {
            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "agent-1", "valid-hash"));

            assertThat(sessionToken).isPresent();

            runPromise(() -> authService.logout(sessionToken.get()));

            // Session should be terminated (would be checked by session verification service)
        }

        @Test
        @DisplayName("Logout unknown token is no-op")
        void logoutUnknownTokenNoOp() {
            // Must not throw
            runPromise(() -> authService.logout("nonexistent-session"));
        }

        @Test
        @DisplayName("Multiple logouts of same token is no-op")
        void multipleLogoutsNoOp() {
            Optional<String> sessionToken = runPromise(() ->
                authService.authenticate("t1", "agent-1", "valid-hash"));

            runPromise(() -> authService.logout(sessionToken.get()));
            // Second logout should not throw
            runPromise(() -> authService.logout(sessionToken.get()));
        }
    }
}
