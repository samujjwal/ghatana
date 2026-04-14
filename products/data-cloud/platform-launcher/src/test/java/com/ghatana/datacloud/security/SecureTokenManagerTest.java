/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
    void setUp() {
        manager = new SecureTokenManager();
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    // =========================================================================
    // Token Generation
    // =========================================================================

    @Nested
    @DisplayName("Token Generation")
    class TokenGeneration {

        @Test
        @DisplayName("generates a non-null token with well-formed result")
        void generatesNonNullToken() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            assertThat(result).isNotNull();
            assertThat(result.getToken()).isNotNull().isNotBlank();
            assertThat(result.getTokenId()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("generated token contains two base64url parts separated by a dot")
        void tokenHasCorrectStructure() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            String[] parts = result.getToken().split("\\.");
            assertThat(parts).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("each call generates a unique tokenId")
        void eachCallProducesUniqueTokenId() {
            SecureTokenManager.TokenResult first = manager.generateToken("user-1", "tenant-1", Map.of());
            SecureTokenManager.TokenResult second = manager.generateToken("user-1", "tenant-1", Map.of());

            assertThat(first.getTokenId()).isNotEqualTo(second.getTokenId());
        }

        @Test
        @DisplayName("expiration is approximately one hour from now")
        void expirationIsOneHourFromNow() {
            Instant before = Instant.now();
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());
            Instant after = Instant.now();

            assertThat(result.getExpiration())
                .isAfterOrEqualTo(before.plus(Duration.ofHours(1)).minusSeconds(1))
                .isBeforeOrEqualTo(after.plus(Duration.ofHours(1)).plusSeconds(1));
        }

        @Test
        @DisplayName("lifetime reported as one hour")
        void lifetimeIsOneHour() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            assertThat(result.getLifetime()).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("session binding is exposed in the result")
        void sessionBindingIsExposed() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            assertThat(result.getSessionBinding()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("session binding is unique per generated token")
        void sessionBindingIsUniquePerToken() {
            SecureTokenManager.TokenResult r1 = manager.generateToken("user-1", "tenant-1", Map.of());
            SecureTokenManager.TokenResult r2 = manager.generateToken("user-2", "tenant-1", Map.of());

            assertThat(r1.getSessionBinding()).isNotEqualTo(r2.getSessionBinding());
        }

        @Test
        @DisplayName("tokensIssued metric increments on each successful generation")
        void tokensIssuedIncrements() {
            manager.generateToken("user-1", "tenant-1", Map.of());
            manager.generateToken("user-1", "tenant-1", Map.of());

            assertThat(manager.getSecurityMetrics().getTokensIssued()).isEqualTo(2);
        }

        @Test
        @DisplayName("activeTokens metric reflects live token count")
        void activeTokensReflectsCount() {
            manager.generateToken("user-1", "tenant-1", Map.of());
            manager.generateToken("user-2", "tenant-1", Map.of());

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(2);
        }

        @Test
        @DisplayName("throws SecurityException when session limit of 5 is exceeded")
        void throwsOnExceededSessionLimit() {
            for (int i = 0; i < 5; i++) {
                manager.generateToken("user-limited", "tenant-1", Map.of());
            }

            assertThatThrownBy(() -> manager.generateToken("user-limited", "tenant-1", Map.of()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("concurrent sessions");
        }

        @Test
        @DisplayName("different users are tracked independently for session limits")
        void differentUsersHaveIndependentSessionLimits() {
            for (int i = 0; i < 5; i++) {
                manager.generateToken("user-a", "tenant-1", Map.of());
            }

            // user-b is unaffected by user-a's session count
            assertThatCode(() -> manager.generateToken("user-b", "tenant-1", Map.of()))
                .doesNotThrowAnyException();
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
        void validTokenValidatesSuccessfully() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(result.getToken(), result.getSessionBinding());

            assertThat(opt).isPresent();
        }

        @Test
        @DisplayName("validated metadata reflects the original user and tenant")
        void validatedMetadataReflectsUserAndTenant() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-42", "tenant-99", Map.of("role", "admin"));

            SecureTokenManager.TokenMetadata meta =
                manager.validateToken(result.getToken(), result.getSessionBinding()).orElseThrow();

            assertThat(meta.getUserId()).isEqualTo("user-42");
            assertThat(meta.getTenantId()).isEqualTo("tenant-99");
        }

        @Test
        @DisplayName("wrong session binding returns empty")
        void wrongSessionBindingReturnsEmpty() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(result.getToken(), "wrong-binding");

            assertThat(opt).isEmpty();
        }

        @Test
        @DisplayName("malformed token string returns empty")
        void malformedTokenReturnsEmpty() {
            assertThat(manager.validateToken("not-a-valid-token", "any")).isEmpty();
        }

        @Test
        @DisplayName("empty token string returns empty")
        void emptyTokenReturnsEmpty() {
            assertThat(manager.validateToken("", "any")).isEmpty();
        }

        @Test
        @DisplayName("revoked token returns empty even with correct session binding")
        void revokedTokenReturnsEmpty() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());
            manager.revokeToken(result.getTokenId(), "test revocation");

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(result.getToken(), result.getSessionBinding());

            assertThat(opt).isEmpty();
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
        void rotationReturnsNewToken() {
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of());

            SecureTokenManager.TokenResult rotated =
                manager.rotateToken(original.getToken(), original.getSessionBinding());

            assertThat(rotated).isNotNull();
            assertThat(rotated.getTokenId()).isNotEqualTo(original.getTokenId());
        }

        @Test
        @DisplayName("rotation increments tokensRotated metric")
        void rotationIncrementsMetric() {
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of());
            manager.rotateToken(original.getToken(), original.getSessionBinding());

            assertThat(manager.getSecurityMetrics().getTokensRotated()).isEqualTo(1);
        }

        @Test
        @DisplayName("rotation issues a new token with fresh expiration")
        void rotationIssuesFreshExpiration() {
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of());
            Instant beforeRotation = Instant.now();

            SecureTokenManager.TokenResult rotated =
                manager.rotateToken(original.getToken(), original.getSessionBinding());

            assertThat(rotated.getExpiration()).isAfter(beforeRotation.plus(Duration.ofMinutes(59)));
        }

        @Test
        @DisplayName("rotation of invalid token throws SecurityException and increments failure metric")
        void rotationOfInvalidTokenThrowsAndIncrementFailures() {
            assertThatThrownBy(() -> manager.rotateToken("invalid.token", "any-binding"))
                .isInstanceOf(SecurityException.class);

            assertThat(manager.getSecurityMetrics().getRotationFailures()).isEqualTo(1);
        }

        @Test
        @DisplayName("rotated token is validated by new session binding")
        void rotatedTokenValidatedByNewBinding() {
            SecureTokenManager.TokenResult original = manager.generateToken("user-1", "tenant-1", Map.of());
            SecureTokenManager.TokenResult rotated =
                manager.rotateToken(original.getToken(), original.getSessionBinding());

            Optional<SecureTokenManager.TokenMetadata> opt =
                manager.validateToken(rotated.getToken(), rotated.getSessionBinding());

            assertThat(opt).isPresent();
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
        void revokeRemovesFromActive() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());
            long activeBefore = manager.getSecurityMetrics().getActiveTokens();

            manager.revokeToken(result.getTokenId(), "test");

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(activeBefore - 1);
        }

        @Test
        @DisplayName("revokeToken increments tokensRevoked metric")
        void revokeIncrementsMetric() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());
            manager.revokeToken(result.getTokenId(), "test");

            assertThat(manager.getSecurityMetrics().getTokensRevoked()).isEqualTo(1);
        }

        @Test
        @DisplayName("revokeToken removes the user session")
        void revokeRemovesUserSession() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());
            manager.revokeToken(result.getTokenId(), "test");

            assertThat(manager.getUserActiveSessions("user-1")).doesNotContain(result.getTokenId());
        }

        @Test
        @DisplayName("revoking non-existent tokenId is safe (no exception)")
        void revokeNonExistentIsSafe() {
            assertThatCode(() -> manager.revokeToken("non-existent-id", "test"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("revokeAllUserTokens revokes all tokens for that user")
        void revokeAllUserTokensRevokesAll() {
            manager.generateToken("user-bulk", "tenant-1", Map.of());
            manager.generateToken("user-bulk", "tenant-1", Map.of());

            manager.revokeAllUserTokens("user-bulk", "bulk revocation");

            assertThat(manager.getUserActiveSessions("user-bulk")).isEmpty();
        }

        @Test
        @DisplayName("revokeAllUserTokens does not affect other users' tokens")
        void revokeAllUserTokensDoesNotAffectOthers() {
            manager.generateToken("user-keep", "tenant-1", Map.of());
            manager.generateToken("user-remove", "tenant-1", Map.of());

            manager.revokeAllUserTokens("user-remove", "test");

            assertThat(manager.getUserActiveSessions("user-keep")).isNotEmpty();
        }

        @Test
        @DisplayName("revokeAllTenantTokens revokes all tokens for that tenant")
        void revokeAllTenantTokensRevokesAll() {
            manager.generateToken("user-a", "tenant-bulk", Map.of());
            manager.generateToken("user-b", "tenant-bulk", Map.of());

            manager.revokeAllTenantTokens("tenant-bulk", "tenant shutdown");

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(0);
        }

        @Test
        @DisplayName("revokeAllTenantTokens does not affect other tenants' tokens")
        void revokeAllTenantTokensDoesNotAffectOtherTenants() {
            manager.generateToken("user-a", "tenant-keep", Map.of());
            manager.generateToken("user-b", "tenant-remove", Map.of());
            long keepable = 1;

            manager.revokeAllTenantTokens("tenant-remove", "test");

            assertThat(manager.getSecurityMetrics().getActiveTokens()).isEqualTo(keepable);
        }

        @Test
        @DisplayName("revokeAllUserTokens on unknown user is safe (no exception)")
        void revokeAllForUnknownUserIsSafe() {
            assertThatCode(() -> manager.revokeAllUserTokens("no-such-user", "test"))
                .doesNotThrowAnyException();
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
        void emptySetForUnknownUser() {
            assertThat(manager.getUserActiveSessions("nobody")).isEmpty();
        }

        @Test
        @DisplayName("getUserActiveSessions contains the tokenId after generation")
        void containsTokenIdAfterGeneration() {
            SecureTokenManager.TokenResult result = manager.generateToken("user-1", "tenant-1", Map.of());

            assertThat(manager.getUserActiveSessions("user-1")).contains(result.getTokenId());
        }

        @Test
        @DisplayName("getUserActiveSessions shrinks after revocation")
        void shrinksAfterRevocation() {
            SecureTokenManager.TokenResult first = manager.generateToken("user-1", "tenant-1", Map.of());
            SecureTokenManager.TokenResult second = manager.generateToken("user-1", "tenant-1", Map.of());

            manager.revokeToken(first.getTokenId(), "test");

            Set<String> sessions = manager.getUserActiveSessions("user-1");
            assertThat(sessions).doesNotContain(first.getTokenId());
            assertThat(sessions).contains(second.getTokenId());
        }

        @Test
        @DisplayName("session count decrements after revokeAllUserTokens")
        void sessionCountDecrementsAfterRevokeAll() {
            manager.generateToken("user-multi", "tenant-1", Map.of());
            manager.generateToken("user-multi", "tenant-1", Map.of());
            manager.generateToken("user-multi", "tenant-1", Map.of());

            manager.revokeAllUserTokens("user-multi", "test");

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
        void falseWhenPlentyOfTimeLeft() {
            Instant issuedAt = Instant.now();
            Instant expiration = issuedAt.plus(Duration.ofHours(1));

            SecureTokenManager.TokenMetadata meta = new SecureTokenManager.TokenMetadata(
                "tid", "uid", "tnid", "raw.token", issuedAt, expiration, Map.of(), "sb"
            );

            assertThat(manager.shouldRotateToken(meta)).isFalse();
        }

        @Test
        @DisplayName("returns true when token expires within 15 minutes")
        void trueWhenWithinRotationWindow() {
            Instant issuedAt = Instant.now().minus(Duration.ofMinutes(50));
            Instant expiration = Instant.now().plus(Duration.ofMinutes(10));

            SecureTokenManager.TokenMetadata meta = new SecureTokenManager.TokenMetadata(
                "tid", "uid", "tnid", "raw.token", issuedAt, expiration, Map.of(), "sb"
            );

            assertThat(manager.shouldRotateToken(meta)).isTrue();
        }

        @Test
        @DisplayName("returns true when token expires in exactly 15 minutes (boundary)")
        void trueAtRotationWindowBoundary() {
            Instant expiration = Instant.now().plus(Duration.ofMinutes(15));

            SecureTokenManager.TokenMetadata meta = new SecureTokenManager.TokenMetadata(
                "tid", "uid", "tnid", "raw.token", Instant.now().minus(Duration.ofMinutes(45)),
                expiration, Map.of(), "sb"
            );

            assertThat(manager.shouldRotateToken(meta)).isTrue();
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
        void initialMetricsAreZero() {
            SecureTokenManager.SecurityMetrics metrics = manager.getSecurityMetrics();

            assertThat(metrics.getTokensIssued()).isZero();
            assertThat(metrics.getTokensRevoked()).isZero();
            assertThat(metrics.getTokensRotated()).isZero();
            assertThat(metrics.getRotationFailures()).isZero();
            assertThat(metrics.getActiveTokens()).isZero();
        }

        @Test
        @DisplayName("metrics accurately reflect a sequence of operations")
        void metricsAccurateAfterSequence() {
            SecureTokenManager.TokenResult r1 = manager.generateToken("user-1", "t1", Map.of());
            SecureTokenManager.TokenResult r2 = manager.generateToken("user-2", "t1", Map.of());
            manager.revokeToken(r1.getTokenId(), "test");
            manager.rotateToken(r2.getToken(), r2.getSessionBinding());

            SecureTokenManager.SecurityMetrics metrics = manager.getSecurityMetrics();

            assertThat(metrics.getTokensIssued()).isGreaterThanOrEqualTo(3); // 2 original + 1 rotated
            assertThat(metrics.getTokensRevoked()).isGreaterThanOrEqualTo(1);
            assertThat(metrics.getTokensRotated()).isEqualTo(1);
        }

        @Test
        @DisplayName("getRotationSuccessRate returns 100% when all rotations succeed")
        void rotationSuccessRateIs100WhenAllSucceed() {
            SecureTokenManager.TokenResult r = manager.generateToken("user-1", "t1", Map.of());
            manager.rotateToken(r.getToken(), r.getSessionBinding());

            assertThat(manager.getSecurityMetrics().getRotationSuccessRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("getRotationSuccessRate is 0% when there are only failures")
        void rotationSuccessRateIs0WhenAllFail() {
            try { manager.rotateToken("invalid.token.here", "bad"); } catch (SecurityException ignored) {}

            assertThat(manager.getSecurityMetrics().getRotationSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getRotationSuccessRate returns 0 when no rotations attempted")
        void rotationSuccessRateIsZeroWhenNoneAttempted() {
            assertThat(manager.getSecurityMetrics().getRotationSuccessRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("activeUsers metric counts users with at least one active token")
        void activeUsersCountsDistinctUsers() {
            manager.generateToken("user-x", "t1", Map.of());
            manager.generateToken("user-y", "t1", Map.of());
            manager.generateToken("user-x", "t1", Map.of()); // second token for same user

            assertThat(manager.getSecurityMetrics().getActiveUsers()).isEqualTo(2);
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
        void shutdownDoesNotThrow() {
            assertThatCode(() -> manager.shutdown()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("multiple shutdown calls are idempotent")
        void multipleShutdownCallsAreSafe() {
            assertThatCode(() -> {
                manager.shutdown();
                manager.shutdown();
            }).doesNotThrowAnyException();
        }
    }
}
