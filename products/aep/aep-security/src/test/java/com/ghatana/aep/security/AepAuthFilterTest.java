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