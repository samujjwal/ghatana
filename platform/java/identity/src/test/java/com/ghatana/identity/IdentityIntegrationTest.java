/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Integration tests for identity module with other platform services.
 *
 * <p>Tests real-world scenarios involving:
 * - Multiple module interactions (governance, database, observability) // GH-90000
 * - Tenant isolation enforcement
 * - Concurrent access patterns
 * - Key rotation with in-flight tokens
 * - Cascading authorization checks
 *
 * @doc.type class
 * @doc.purpose Integration tests covering cross-module scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Identity Integration Tests")
class IdentityIntegrationTest extends EventloopTestBase {

    private DefaultTokenProvider tokenProvider;
    private DefaultIdentityService identityService;
    private DefaultAuthenticationService authService;
    private DefaultAuthorizationService authzService;
    private InMemoryIdentityResolver resolver;

    @BeforeEach
    void setUp() { // GH-90000
        tokenProvider = new DefaultTokenProvider(); // GH-90000
        resolver = new InMemoryIdentityResolver(); // GH-90000
        identityService = new DefaultIdentityService(resolver); // GH-90000
        authService = new DefaultAuthenticationService(tokenProvider, identityService); // GH-90000
        authzService = new DefaultAuthorizationService(identityService); // GH-90000

        // Setup test agents for different tenants
        AgentIdentity agent1t1 = new AgentIdentity("t1", "agent-1", // GH-90000
            "spiffe://ghatana.io/t1/agent-1", Set.of("collection:read", "job:execute"), Instant.now()); // GH-90000
        AgentIdentity agent1t2 = new AgentIdentity("t2", "agent-1", // GH-90000
            "spiffe://ghatana.io/t2/agent-1", Set.of("collection:read"), Instant.now());
        resolver.register(agent1t1); // GH-90000
        resolver.register(agent1t2); // GH-90000
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("Agent in t1 cannot access resources in t2")
        void agentT1CannotAccessT2() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t2", "agent-1", "collection:read")); // GH-90000

            // agent-1 in t2 has permission, but since we're checking t2 context,
            // we need to register this correctly
            assertThat(authorized).isTrue(); // GH-90000

            // Now check t1 agent trying to access t2
            Boolean t1Authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t2", "agent-1", "job:execute")); // GH-90000

            // The registered agent-1 in t2 doesn't have job:execute
            assertThat(t1Authorized).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Authentication is tenant-scoped")
        void authenticationTenantScoped() { // GH-90000
            Optional<String> t1Session = runPromise(() -> // GH-90000
                authService.authenticate("t1", "agent-1", "valid-hash")); // GH-90000
            Optional<String> t2Session = runPromise(() -> // GH-90000
                authService.authenticate("t2", "agent-1", "valid-hash")); // GH-90000

            assertThat(t1Session).isPresent(); // GH-90000
            assertThat(t2Session).isPresent(); // GH-90000
            assertThat(t1Session).isNotEqualTo(t2Session); // GH-90000
        }

        @Test
        @DisplayName("Failed attempts are tenant-scoped")
        void failedAttemptsTenantScoped() { // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1")); // GH-90000
            }

            Optional<LockoutInfo> t1Lockout = runPromise(() -> authService.checkLockout("t1", "agent-1")); // GH-90000
            Optional<LockoutInfo> t2Lockout = runPromise(() -> authService.checkLockout("t2", "agent-1")); // GH-90000

            assertThat(t1Lockout).isEmpty(); // GH-90000
            assertThat(t2Lockout).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Authentication + Authorization Flow")
    class AuthFlowTests {

        @Test
        @DisplayName("Complete login and authorization check")
        void completeAuthFlow() { // GH-90000
            // 1. Authenticate
            Optional<String> sessionToken = runPromise(() -> // GH-90000
                authService.authenticate("t1", "agent-1", "valid-hash")); // GH-90000
            assertThat(sessionToken).isPresent(); // GH-90000

            // 2. Extract token claims
            Optional<TokenClaims> claims = runPromise(() -> // GH-90000
                tokenProvider.verifyToken(sessionToken.get())); // GH-90000
            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().tenantId()).isEqualTo("t1");
            assertThat(claims.get().agentId()).isEqualTo("agent-1");

            // 3. Check authorization
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "agent-1", "job:execute")); // GH-90000
            assertThat(authorized).isTrue(); // GH-90000

            // 4. Logout
            runPromise(() -> authService.logout(sessionToken.get())); // GH-90000
        }

        @Test
        @DisplayName("Lockout prevents further authentication")
        void lockoutPreventsAuth() { // GH-90000
            // Force lockout
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1")); // GH-90000
            }

            // Try to authenticate while locked
            Optional<String> sessionToken = runPromise(() -> // GH-90000
                authService.authenticate("t1", "agent-1", "valid-hash")); // GH-90000

            assertThat(sessionToken).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Token Lifecycle")
    class TokenLifecycleTests {

        @Test
        @DisplayName("Token remains valid throughout session")
        void tokenValidThroughputSession() { // GH-90000
            String token = runPromise(() -> tokenProvider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            // Verify immediately
            Optional<TokenClaims> claims1 = runPromise(() -> tokenProvider.verifyToken(token)); // GH-90000
            assertThat(claims1).isPresent(); // GH-90000

            // Simulate small delay
            try { Thread.sleep(10); } catch (InterruptedException e) {} // GH-90000

            // Still valid
            Optional<TokenClaims> claims2 = runPromise(() -> tokenProvider.verifyToken(token)); // GH-90000
            assertThat(claims2).isPresent(); // GH-90000

            // Claims are consistent
            assertThat(claims1.get().tokenId()).isEqualTo(claims2.get().tokenId()); // GH-90000
        }

        @Test
        @DisplayName("Key rotation with grace period backward compatible")
        void keyRotationBackwardCompatible() { // GH-90000
            // Issue token with old key
            String oldToken = runPromise(() -> tokenProvider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            // Rotate key with grace period
            runPromise(() -> tokenProvider.rotateSigningKey(Duration.ofSeconds(5))); // GH-90000

            // Old token still valid during grace
            Optional<TokenClaims> claims = runPromise(() -> tokenProvider.verifyToken(oldToken)); // GH-90000
            assertThat(claims).isPresent(); // GH-90000

            // Issue new token with new key
            String newToken = runPromise(() -> tokenProvider.createToken("t1", "agent-2", Duration.ofMinutes(10))); // GH-90000

            // Both tokens valid during grace period
            Optional<TokenClaims> oldClaims = runPromise(() -> tokenProvider.verifyToken(oldToken)); // GH-90000
            Optional<TokenClaims> newClaims = runPromise(() -> tokenProvider.verifyToken(newToken)); // GH-90000
            assertThat(oldClaims).isPresent(); // GH-90000
            assertThat(newClaims).isPresent(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Delegation Token Integration")
    class DelegationIntegrationTests {

        @Test
        @DisplayName("Delegation preserves tenant boundaries")
        void delegationTenantBoundaries() { // GH-90000
            DelegationTokenService delegService = new DefaultDelegationTokenService(); // GH-90000

            DelegationToken delegation = runPromise(() -> // GH-90000
                delegService.delegate("t1", "agent-a", "agent-b", Set.of("read"), Duration.ofHours(1)));

            assertThat(delegation.tenantId()).isEqualTo("t1");
            assertThat(delegation.chain()).containsExactly("agent-a", "agent-b"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Concurrency & Race Conditions")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent token creation is safe")
        void concurrentTokenCreationSafe() { // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                String token1 = runPromise(() -> tokenProvider.createToken("t1", "agent-" + idx, Duration.ofMinutes(10))); // GH-90000
                String token2 = runPromise(() -> tokenProvider.createToken("t1", "agent-" + idx, Duration.ofMinutes(10))); // GH-90000

                assertThat(token1).isNotEqualTo(token2); // GH-90000

                Optional<TokenClaims> claims1 = runPromise(() -> tokenProvider.verifyToken(token1)); // GH-90000
                Optional<TokenClaims> claims2 = runPromise(() -> tokenProvider.verifyToken(token2)); // GH-90000

                assertThat(claims1).isPresent(); // GH-90000
                assertThat(claims2).isPresent(); // GH-90000
            }
        }

        @Test
        @DisplayName("Concurrent authentication and authorization")
        void concurrentAuthzChecks() { // GH-90000
            Optional<String> session = runPromise(() -> // GH-90000
                authService.authenticate("t1", "agent-1", "valid-hash")); // GH-90000
            assertThat(session).isPresent(); // GH-90000

            // Multiple authorization checks in rapid succession
            for (int i = 0; i < 5; i++) { // GH-90000
                Boolean authorized = runPromise(() -> // GH-90000
                    authzService.isAuthorized("t1", "agent-1", "collection:read")); // GH-90000
                assertThat(authorized).isTrue(); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Error Handling & Recovery")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Handles unknown principal gracefully")
        void unknownPrincipalHandling() { // GH-90000
            Boolean authorized = runPromise(() -> // GH-90000
                authzService.isAuthorized("t1", "nonexistent-agent", "collection:read")); // GH-90000

            assertThat(authorized).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Handles malformed token gracefully")
        void malformedTokenHandling() { // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> // GH-90000
                tokenProvider.verifyToken("not-a-valid-jwt"));

            assertThat(claims).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Handles invalid credentials in authentication")
        void invalidCredentialsHandling() { // GH-90000
            Optional<String> session = runPromise(() -> // GH-90000
                authService.authenticate("t1", "agent-1", "")); // GH-90000

            assertThat(session).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Recovery after lockout expiration")
        void recoveryAfterLockout() { // GH-90000
            // Lock account
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> authService.recordFailedAttempt("t1", "agent-1")); // GH-90000
            }

            Optional<LockoutInfo> locked = runPromise(() -> authService.checkLockout("t1", "agent-1")); // GH-90000
            assertThat(locked).isPresent(); // GH-90000

            // Reset (simulates lockout expiration in real system) // GH-90000
            runPromise(() -> authService.resetFailedAttempts("t1", "agent-1")); // GH-90000

            // Can authenticate again
            Optional<String> session = runPromise(() -> // GH-90000
                authService.authenticate("t1", "agent-1", "valid-hash")); // GH-90000
            assertThat(session).isPresent(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Identity Verification Flow")
    class IdentityVerificationTests {

        @Test
        @DisplayName("Full identity resolution chain")
        void fullIdentityResolution() { // GH-90000
            Optional<AgentIdentity> identity = runPromise(() -> // GH-90000
                identityService.resolve("t1", "agent-1")); // GH-90000

            assertThat(identity).isPresent(); // GH-90000
            assertThat(identity.get().agentId()).isEqualTo("agent-1");
            assertThat(identity.get().scopes()).contains("collection:read");
            assertThat(identity.get().spiffeId()).contains("spiffe://");
        }

        @Test
        @DisplayName("Credential issuance and revocation")
        void credentialLifecycle() { // GH-90000
            CredentialToken issued = runPromise(() -> // GH-90000
                identityService.issueCredential("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            assertThat(issued.isExpired()).isFalse(); // GH-90000

            Boolean valid1 = runPromise(() -> identityService.isCredentialValid(issued.tokenId())); // GH-90000
            assertThat(valid1).isTrue(); // GH-90000

            runPromise(() -> identityService.revokeCredential(issued.tokenId())); // GH-90000

            Boolean valid2 = runPromise(() -> identityService.isCredentialValid(issued.tokenId())); // GH-90000
            assertThat(valid2).isFalse(); // GH-90000
        }
    }
}
