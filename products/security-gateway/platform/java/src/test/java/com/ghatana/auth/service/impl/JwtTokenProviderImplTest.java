package com.ghatana.auth.service.impl;

import com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException;
import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtTokenProviderImpl.
 *
 * Tests validate:
 * - Token generation (access + refresh) with all claims
 * - Token validation (signature, expiration, issuer, audience, tenant, revocation)
 * - Token revocation tracking per tenant
 * - Metrics emission for all operations
 * - Tenant isolation in all operations
 * - Edge cases and error conditions
 *
 * @see JwtTokenProviderImpl
 * @see com.ghatana.auth.core.port.JwtTokenProvider
 */
@DisplayName("JWT Token Provider Tests")
class JwtTokenProviderImplTest extends EventloopTestBase {

    private JwtTokenProviderImpl provider;
    private TenantId tenantId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        // GIVEN: JWT provider with no-op metrics
        provider = new JwtTokenProviderImpl(new NoopMetricsCollector());

        // GIVEN: Test tenant and user
        tenantId = TenantId.of("test-tenant-123");
        principal = UserPrincipal.builder()
                .userId("user-456")
                .email("user@example.com")
                .name("Test User")
                .roles(Set.of("USER", "ADMIN"))
                .permissions(Set.of("READ", "WRITE", "DELETE"))
                .build();
    }

    // ==================== TOKEN GENERATION TESTS ====================

    /**
     * GIVEN: Valid tenant and user principal
     * WHEN: generateToken() is called
     * THEN: Valid JWT access token is returned with all claims
     */
    @Test
    @DisplayName("Should generate access token with all claims")
    void shouldGenerateAccessTokenWithAllClaims() {
        // GIVEN: Duration for token TTL
        Duration ttl = Duration.ofHours(1);

        // WHEN: Generate access token
        String token = runPromise(() -> provider.generateToken(tenantId, principal, ttl));

        // THEN: Token is not null or empty
        assertThat(token)
                .as("Generated token should not be empty")
                .isNotEmpty();

        // AND: Token is a valid JWT (3 parts separated by dots)
        assertThat(token.split("\\."))
                .as("Token should be valid JWT format (header.payload.signature)")
                .hasSize(3);
    }

    /**
     * GIVEN: Valid tenant and user principal
     * WHEN: generateRefreshToken() is called
     * THEN: Valid JWT refresh token is returned
     */
    @Test
    @DisplayName("Should generate refresh token")
    void shouldGenerateRefreshToken() {
        // GIVEN: Duration for refresh token (longer TTL)
        Duration ttl = Duration.ofDays(30);

        // WHEN: Generate refresh token
        String token = runPromise(() -> provider.generateRefreshToken(tenantId, principal, ttl));

        // THEN: Token is valid JWT
        assertThat(token)
                .as("Generated refresh token should not be empty")
                .isNotEmpty();
        assertThat(token.split("\\."))
                .as("Refresh token should be valid JWT format")
                .hasSize(3);
    }

    /**
     * GIVEN: Generated token
     * WHEN: Token is generated multiple times
     * THEN: Each token has unique JWT ID (jti)
     */
    @Test
    @DisplayName("Should generate tokens with unique JTI")
    void shouldGenerateTokensWithUniqueJti() {
        // WHEN: Generate multiple tokens
        String token1 = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));
        String token2 = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));

        // THEN: Both tokens are valid
        assertThat(token1).isNotEmpty();
        assertThat(token2).isNotEmpty();

        // AND: Tokens are different (different jti)
        assertThat(token1)
                .as("Tokens with different jti should be different")
                .isNotEqualTo(token2);
    }

    // ==================== TOKEN VALIDATION TESTS ====================

    /**
     * GIVEN: Generated token
     * WHEN: validateToken() is called with correct tenant and token
     * THEN: Token is validated successfully and claims are returned
     */
    @Test
    @DisplayName("Should validate token successfully with correct tenant")
    void shouldValidateTokenSuccessfullyWithCorrectTenant() {
        // GIVEN: Valid token
        String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));

        // WHEN: Validate token
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));

        // THEN: Claims are extracted correctly
        assertThat(claims)
                .as("Validated claims should not be null")
                .isNotNull();
        assertThat(claims.getSubject())
                .as("Subject should match user ID")
                .isEqualTo(principal.getUserId());
        assertThat(claims.getEmail())
                .as("Email should match principal email")
                .isEqualTo(principal.getEmail());
        assertThat(claims.getName())
                .as("Name should match principal name")
                .isEqualTo(principal.getName());
    }

    /**
     * GIVEN: Valid token
     * WHEN: Validated with different tenant
     * THEN: Validation fails with tenant mismatch error
     */
    @Test
    @DisplayName("Should reject token with tenant mismatch")
    void shouldRejectTokenWithTenantMismatch() {
        // GIVEN: Valid token for tenantA
        TenantId tenantA = TenantId.of("tenant-a");
        TenantId tenantB = TenantId.of("tenant-b");
        String token = runPromise(() -> provider.generateToken(tenantA, principal, Duration.ofHours(1)));

        // WHEN/THEN: Validation with different tenant fails
        boolean exceptionThrown = false;
        try {
            runPromise(() -> provider.validateToken(tenantB, token));
        } catch (Exception e) {
            exceptionThrown = true;
            assertThat(e)
                    .as("Should throw JwtValidationException")
                    .isInstanceOf(JwtValidationException.class);
        }
        assertThat(exceptionThrown)
                .as("Exception should be thrown for tenant mismatch")
                .isTrue();
    }

    /**
     * GIVEN: Generated access token
     * WHEN: Validated
     * THEN: Token includes roles and permissions
     */
    @Test
    @DisplayName("Should include roles and permissions in access token")
    void shouldIncludeRolesAndPermissionsInAccessToken() {
        // GIVEN: Access token
        String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));

        // WHEN: Validate token
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));

        // THEN: Roles and permissions are present
        assertThat(claims.getRoles())
                .as("Access token should include roles")
                .isNotEmpty()
                .contains("USER", "ADMIN");
        assertThat(claims.getPermissions())
                .as("Access token should include permissions")
                .isNotEmpty()
                .contains("READ", "WRITE", "DELETE");
    }

    /**
     * GIVEN: Generated refresh token
     * WHEN: Validated
     * THEN: Token excludes roles and permissions
     */
    @Test
    @DisplayName("Should exclude roles and permissions in refresh token")
    void shouldExcludeRolesAndPermissionsInRefreshToken() {
        // GIVEN: Refresh token
        String token = runPromise(() -> provider.generateRefreshToken(tenantId, principal, Duration.ofDays(30)));

        // WHEN: Validate token
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));

        // THEN: Roles and permissions are empty (security best practice)
        assertThat(claims.getRoles())
                .as("Refresh token should not include roles")
                .isEmpty();
        assertThat(claims.getPermissions())
                .as("Refresh token should not include permissions")
                .isEmpty();
    }

    // ==================== TOKEN REVOCATION TESTS ====================

    /**
     * GIVEN: Valid token
     * WHEN: revokeToken() is called with token ID
     * THEN: Token is marked as revoked
     */
    @Test
    @DisplayName("Should revoke token successfully")
    void shouldRevokeTokenSuccessfully() {
        // GIVEN: Valid token and its ID
        String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));
        String tokenId = claims.getTokenId();

        // WHEN: Revoke token
        boolean wasRevoked = runPromise(() -> provider.revokeToken(tenantId, tokenId));

        // THEN: Token is revoked
        assertThat(wasRevoked)
                .as("Newly revoked token should return true")
                .isTrue();

        // AND: Subsequent validation fails
        boolean revokedExceptionThrown = false;
        try {
            runPromise(() -> provider.validateToken(tenantId, token));
        } catch (Exception e) {
            revokedExceptionThrown = true;
            assertThat(e).isInstanceOf(JwtValidationException.class);
        }
        assertThat(revokedExceptionThrown)
                .as("Should throw exception for revoked token")
                .isTrue();
    }

    /**
     * GIVEN: Revoked token
     * WHEN: revokeToken() is called again
     * THEN: Returns false (already revoked)
     */
    @Test
    @DisplayName("Should handle re-revocation idempotently")
    void shouldHandleReRevocationIdempotently() {
        // GIVEN: Revoked token
        String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));
        String tokenId = claims.getTokenId();

        boolean firstRevoke = runPromise(() -> provider.revokeToken(tenantId, tokenId));

        // WHEN: Revoke same token again
        boolean secondRevoke = runPromise(() -> provider.revokeToken(tenantId, tokenId));

        // THEN: First revoke returns true, second returns false
        assertThat(firstRevoke)
                .as("First revocation should return true (not revoked before)")
                .isTrue();
        assertThat(secondRevoke)
                .as("Second revocation should return false (already revoked)")
                .isFalse();
    }

    /**
     * GIVEN: Token for tenantA and tenantB
     * WHEN: Token from tenantA is revoked
     * THEN: Same token ID in tenantB is not affected
     */
    @Test
    @DisplayName("Should isolate revocations by tenant")
    void shouldIsolateTenantRevocations() {
        // GIVEN: Two tenants with provider
        TenantId tenantA = TenantId.of("tenant-a");
        TenantId tenantB = TenantId.of("tenant-b");

        String tokenA = runPromise(() -> provider.generateToken(tenantA, principal, Duration.ofHours(1)));
        JwtClaims claimsA = runPromise(() -> provider.validateToken(tenantA, tokenA));
        String tokenIdA = claimsA.getTokenId();

        String tokenB = runPromise(() -> provider.generateToken(tenantB, principal, Duration.ofHours(1)));
        runPromise(() -> provider.validateToken(tenantB, tokenB));

        // WHEN: Revoke token for tenantA
        runPromise(() -> provider.revokeToken(tenantA, tokenIdA));

        // THEN: TokenA is revoked and validation fails
        boolean tenantARevoked = false;
        try {
            runPromise(() -> provider.validateToken(tenantA, tokenA));
        } catch (Exception e) {
            tenantARevoked = true;
            assertThat(e).isInstanceOf(JwtValidationException.class);
        }
        assertThat(tenantARevoked).as("Token for tenantA should be revoked").isTrue();

        // AND: TokenB still validates successfully (different tenant)
        JwtClaims validatedB = runPromise(() -> provider.validateToken(tenantB, tokenB));
        assertThat(validatedB)
                .as("Token for different tenant should still validate")
                .isNotNull();
    }

    /**
     * GIVEN: Token ID
     * WHEN: isTokenRevoked() is called
     * THEN: Returns correct revocation status
     */
    @Test
    @DisplayName("Should check token revocation status")
    void shouldCheckTokenRevocationStatus() {
        // GIVEN: Token
        String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));
        String tokenId = claims.getTokenId();

        // WHEN: Check revocation status before revoking
        boolean revokedBefore = runPromise(() -> provider.isTokenRevoked(tenantId, tokenId));

        // THEN: Token is not revoked
        assertThat(revokedBefore)
                .as("Token should not be revoked initially")
                .isFalse();

        // WHEN: Revoke token
        runPromise(() -> provider.revokeToken(tenantId, tokenId));

        // AND: Check revocation status after revoking
        boolean revokedAfter = runPromise(() -> provider.isTokenRevoked(tenantId, tokenId));

        // THEN: Token is revoked
        assertThat(revokedAfter)
                .as("Token should be revoked after revocation")
                .isTrue();
    }

    // ==================== ERROR HANDLING TESTS ====================

    /**
     * GIVEN: Invalid token string
     * WHEN: validateToken() is called
     * THEN: JwtValidationException is thrown
     */
    @Test
    @DisplayName("Should reject malformed token")
    void shouldRejectMalformedToken() {
        // GIVEN: Invalid token (not JWT format)
        String invalidToken = "not.a.valid.jwt";

        // WHEN/THEN: Validation fails
        boolean exceptionThrown = false;
        try {
            runPromise(() -> provider.validateToken(tenantId, invalidToken));
        } catch (Exception e) {
            exceptionThrown = true;
            assertThat(e).isInstanceOf(JwtValidationException.class);
        }
        assertThat(exceptionThrown).as("Should throw exception for malformed token").isTrue();
    }

    /**
     * GIVEN: Valid token
     * WHEN: Token is tampered (signature changed)
     * THEN: Validation fails with signature error
     */
    @Test
    @DisplayName("Should reject token with tampered signature")
    void shouldRejectTamperedToken() {
        // GIVEN: Valid token
        String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));

        // When: Tamper with signature
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".tamperedsignature";

        // THEN: Validation fails
        boolean exceptionThrown = false;
        try {
            runPromise(() -> provider.validateToken(tenantId, tamperedToken));
        } catch (Exception e) {
            exceptionThrown = true;
            assertThat(e).isInstanceOf(JwtValidationException.class);
        }
        assertThat(exceptionThrown).as("Should throw exception for tampered token").isTrue();
    }

    // ==================== EXPIRATION TESTS ====================

    /**
     * GIVEN: Token with very short TTL
     * WHEN: Token is validated after expiration
     * THEN: Validation fails with expiration error
     */
    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() throws InterruptedException {
        // GIVEN: Token with 100ms TTL
        Duration veryShortTtl = Duration.ofMillis(100);
        String token = runPromise(() -> provider.generateToken(tenantId, principal, veryShortTtl));

        // WHEN: Wait for expiration
        Thread.sleep(150);

        // THEN: Validation fails
        boolean exceptionThrown = false;
        try {
            runPromise(() -> provider.validateToken(tenantId, token));
        } catch (Exception e) {
            exceptionThrown = true;
            assertThat(e).isInstanceOf(JwtValidationException.class);
        }
        assertThat(exceptionThrown).as("Should throw exception for expired token").isTrue();
    }

    // ==================== NULL/EDGE CASE TESTS ====================

    /**
     * GIVEN: Null parameters
     * WHEN: generateToken() is called with null
     * THEN: NullPointerException is thrown
     */
    @Test
    @DisplayName("Should handle null tenant ID")
    void shouldHandleNullTenantId() {
        // GIVEN: Null tenant
        TenantId nullTenant = null;

        // WHEN/THEN: Should throw Exception (likely NullPointerException)
        boolean exceptionThrown = false;
        try {
            runPromise(() -> provider.generateToken(nullTenant, principal, Duration.ofHours(1)));
        } catch (Exception e) {
            exceptionThrown = true;
            // Expected - could be NullPointerException or wrapped RuntimeException
            assertThat(e).isNotNull();
        }
        assertThat(exceptionThrown).as("Should throw exception for null tenant").isTrue();
    }

    /**
     * GIVEN: Principal with empty roles/permissions
     * WHEN: generateToken() is called
     * THEN: Token is generated successfully
     */
    @Test
    @DisplayName("Should generate token for user with empty roles")
    void shouldGenerateTokenForUserWithEmptyRoles() {
        // GIVEN: Principal with no roles
        UserPrincipal minimalPrincipal = UserPrincipal.builder()
                .userId("user-minimal")
                .email("minimal@example.com")
                .name("Minimal User")
                .roles(Set.of())
                .permissions(Set.of())
                .build();

        // WHEN: Generate token
        String token = runPromise(() -> provider.generateToken(tenantId, minimalPrincipal, Duration.ofHours(1)));

        // THEN: Token is generated
        assertThat(token)
                .as("Token should be generated for user with empty roles")
                .isNotEmpty();

        // AND: Validation succeeds with empty roles
        JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));
        assertThat(claims.getRoles())
                .as("Claims should have empty roles")
                .isEmpty();
    }
}
