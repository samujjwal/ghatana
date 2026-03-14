/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.service;

import com.ghatana.appplatform.iam.domain.TokenClaims;
import com.ghatana.appplatform.iam.provider.InMemorySigningKeyProvider;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for RS256 JWT token issuance (STORY-K01-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JwtTokenService Tests")
class JwtTokenServiceTest {

    private final InMemorySigningKeyProvider keyProvider = new InMemorySigningKeyProvider();
    private final JwtTokenService tokenService = new JwtTokenService(keyProvider);

    private TokenClaims buildClaims() {
        return TokenClaims.builder()
                .subject("svc-ledger")
                .tenantId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .roles(List.of("platform:admin"))
                .permissions(List.of("ledger:read", "ledger:post"))
                .issuer("https://auth.ghatana.io")
                .audience("ghatana-api")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("issue() returns non-null compact JWT string")
    void issue_returnsNonNullToken() {
        String token = tokenService.issue(buildClaims());
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);  // header.payload.signature
    }

    @Test
    @DisplayName("issued JWT has valid RS256 signature verifiable with the public key")
    void issue_signatureValidatesWithPublicKey() throws Exception {
        String token = tokenService.issue(buildClaims());
        SignedJWT parsed = SignedJWT.parse(token);
        RSASSAVerifier verifier = new RSASSAVerifier(keyProvider.getSigningKey().toPublicJWK());
        assertThat(parsed.verify(verifier)).isTrue();
    }

    @Test
    @DisplayName("issued JWT contains all required claims")
    void issue_containsAllRequiredClaims() throws Exception {
        TokenClaims claims = buildClaims();
        String token = tokenService.issue(claims);
        JWTClaimsSet parsedClaims = SignedJWT.parse(token).getJWTClaimsSet();

        assertThat(parsedClaims.getSubject()).isEqualTo("svc-ledger");
        assertThat(parsedClaims.getIssuer()).isEqualTo("https://auth.ghatana.io");
        assertThat(parsedClaims.getAudience()).contains("ghatana-api");
        assertThat(parsedClaims.getJWTID()).isNotBlank();
        assertThat(parsedClaims.getStringClaim("tenant_id"))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(parsedClaims.getStringListClaim("roles")).containsExactly("platform:admin");
        assertThat(parsedClaims.getStringListClaim("permissions"))
                .containsExactlyInAnyOrder("ledger:read", "ledger:post");
    }

    @Test
    @DisplayName("issued JWT has correct expiry matching expiresAt claim")
    void issue_expiryMatchesClaim() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(1800);
        TokenClaims claims = TokenClaims.builder()
                .subject("svc-ledger")
                .tenantId(UUID.randomUUID())
                .issuer("https://auth.ghatana.io")
                .audience("ghatana-api")
                .expiresAt(expiresAt)
                .build();

        String token = tokenService.issue(claims);
        JWTClaimsSet parsedClaims = SignedJWT.parse(token).getJWTClaimsSet();

        // Allow 1-second clock skew
        long expectedEpoch = expiresAt.getEpochSecond();
        long actualEpoch = parsedClaims.getExpirationTime().toInstant().getEpochSecond();
        assertThat(Math.abs(actualEpoch - expectedEpoch)).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("claims with past expiresAt show as expired via isExpired()")
    void claims_withPastExpiry_isExpiredReturnsTrue() {
        TokenClaims claims = TokenClaims.builder()
                .subject("svc-old")
                .tenantId(UUID.randomUUID())
                .issuer("test-iss")
                .audience("test-aud")
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        assertThat(claims.isExpired()).isTrue();
    }

    @Test
    @DisplayName("two tokens issued from the same claims have distinct jti values")
    void issue_eachTokenHasUniqueJti() throws Exception {
        TokenClaims claims1 = buildClaims();
        TokenClaims claims2 = buildClaims();

        String jti1 = SignedJWT.parse(tokenService.issue(claims1)).getJWTClaimsSet().getJWTID();
        String jti2 = SignedJWT.parse(tokenService.issue(claims2)).getJWTClaimsSet().getJWTID();

        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    @DisplayName("JWS kid header matches the key provider's key ID")
    void issue_kidHeaderMatchesProvider() throws Exception {
        String token = tokenService.issue(buildClaims());
        SignedJWT parsed = SignedJWT.parse(token);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(keyProvider.getKeyId());
    }
}
