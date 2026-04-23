/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Phase 1 — Security Hardening: Comprehensive token lifecycle tests for SecureTokenManager.
 * Covers token generation, validation, rotation, revocation, session management, and metrics.
 */
package com.ghatana.datacloud.security;

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for SecureTokenManager covering all token lifecycle operations
 * with correctness guarantees: generation, validation, rotation, revocation, session
 * concurrency limits, and security metrics accuracy.
 *
 * @doc.type test
 * @doc.purpose Verify token lifecycle correctness including generation, validation, rotation, revocation, and session management
 * @doc.layer security
 * @doc.pattern UnitTest
 */
@DisplayName("SecureTokenManager")
class SecureTokenManagerTest {

    private SecureTokenManager manager;

    @BeforeEach
    void setUp() { // GH-90000
        manager = new SecureTokenManager(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        manager.shutdown(); // GH-90000
    }

    // =========================================================================
    // Token Generation
    // =========================================================================

    @Nested
    @DisplayName("Token Generation")
    class TokenGeneration {

        @Test
        @DisplayName("generates a non-null token with well-formed result")
        void generatesNonNullToken() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getToken()).isNotNull().isNotBlank(); // GH-90000
            assertThat(result.getTokenId()).isNotNull().isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("generated token contains two base64url parts separated by a dot")
        void tokenHasCorrectStructure() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            String[] parts = result.getToken().split("\\.");
            assertThat(parts).hasSizeGreaterThanOrEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("each call generates a unique tokenId")
        void eachCallProducesUniqueTokenId() { // GH-90000
            SecureTokenManager.TokenResult first = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            SecureTokenManager.TokenResult second = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            assertThat(first.getTokenId()).isNotEqualTo(second.getTokenId()); // GH-90000
        }

        @Test
        @DisplayName("expiration is approximately one hour from now")
        void expirationIsOneHourFromNow() { // GH-90000
            Instant before = Instant.now(); // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            Instant after = Instant.now(); // GH-90000

            assertThat(result.getExpiration()) // GH-90000
                .isAfterOrEqualTo(before.plus(Duration.ofHours(1)).minusSeconds(1)) // GH-90000
                .isBeforeOrEqualTo(after.plus(Duration.ofHours(1)).plusSeconds(1)); // GH-90000
        }

        @Test
        @DisplayName("lifetime reported as one hour")
        void lifetimeIsOneHour() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            assertThat(result.getLifetime()).isEqualTo(Duration.ofHours(1)); // GH-90000
        }

        @Test
        @DisplayName("session binding is exposed in the result")
        void sessionBindingIsExposed() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            assertThat(result.getSessionBinding()).isNotNull().isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("session binding is unique per generated token")
        void sessionBindingIsUniquePerToken() { // GH-90000
            SecureTokenManager.TokenResult r1 = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            SecureTokenManager.TokenResult r2 = manager.generateToken("user-2", "tenant-1", Map.of()); // GH-90000

            assertThat(r1.getSessionBinding()).isNotEqualTo(r2.getSessionBinding()); // GH-90000
        }

        @Test
        @DisplayName("tokensIssued metric increments on each successful generation")
        void tokensIssuedIncrements() { // GH-90000
            manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            assertThat(manager.getSecurityMetrics().getTokensIssued()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("activeTokens metric reflects live token count")
        void activeTokensReflectsCount() { // GH-90000
            manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            manager.generateToken("user-2", "tenant-1", Map.of()); // GH-90000

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("throws SecurityException when session limit of 5 is exceeded")
        void throwsOnExceededSessionLimit() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                manager.generateToken("user-limited", "tenant-1", Map.of()); // GH-90000
            }

            assertThatThrownBy(() -> manager.generateToken("user-limited", "tenant-1", Map.of())) // GH-90000
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("concurrent sessions");
        }

        @Test
        @DisplayName("different users are tracked independently for session limits")
        void differentUsersHaveIndependentSessionLimits() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                manager.generateToken("user-a", "tenant-1", Map.of()); // GH-90000
            }

            // user-b is unaffected by user-a's session count
            assertThatCode(() -> manager.generateToken("user-b", "tenant-1", Map.of())) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // Token Validation
    // =========================================================================

    @Nested
    @DisplayName("Token Validation")
    class TokenValidation {

        @Test
        @DisplayName("valid token with correct session binding validates successfully")
        void validTokenValidatesSuccessfully() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(result.getToken(), result.getSessionBinding()); // GH-90000

            assertThat(opt).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("validated metadata reflects the original user and tenant")
        void validatedMetadataReflectsUserAndTenant() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-42", "tenant-99", Map.of("role", "admin")); // GH-90000

            SecureTokenManager.TokenMetadata meta =
                manager.validateToken(result.getToken(), result.getSessionBinding()).orElseThrow(); // GH-90000

            assertThat(meta.getUserId()).isEqualTo("user-42");
            assertThat(meta.getTenantId()).isEqualTo("tenant-99");
        }

        @Test
        @DisplayName("wrong session binding returns empty")
        void wrongSessionBindingReturnsEmpty() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(result.getToken(), "wrong-binding"); // GH-90000

            assertThat(opt).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("malformed token string returns empty")
        void malformedTokenReturnsEmpty() { // GH-90000
            assertThat(manager.validateToken("not-a-valid-token", "any")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("empty token string returns empty")
        void emptyTokenReturnsEmpty() { // GH-90000
            assertThat(manager.validateToken("", "any")).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("revoked token returns empty even with correct session binding")
        void revokedTokenReturnsEmpty() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            manager.revokeToken(result.getTokenId(), "test revocation"); // GH-90000

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(result.getToken(), result.getSessionBinding()); // GH-90000

            assertThat(opt).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Token Rotation
    // =========================================================================

    @Nested
    @DisplayName("Token Rotation")
    class TokenRotation {

        @Test
        @DisplayName("successful rotation returns a new token")
        void rotationReturnsNewToken() { // GH-90000
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            SecureTokenManager.TokenResult rotated =
                manager.rotateToken(original.getToken(), original.getSessionBinding()); // GH-90000

            assertThat(rotated).isNotNull(); // GH-90000
            assertThat(rotated.getTokenId()).isNotEqualTo(original.getTokenId()); // GH-90000
        }

        @Test
        @DisplayName("rotation increments tokensRotated metric")
        void rotationIncrementsMetric() { // GH-90000
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            manager.rotateToken(original.getToken(), original.getSessionBinding()); // GH-90000

            assertThat(manager.getSecurityMetrics().getTokensRotated()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("rotation issues a new token with fresh expiration")
        void rotationIssuesFreshExpiration() { // GH-90000
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            Instant beforeRotation = Instant.now(); // GH-90000

            SecureTokenManager.TokenResult rotated =
                manager.rotateToken(original.getToken(), original.getSessionBinding()); // GH-90000

            assertThat(rotated.getExpiration()).isAfter(beforeRotation.plus(Duration.ofMinutes(59))); // GH-90000
        }

        @Test
        @DisplayName("rotation of invalid token throws SecurityException and increments failure metric")
        void rotationOfInvalidTokenThrowsAndIncrementFailures() { // GH-90000
            assertThatThrownBy(() -> manager.rotateToken("invalid.token", "any-binding")) // GH-90000
                .isInstanceOf(SecurityException.class); // GH-90000

            assertThat(manager.getSecurityMetrics().getRotationFailures()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("rotated token is validated by new session binding")
        void rotatedTokenValidatedByNewBinding() { // GH-90000
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            SecureTokenManager.TokenResult rotated =
                manager.rotateToken(original.getToken(), original.getSessionBinding()); // GH-90000

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(rotated.getToken(), rotated.getSessionBinding()); // GH-90000

            assertThat(opt).isPresent(); // GH-90000
        }
    }

    // =========================================================================
    // Token Revocation
    // =========================================================================

    @Nested
    @DisplayName("Token Revocation")
    class TokenRevocation {

        @Test
        @DisplayName("revokeToken removes token from active set")
        void revokeRemovesFromActive() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            long activeBefore = manager.getSecurityMetrics().getActiveTokens(); // GH-90000

            manager.revokeToken(result.getTokenId(), "test"); // GH-90000

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(activeBefore - 1); // GH-90000
        }

        @Test
        @DisplayName("revokeToken increments tokensRevoked metric")
        void revokeIncrementsMetric() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            manager.revokeToken(result.getTokenId(), "test"); // GH-90000

            assertThat(manager.getSecurityMetrics().getTokensRevoked()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("revokeToken removes the user session")
        void revokeRemovesUserSession() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            manager.revokeToken(result.getTokenId(), "test"); // GH-90000

            assertThat(manager.getUserActiveSessions("user-1")).doesNotContain(result.getTokenId());
        }

        @Test
        @DisplayName("revoking non-existent tokenId is safe (no exception)")
        void revokeNonExistentIsSafe() { // GH-90000
            assertThatCode(() -> manager.revokeToken("non-existent-id", "test")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("revokeAllUserTokens revokes all tokens for that user")
        void revokeAllUserTokensRevokesAll() { // GH-90000
            manager.generateToken("user-bulk", "tenant-1", Map.of()); // GH-90000
            manager.generateToken("user-bulk", "tenant-1", Map.of()); // GH-90000

            manager.revokeAllUserTokens("user-bulk", "bulk revocation"); // GH-90000

            assertThat(manager.getUserActiveSessions("user-bulk")).isEmpty();
        }

        @Test
        @DisplayName("revokeAllUserTokens does not affect other users' tokens")
        void revokeAllUserTokensDoesNotAffectOthers() { // GH-90000
            manager.generateToken("user-keep", "tenant-1", Map.of()); // GH-90000
            manager.generateToken("user-remove", "tenant-1", Map.of()); // GH-90000

            manager.revokeAllUserTokens("user-remove", "test"); // GH-90000

            assertThat(manager.getUserActiveSessions("user-keep")).isNotEmpty();
        }

        @Test
        @DisplayName("revokeAllTenantTokens revokes all tokens for that tenant")
        void revokeAllTenantTokensRevokesAll() { // GH-90000
            manager.generateToken("user-a", "tenant-bulk", Map.of()); // GH-90000
            manager.generateToken("user-b", "tenant-bulk", Map.of()); // GH-90000

            manager.revokeAllTenantTokens("tenant-bulk", "tenant shutdown"); // GH-90000

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("revokeAllTenantTokens does not affect other tenants' tokens")
        void revokeAllTenantTokensDoesNotAffectOtherTenants() { // GH-90000
            manager.generateToken("user-a", "tenant-keep", Map.of()); // GH-90000
            manager.generateToken("user-b", "tenant-remove", Map.of()); // GH-90000
            long keepable = 1;

            manager.revokeAllTenantTokens("tenant-remove", "test"); // GH-90000

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(keepable); // GH-90000
        }

        @Test
        @DisplayName("revokeAllUserTokens on unknown user is safe (no exception)")
        void revokeAllForUnknownUserIsSafe() { // GH-90000
            assertThatCode(() -> manager.revokeAllUserTokens("no-such-user", "test")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // Session Management
    // =========================================================================

    @Nested
    @DisplayName("Session Management")
    class SessionManagement {

        @Test
        @DisplayName("getUserActiveSessions returns empty set for unknown user")
        void emptySetForUnknownUser() { // GH-90000
            assertThat(manager.getUserActiveSessions("nobody")).isEmpty();
        }

        @Test
        @DisplayName("getUserActiveSessions contains the tokenId after generation")
        void containsTokenIdAfterGeneration() { // GH-90000
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            assertThat(manager.getUserActiveSessions("user-1")).contains(result.getTokenId());
        }

        @Test
        @DisplayName("getUserActiveSessions shrinks after revocation")
        void shrinksAfterRevocation() { // GH-90000
            SecureTokenManager.TokenResult first = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000
            SecureTokenManager.TokenResult second = manager.generateToken("user-1", "tenant-1", Map.of()); // GH-90000

            manager.revokeToken(first.getTokenId(), "test"); // GH-90000

            Set<String> sessions = manager.getUserActiveSessions("user-1");
            assertThat(sessions).doesNotContain(first.getTokenId()); // GH-90000
            assertThat(sessions).contains(second.getTokenId()); // GH-90000
        }

        @Test
        @DisplayName("session count decrements after revokeAllUserTokens")
        void sessionCountDecrementsAfterRevokeAll() { // GH-90000
            manager.generateToken("user-multi", "tenant-1", Map.of()); // GH-90000
            manager.generateToken("user-multi", "tenant-1", Map.of()); // GH-90000
            manager.generateToken("user-multi", "tenant-1", Map.of()); // GH-90000

            manager.revokeAllUserTokens("user-multi", "test"); // GH-90000

            assertThat(manager.getUserActiveSessions("user-multi")).isEmpty();
        }
    }

    // =========================================================================
    // Should Rotate Token
    // =========================================================================

    @Nested
    @DisplayName("shouldRotateToken")
    class ShouldRotateToken {

        @Test
        @DisplayName("returns false when token has more than 15 minutes remaining")
        void falseWhenPlentyOfTimeLeft() { // GH-90000
            Instant issuedAt = Instant.now(); // GH-90000
            Instant expiration = issuedAt.plus(Duration.ofHours(1)); // GH-90000

            SecureTokenManager.TokenMetadata meta = new SecureTokenManager.TokenMetadata( // GH-90000
                "tid", "uid", "tnid", "raw.token", issuedAt, expiration, Map.of(), "sb" // GH-90000
            );

            assertThat(manager.shouldRotateToken(meta)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("returns true when token expires within 15 minutes")
        void trueWhenWithinRotationWindow() { // GH-90000
            Instant issuedAt = Instant.now().minus(Duration.ofMinutes(50)); // GH-90000
            Instant expiration = Instant.now().plus(Duration.ofMinutes(10)); // GH-90000

            SecureTokenManager.TokenMetadata meta = new SecureTokenManager.TokenMetadata( // GH-90000
                "tid", "uid", "tnid", "raw.token", issuedAt, expiration, Map.of(), "sb" // GH-90000
            );

            assertThat(manager.shouldRotateToken(meta)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns true when token expires in exactly 15 minutes (boundary)")
        void trueAtRotationWindowBoundary() { // GH-90000
            Instant expiration = Instant.now().plus(Duration.ofMinutes(15)); // GH-90000

            SecureTokenManager.TokenMetadata meta = new SecureTokenManager.TokenMetadata( // GH-90000
                "tid", "uid", "tnid", "raw.token", Instant.now().minus(Duration.ofMinutes(45)), // GH-90000
                expiration, Map.of(), "sb" // GH-90000
            );

            assertThat(manager.shouldRotateToken(meta)).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Security Metrics
    // =========================================================================

    @Nested
    @DisplayName("Security Metrics")
    class SecurityMetricsTests {

        @Test
        @DisplayName("initial metrics are all zero")
        void initialMetricsAreZero() { // GH-90000
            SecureTokenManager.SecurityMetrics metrics = manager.getSecurityMetrics(); // GH-90000

            assertThat(metrics.getTokensIssued()).isZero(); // GH-90000
            assertThat(metrics.getTokensRevoked()).isZero(); // GH-90000
            assertThat(metrics.getTokensRotated()).isZero(); // GH-90000
            assertThat(metrics.getRotationFailures()).isZero(); // GH-90000
            assertThat(metrics.getActiveTokens()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("metrics accurately reflect a sequence of operations")
        void metricsAccurateAfterSequence() { // GH-90000
            SecureTokenManager.TokenResult r1 = manager.generateToken("user-1", "t1", Map.of()); // GH-90000
            SecureTokenManager.TokenResult r2 = manager.generateToken("user-2", "t1", Map.of()); // GH-90000
            manager.revokeToken(r1.getTokenId(), "test"); // GH-90000
            manager.rotateToken(r2.getToken(), r2.getSessionBinding()); // GH-90000

            SecureTokenManager.SecurityMetrics metrics = manager.getSecurityMetrics(); // GH-90000

            assertThat(metrics.getTokensIssued()).isGreaterThanOrEqualTo(3); // 2 original + 1 rotated // GH-90000
            assertThat(metrics.getTokensRevoked()).isGreaterThanOrEqualTo(1); // GH-90000
            assertThat(metrics.getTokensRotated()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("getRotationSuccessRate returns 100% when all rotations succeed")
        void rotationSuccessRateIs100WhenAllSucceed() { // GH-90000
            SecureTokenManager.TokenResult r = manager.generateToken("user-1", "t1", Map.of()); // GH-90000
            manager.rotateToken(r.getToken(), r.getSessionBinding()); // GH-90000

            assertThat(manager.getSecurityMetrics().getRotationSuccessRate()).isEqualTo(100.0); // GH-90000
        }

        @Test
        @DisplayName("getRotationSuccessRate is 0% when there are only failures")
        void rotationSuccessRateIs0WhenAllFail() { // GH-90000
            try { manager.rotateToken("invalid.token.here", "bad"); } catch (SecurityException ignored) {} // GH-90000

            assertThat(manager.getSecurityMetrics().getRotationSuccessRate()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("getRotationSuccessRate returns 0 when no rotations attempted")
        void rotationSuccessRateIsZeroWhenNoneAttempted() { // GH-90000
            assertThat(manager.getSecurityMetrics().getRotationSuccessRate()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("activeUsers metric counts users with at least one active token")
        void activeUsersCountsDistinctUsers() { // GH-90000
            manager.generateToken("user-x", "t1", Map.of()); // GH-90000
            manager.generateToken("user-y", "t1", Map.of()); // GH-90000
            manager.generateToken("user-x", "t1", Map.of()); // second token for same user // GH-90000

            assertThat(manager.getSecurityMetrics().getActiveUsers()).isEqualTo(2); // GH-90000
        }
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    @Nested
    @DisplayName("Shutdown")
    class Shutdown {

        @Test
        @DisplayName("shutdown completes without exception")
        void shutdownDoesNotThrow() { // GH-90000
            assertThatCode(() -> manager.shutdown()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("multiple shutdown calls are idempotent")
        void multipleShutdownCallsAreSafe() { // GH-90000
            assertThatCode(() -> { // GH-90000
                manager.shutdown(); // GH-90000
                manager.shutdown(); // GH-90000
            }).doesNotThrowAnyException(); // GH-90000
        }
    }
}
