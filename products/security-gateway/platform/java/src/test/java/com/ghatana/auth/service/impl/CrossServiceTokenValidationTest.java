package com.ghatana.auth.service.impl;

import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.auth.core.port.JwtTokenProvider;
import com.ghatana.auth.core.port.JwtTokenProvider.JwtValidationException;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration contract test verifying that the security-gateway JWT validation pipeline
 * correctly handles tokens issued using the same platform JWT implementation used by
 * the shared-services/auth-gateway.
 *
 * <p>This is the required cross-service token validation proof per ADR-019.
 *
 * <p>Contract verified:
 * <ul>
 *   <li>A JWT issued with {@link JwtTokenProviderImpl} (platform convention) is accepted
 *       when validated through the same provider (mimicking the shared issuer → validator path).</li>
 *   <li>An expired token is rejected with a meaningful validation exception.</li>
 *   <li>A tampered (malformed) token is rejected.</li>
 *   <li>A token issued for one tenant cannot satisfy a validation for another tenant.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Cross-service JWT issuance-to-validation contract test (ADR-019)
 * @doc.layer product
 * @doc.pattern ValidationTest
 */
@DisplayName("Cross-Service Token Validation Contract (ADR-019)")
class CrossServiceTokenValidationTest extends EventloopTestBase {

    /** Shared provider — represents the platform implementation used by auth-gateway and security-gateway. */
    private JwtTokenProviderImpl provider;
    private TenantId tenantId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProviderImpl(new NoopMetricsCollector());
        tenantId = TenantId.of("contoso");
        principal = UserPrincipal.builder()
                .userId("u-001")
                .email("alice@contoso.com")
                .name("Alice")
                .roles(Set.of("USER"))
                .permissions(Set.of("READ"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Happy Path
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Valid token acceptance")
    class ValidTokenAcceptance {

        @Test
        @DisplayName("Token issued by platform implementation is accepted by security-gateway validator")
        void platformIssuedTokenIsAcceptedByValidator() {
            // GIVEN — token issued following auth-gateway convention (same impl, keyed per tenant)
            String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));

            // WHEN — security-gateway validates it
            JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));

            // THEN — all identity claims survive the round-trip
            assertThat(claims.getUserId()).isEqualTo("u-001");
            assertThat(claims.getEmail()).isEqualTo("alice@contoso.com");
            assertThat(claims.getTenantId()).isEqualTo(tenantId);
            assertThat(claims.getRoles()).contains("USER");
        }

        @Test
        @DisplayName("Refresh token issued by platform implementation is accepted by validator")
        void refreshTokenIsAcceptedByValidator() {
            // GIVEN
            String refreshToken = runPromise(() ->
                    provider.generateRefreshToken(tenantId, principal, Duration.ofDays(30)));

            // WHEN
            JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, refreshToken));

            // THEN
            assertThat(claims.getUserId()).isEqualTo("u-001");
            assertThat(claims.getTenantId()).isEqualTo(tenantId);
        }
    }

    // -------------------------------------------------------------------------
    // Rejection Path
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid token rejection")
    class InvalidTokenRejection {

        @Test
        @DisplayName("Expired token is rejected by the validator")
        void expiredTokenIsRejected() {
            // GIVEN — token with 1ms TTL (will be expired by the time validation runs)
            String expiredToken = runPromise(() ->
                    provider.generateToken(tenantId, principal, Duration.ofMillis(1)));

            // Brief sleep so the token expires before we validate
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // WHEN / THEN — validation must fail with a meaningful exception
            assertThatThrownBy(() -> runPromise(() -> provider.validateToken(tenantId, expiredToken)))
                    .isInstanceOf(JwtValidationException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("Tampered token (modified signature) is rejected")
        void tamperedTokenIsRejected() {
            // GIVEN — valid token whose signature is corrupted
            String validToken = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));
            String tampered = validToken.substring(0, validToken.length() - 5) + "XXXXX";

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> provider.validateToken(tenantId, tampered)))
                    .isInstanceOf(JwtValidationException.class);
        }

        @Test
        @DisplayName("Completely malformed token string is rejected")
        void malformedTokenIsRejected() {
            assertThatThrownBy(() -> runPromise(() -> provider.validateToken(tenantId, "not.a.jwt")))
                    .isInstanceOf(JwtValidationException.class);
        }

        @Test
        @DisplayName("Token issued for one tenant is rejected when validated against a different tenant")
        void crossTenantTokenIsRejected() {
            // GIVEN — token for contoso
            String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));

            // WHEN — validated against a different tenant
            TenantId otherTenant = TenantId.of("fabrikam");

            // THEN — must reject (tenant isolation)
            assertThatThrownBy(() -> runPromise(() -> provider.validateToken(otherTenant, token)))
                    .isInstanceOf(JwtValidationException.class);
        }

        @Test
        @DisplayName("Revoked token is rejected by the validator")
        void revokedTokenIsRejected() {
            // GIVEN — generate and then revoke a token
            String token = runPromise(() -> provider.generateToken(tenantId, principal, Duration.ofHours(1)));
            JwtClaims claims = runPromise(() -> provider.validateToken(tenantId, token));
            runPromise(() -> provider.revokeToken(tenantId, claims.getTokenId()));

            // WHEN / THEN
            assertThatThrownBy(() -> runPromise(() -> provider.validateToken(tenantId, token)))
                    .isInstanceOf(JwtValidationException.class)
                    .hasMessageContaining("revoked");
        }
    }
}
