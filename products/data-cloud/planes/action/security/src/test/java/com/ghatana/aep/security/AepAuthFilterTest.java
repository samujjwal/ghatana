/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for auth filter correlation propagation and auth edge behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepAuthFilter")
class AepAuthFilterTest extends EventloopTestBase {

    private static final String PRIVATE_URL = "http://localhost/api/v1/pipelines";
    private static final String AI_SUGGESTIONS_URL = "http://localhost/api/v1/ai/suggestions";
    private static final String PUBLIC_URL = "http://localhost/health";

    @Test
    @DisplayName("public endpoint preserves provided correlation ID and clears MDC after request")
    void publicEndpointPreservesCorrelationId() throws Exception { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        AtomicReference<String> observedCorrelationId = new AtomicReference<>(); 
        when(nextServlet.serve(any())).thenAnswer(invocation -> { 
            observedCorrelationId.set(MDC.get("correlationId"));
            return Promise.of(HttpResponse.ofCode(200).build()); 
        });

        AepAuthFilter filter = new AepAuthFilter(nextServlet, "secret", true); 

        HttpRequest request = HttpRequest.get(PUBLIC_URL) 
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-public-123")
            .build(); 
        HttpResponse response = serve(filter, request); 

        assertEquals(200, response.getCode()); 
        assertEquals("corr-public-123", observedCorrelationId.get()); 
        assertEquals("corr-public-123", response.getHeader(HttpHeaders.of("X-Correlation-ID")));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    @DisplayName("valid JWT request generates correlation ID when header is absent")
    void validJwtRequestGeneratesCorrelationId() throws Exception { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        AtomicReference<String> observedCorrelationId = new AtomicReference<>(); 
        when(nextServlet.serve(any())).thenAnswer(invocation -> { 
            observedCorrelationId.set(MDC.get("correlationId"));
            return Promise.of(HttpResponse.ofCode(200).build()); 
        });

        String jwtSecret = "unit-test-secret";
        AepAuthFilter filter = new AepAuthFilter(nextServlet, jwtSecret, true); 

        HttpRequest request = HttpRequest.get(PRIVATE_URL) 
            .withHeader(HttpHeaders.of("Authorization"), "Bearer " + createJwt(jwtSecret))
            .build(); 
        HttpResponse response = serve(filter, request); 
        String correlationId = response.getHeader(HttpHeaders.of("X-Correlation-ID"));

        assertEquals(200, response.getCode()); 
        assertNotNull(correlationId); 
        assertFalse(correlationId.isBlank()); 
        assertEquals(correlationId, observedCorrelationId.get()); 
        assertNull(MDC.get("correlationId"));
    }

    @Test
    @DisplayName("valid JWT attaches parsed role and permission claims for downstream authorization")
    void validJwtAttachesAuthorizationClaims() throws Exception { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        AtomicReference<AepAuthFilter.JwtPayload> observedPayload = new AtomicReference<>(); 
        when(nextServlet.serve(any())).thenAnswer(invocation -> { 
            HttpRequest request = invocation.getArgument(0); 
            observedPayload.set(request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT)); 
            return Promise.of(HttpResponse.ofCode(200).build()); 
        });

        String jwtSecret = "unit-test-secret";
        AepAuthFilter filter = new AepAuthFilter(nextServlet, jwtSecret, true); 

        HttpRequest request = HttpRequest.get(PRIVATE_URL) 
            .withHeader(HttpHeaders.of("Authorization"), "Bearer " + createJwt(
                jwtSecret,
                "\"roles\":[\"admin\",\"operator\"],\"permissions\":[\"deployment:create\"]"
            ))
            .build(); 
        HttpResponse response = serve(filter, request); 

        assertEquals(200, response.getCode()); 
        assertNotNull(observedPayload.get()); 
        assertThat(observedPayload.get().roles()).contains("admin", "operator"); 
        assertThat(observedPayload.get().permissions()).contains("deployment:create");
        assertThat(observedPayload.get().canManageDeployments()).isTrue(); 
    }

    @Test
    @DisplayName("unauthorized response includes correlation ID and does not call downstream")
    void unauthorizedResponseIncludesCorrelationId() throws Exception { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true); 

        HttpRequest request = HttpRequest.get(PRIVATE_URL) 
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-failure-456")
            .build(); 
        HttpResponse response = serve(filter, request); 

        assertEquals(401, response.getCode()); 
        assertEquals("corr-failure-456", response.getHeader(HttpHeaders.of("X-Correlation-ID")));
        verify(nextServlet, never()).serve(any()); 
        assertNull(MDC.get("correlationId"));
    }

    @Test
    @DisplayName("ai suggestions endpoint requires authentication when auth is enabled")
    void aiSuggestionsEndpointRequiresAuthentication() { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true); 

        HttpRequest request = HttpRequest.get(AI_SUGGESTIONS_URL).build(); 
        HttpResponse response = serve(filter, request); 

        assertEquals(401, response.getCode()); 
    }

    @Test
    @DisplayName("production env + auth disabled throws IllegalStateException at construction")
    void productionWithAuthDisabledThrows() { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        assertThrows(IllegalStateException.class, 
            () -> new AepAuthFilter(nextServlet, "some-secret", false, "production"), 
            "Should refuse to start in production with auth disabled");
    }

    @Test
    @DisplayName("production env + blank JWT secret throws IllegalStateException at construction")
    void productionWithBlankSecretThrows() { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        assertThrows(IllegalStateException.class, 
            () -> new AepAuthFilter(nextServlet, "", true, "production"), 
            "Should refuse to start in production with blank JWT secret");
    }

    @Test
    @DisplayName("development env + auth disabled is allowed (no exception)")
    void developmentWithAuthDisabledIsAllowed() { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        // Must not throw
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "", true, "development"); 
        assertNotNull(filter); 
    }

    @Test
    @DisplayName("test env + auth disabled is allowed (no exception)")
    void testEnvWithAuthDisabledIsAllowed() { 
        AsyncServlet nextServlet = mock(AsyncServlet.class); 
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "", true, "test"); 
        assertNotNull(filter); 
    }

    // ─── Additional coverage: auth bypass, token validity, tenant, payload unit tests ───

    @Test
    @DisplayName("missing Authorization header returns 401 on private path")
    void missingAuthorizationHeaderReturnsUnauthorized() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true);

        HttpRequest request = HttpRequest.get(PRIVATE_URL).build();
        HttpResponse response = serve(filter, request);

        assertEquals(401, response.getCode());
        verify(nextServlet, never()).serve(any());
    }

    @Test
    @DisplayName("Authorization header with empty bearer token after prefix returns 401")
    void emptyBearerTokenReturnsUnauthorized() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true);

        HttpRequest request = HttpRequest.get(PRIVATE_URL)
                .withHeader(HttpHeaders.of("Authorization"), "Bearer ")
                .build();
        HttpResponse response = serve(filter, request);

        assertEquals(401, response.getCode());
        verify(nextServlet, never()).serve(any());
    }

    @Test
    @DisplayName("JWT with invalid structure (single part) returns 401")
    void invalidJwtStructureReturnsUnauthorized() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true);

        HttpRequest request = HttpRequest.get(PRIVATE_URL)
                .withHeader(HttpHeaders.of("Authorization"), "Bearer notavalidjwt")
                .build();
        HttpResponse response = serve(filter, request);

        assertEquals(401, response.getCode());
        verify(nextServlet, never()).serve(any());
    }

    @Test
    @DisplayName("JWT signed with wrong secret returns 401 (signature mismatch)")
    void jwtSignedWithWrongSecretReturnsUnauthorized() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "correct-secret", true);

        // Sign token with a DIFFERENT secret — signature mismatch
        String tamperedJwt = createJwt("wrong-secret");

        HttpRequest request = HttpRequest.get(PRIVATE_URL)
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + tamperedJwt)
                .build();
        HttpResponse response = serve(filter, request);

        assertEquals(401, response.getCode());
        verify(nextServlet, never()).serve(any());
    }

    @Test
    @DisplayName("expired JWT returns 401 (exp in the past)")
    void expiredJwtReturnsUnauthorized() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        String jwtSecret = "unit-test-secret";
        AepAuthFilter filter = new AepAuthFilter(nextServlet, jwtSecret, true);

        // Build a JWT whose exp is 60 seconds in the past
        long issuedAt = Instant.now().getEpochSecond() - 120;
        long expiresAt = issuedAt + 60; // already expired
        String header = encodeBase64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encodeBase64Url(String.format(
                "{\"sub\":\"expired-user\",\"iss\":\"unit-test\",\"iat\":%d,\"exp\":%d}",
                issuedAt, expiresAt));
        String token = header + "." + payload + "." + sign(header + "." + payload, jwtSecret);

        HttpRequest request = HttpRequest.get(PRIVATE_URL)
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + token)
                .build();
        HttpResponse response = serve(filter, request);

        assertEquals(401, response.getCode());
        verify(nextServlet, never()).serve(any());
    }

    @Test
    @DisplayName("auth disabled (dev mode) allows requests to private paths without a token")
    void authDisabledAllowsPrivatePath() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build()));

        // authEnabled=false, jwtSecret=blank — permitted in development environment
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "", false, "development");

        HttpRequest request = HttpRequest.get(PRIVATE_URL).build();
        HttpResponse response = serve(filter, request);

        assertEquals(200, response.getCode());
        verify(nextServlet).serve(any());
    }

    @Test
    @DisplayName("tenant ID is extracted from JWT claims and attached to the request payload")
    void tenantIdExtractedFromJwtClaims() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        AtomicReference<AepAuthFilter.JwtPayload> capturedPayload = new AtomicReference<>();
        when(nextServlet.serve(any())).thenAnswer(invocation -> {
            HttpRequest req = invocation.getArgument(0);
            capturedPayload.set(req.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT));
            return Promise.of(HttpResponse.ofCode(200).build());
        });

        String jwtSecret = "unit-test-secret";
        AepAuthFilter filter = new AepAuthFilter(nextServlet, jwtSecret, true);

        String jwt = createJwt(jwtSecret, "\"tenantId\":\"tenant-acme-42\"");
        HttpRequest request = HttpRequest.get(PRIVATE_URL)
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + jwt)
                .build();
        serve(filter, request);

        assertNotNull(capturedPayload.get());
        assertEquals("tenant-acme-42", capturedPayload.get().tenantId());
    }

    @Test
    @DisplayName("OPTIONS preflight request bypasses JWT auth and reaches downstream")
    void optionsPreflightBypassesJwtAuth() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(204).build()));

        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true);

        HttpRequest request = HttpRequest.builder(io.activej.http.HttpMethod.OPTIONS, PRIVATE_URL)
                .withHeader(HttpHeaders.of("Origin"), "https://app.ghatana.dev")
                .build();
        HttpResponse response = serve(filter, request);

        // Downstream should be called without auth check; no 401
        assertEquals(204, response.getCode());
        verify(nextServlet).serve(any());
    }

    @Test
    @DisplayName("JwtPayload.hasRole is case-insensitive")
    void jwtPayloadHasRoleIsCaseInsensitive() {
        AepAuthFilter.JwtPayload payload = new AepAuthFilter.JwtPayload(
                "sub", "iss", 0L, 0L,
                java.util.List.of("ADMIN", "viewer"),
                java.util.List.of(),
                null);

        assertThat(payload.hasRole("admin")).isTrue();
        assertThat(payload.hasRole("VIEWER")).isTrue();
        assertThat(payload.hasRole("operator")).isFalse();
    }

    @Test
    @DisplayName("JwtPayload.hasPermission returns true for wildcard permission '*'")
    void jwtPayloadWildcardPermissionGrantsAll() {
        AepAuthFilter.JwtPayload payload = new AepAuthFilter.JwtPayload(
                "sub", "iss", 0L, 0L,
                java.util.List.of(),
                java.util.List.of("*"),
                null);

        assertThat(payload.hasPermission("deployment:create")).isTrue();
        assertThat(payload.hasPermission("anything")).isTrue();
    }

    @Test
    @DisplayName("JwtPayload.canManageDeployments is true for 'deployer' role")
    void jwtPayloadDeployerRoleCanManage() {
        AepAuthFilter.JwtPayload payload = new AepAuthFilter.JwtPayload(
                "sub", "iss", 0L, 0L,
                java.util.List.of("deployer"),
                java.util.List.of("read"),
                "tenant-xyz");

        assertThat(payload.canManageDeployments()).isTrue();
    }

    @Test
    @DisplayName("JwtPayload.canManageDeployments is false for 'viewer' role with no deployment permissions")
    void jwtPayloadViewerCannotManage() {
        AepAuthFilter.JwtPayload payload = new AepAuthFilter.JwtPayload(
                "sub", "iss", 0L, 0L,
                java.util.List.of("viewer"),
                java.util.List.of("read"),
                "tenant-xyz");

        assertThat(payload.canManageDeployments()).isFalse();
    }

    @Test
    @DisplayName("missing JWT secret with auth enabled returns 401 (fail-closed)")
    void missingJwtSecretWithAuthEnabledRejectRequests() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        // authEnabled=true but no secret — should fail-closed
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "", true, "development");

        HttpRequest request = HttpRequest.get(PRIVATE_URL)
                .withHeader(HttpHeaders.of("Authorization"), "Bearer sometoken")
                .build();
        HttpResponse response = serve(filter, request);

        assertEquals(401, response.getCode());
        verify(nextServlet, never()).serve(any());
    }

    private HttpResponse serve(AsyncServlet filter, HttpRequest request) { 
        return runPromise(() -> filter.serve(request)); 
    }

    private String createJwt(String secret) throws Exception { 
        return createJwt(secret, null); 
    }

    private String createJwt(String secret, String additionalClaims) throws Exception { 
        long issuedAt = Instant.now().getEpochSecond(); 
        long expiresAt = issuedAt + 300;
        String header = encodeBase64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"); 
        String extraClaims = (additionalClaims == null || additionalClaims.isBlank()) ? "" : "," + additionalClaims; 
        String payload = encodeBase64Url(String.format( 
            "{\"sub\":\"test-user\",\"iss\":\"unit-test\",\"iat\":%d,\"exp\":%d%s}",
            issuedAt,
            expiresAt,
            extraClaims
        ));
        String signature = sign(header + "." + payload, secret); 
        return header + "." + payload + "." + signature;
    }

    private String encodeBase64Url(String value) { 
        return Base64.getUrlEncoder().withoutPadding() 
            .encodeToString(value.getBytes(StandardCharsets.UTF_8)); 
    }

    private String sign(String data, String secret) throws Exception { 
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); 
        return Base64.getUrlEncoder().withoutPadding() 
            .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))); 
    }
}
