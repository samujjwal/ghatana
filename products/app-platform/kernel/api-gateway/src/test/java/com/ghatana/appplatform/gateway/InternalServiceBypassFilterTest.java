/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway;

import com.ghatana.appplatform.iam.domain.TokenClaims;
import com.ghatana.appplatform.iam.provider.InMemorySigningKeyProvider;
import com.ghatana.appplatform.iam.service.JwtTokenService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InternalServiceBypassFilter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for internal service bypass filter (K11-007)
 * @doc.layer kernel
 * @doc.pattern Test
 */
@DisplayName("InternalServiceBypassFilter — Unit Tests")
class InternalServiceBypassFilterTest extends EventloopTestBase {

    private static final String SERVICE_ACCOUNT = "svc-ledger-framework";
    private static final Set<String> ALLOWED = Set.of(SERVICE_ACCOUNT, "svc-audit-trail");

    private InternalServiceBypassFilter filter;
    private InMemorySigningKeyProvider keyProvider;
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        keyProvider  = new InMemorySigningKeyProvider();
        tokenService = new JwtTokenService(keyProvider);
        filter       = new InternalServiceBypassFilter(ALLOWED);
    }

    // ── Token helpers ──────────────────────────────────────────────────────────

    private String internalServiceToken(String serviceAccount) throws Exception {
        com.nimbusds.jose.JWSHeader header = new com.nimbusds.jose.JWSHeader.Builder(
                com.nimbusds.jose.JWSAlgorithm.RS256)
                .keyID(keyProvider.getKeyId())
                .build();
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
                .subject(serviceAccount)
                .claim("role", "INTERNAL_SERVICE")
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(300)))
                .build();
        com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(header, claims);
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(keyProvider.getSigningKey()));
        return jwt.serialize();
    }

    private String regularUserToken() {
        return tokenService.issue(TokenClaims.builder()
                .subject("user-abc")
                .tenantId(UUID.randomUUID())
                .roles(List.of("user"))
                .permissions(List.of())
                .issuer("https://auth.ghatana.io")
                .audience("api-gateway")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());
    }

    private static io.activej.http.AsyncServlet captureRequest(java.util.concurrent.atomic.AtomicReference<HttpRequest> ref) {
        return req -> {
            ref.set(req);
            return Promise.of(HttpResponse.ok200().build());
        };
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("allowed internal service account — marks request as internal and forwards")
    void internalService_markedAndForwarded() throws Exception {
        String token = internalServiceToken(SERVICE_ACCOUNT);
        java.util.concurrent.atomic.AtomicReference<HttpRequest> served = new java.util.concurrent.atomic.AtomicReference<>();
        HttpRequest request = HttpRequest.get("http://localhost/internal-op")
                .withHeader(io.activej.http.HttpHeaders.of("Authorization"), "Bearer " + token)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, captureRequest(served)));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(served.get()).isNotNull();
        assertThat(InternalServiceBypassFilter.isInternalRequest(served.get())).isTrue();
    }

    @Test
    @DisplayName("regular user token — not marked as internal, forwarded normally")
    void regularUserToken_notMarkedInternal() {
        String token = regularUserToken();
        java.util.concurrent.atomic.AtomicReference<HttpRequest> served = new java.util.concurrent.atomic.AtomicReference<>();
        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(io.activej.http.HttpHeaders.of("Authorization"), "Bearer " + token)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, captureRequest(served)));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(InternalServiceBypassFilter.isInternalRequest(served.get())).isFalse();
    }

    @Test
    @DisplayName("unknown service account (not in allow-list) — not marked internal")
    void unknownServiceAccount_notMarkedInternal() throws Exception {
        String token = internalServiceToken("svc-rogue-service");
        java.util.concurrent.atomic.AtomicReference<HttpRequest> served = new java.util.concurrent.atomic.AtomicReference<>();
        HttpRequest request = HttpRequest.get("http://localhost/internal-op")
                .withHeader(io.activej.http.HttpHeaders.of("Authorization"), "Bearer " + token)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, captureRequest(served)));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(InternalServiceBypassFilter.isInternalRequest(served.get())).isFalse();
    }

    @Test
    @DisplayName("no Authorization header — forwarded without internal bypass")
    void noAuthHeader_notMarkedInternal() {
        java.util.concurrent.atomic.AtomicReference<HttpRequest> served = new java.util.concurrent.atomic.AtomicReference<>();
        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger").build();

        HttpResponse response = runPromise(() -> filter.apply(request, captureRequest(served)));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(InternalServiceBypassFilter.isInternalRequest(served.get())).isFalse();
    }

    @Test
    @DisplayName("malformed JWT — filter continues chain without bypass")
    void malformedJwt_continuedWithoutBypass() {
        java.util.concurrent.atomic.AtomicReference<HttpRequest> served = new java.util.concurrent.atomic.AtomicReference<>();
        HttpRequest request = HttpRequest.get("http://localhost/api/v2/ledger")
                .withHeader(io.activej.http.HttpHeaders.of("Authorization"), "Bearer garbage")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, captureRequest(served)));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(InternalServiceBypassFilter.isInternalRequest(served.get())).isFalse();
    }

    @Test
    @DisplayName("second allowed service account — also granted bypass")
    void secondAllowedServiceAccount_markedInternal() throws Exception {
        String token = internalServiceToken("svc-audit-trail");
        java.util.concurrent.atomic.AtomicReference<HttpRequest> served = new java.util.concurrent.atomic.AtomicReference<>();
        HttpRequest request = HttpRequest.get("http://localhost/internal-op")
                .withHeader(io.activej.http.HttpHeaders.of("Authorization"), "Bearer " + token)
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, captureRequest(served)));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(InternalServiceBypassFilter.isInternalRequest(served.get())).isTrue();
    }
}
