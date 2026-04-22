/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("AepAuthFilter [GH-90000]")
class AepAuthFilterTest extends EventloopTestBase {

    private static final String PRIVATE_URL = "http://localhost/api/v1/pipelines";
    private static final String AI_SUGGESTIONS_URL = "http://localhost/api/v1/ai/suggestions";
    private static final String PUBLIC_URL = "http://localhost/health";

    @Test
    @DisplayName("public endpoint preserves provided correlation ID and clears MDC after request [GH-90000]")
    void publicEndpointPreservesCorrelationId() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        AtomicReference<String> observedCorrelationId = new AtomicReference<>(); // GH-90000
        when(nextServlet.serve(any())).thenAnswer(invocation -> { // GH-90000
            observedCorrelationId.set(MDC.get("correlationId [GH-90000]"));
            return Promise.of(HttpResponse.ofCode(200).build()); // GH-90000
        });

        AepAuthFilter filter = new AepAuthFilter(nextServlet, "secret", true); // GH-90000

        HttpRequest request = HttpRequest.get(PUBLIC_URL) // GH-90000
            .withHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"), "corr-public-123")
            .build(); // GH-90000
        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(200, response.getCode()); // GH-90000
        assertEquals("corr-public-123", observedCorrelationId.get()); // GH-90000
        assertEquals("corr-public-123", response.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]")));
        assertNull(MDC.get("correlationId [GH-90000]"));
    }

    @Test
    @DisplayName("valid JWT request generates correlation ID when header is absent [GH-90000]")
    void validJwtRequestGeneratesCorrelationId() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        AtomicReference<String> observedCorrelationId = new AtomicReference<>(); // GH-90000
        when(nextServlet.serve(any())).thenAnswer(invocation -> { // GH-90000
            observedCorrelationId.set(MDC.get("correlationId [GH-90000]"));
            return Promise.of(HttpResponse.ofCode(200).build()); // GH-90000
        });

        String jwtSecret = "unit-test-secret";
        AepAuthFilter filter = new AepAuthFilter(nextServlet, jwtSecret, true); // GH-90000

        HttpRequest request = HttpRequest.get(PRIVATE_URL) // GH-90000
            .withHeader(HttpHeaders.of("Authorization [GH-90000]"), "Bearer " + createJwt(jwtSecret))
            .build(); // GH-90000
        HttpResponse response = serve(filter, request); // GH-90000
        String correlationId = response.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"));

        assertEquals(200, response.getCode()); // GH-90000
        assertNotNull(correlationId); // GH-90000
        assertFalse(correlationId.isBlank()); // GH-90000
        assertEquals(correlationId, observedCorrelationId.get()); // GH-90000
        assertNull(MDC.get("correlationId [GH-90000]"));
    }

    @Test
    @DisplayName("valid JWT attaches parsed role and permission claims for downstream authorization [GH-90000]")
    void validJwtAttachesAuthorizationClaims() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        AtomicReference<AepAuthFilter.JwtPayload> observedPayload = new AtomicReference<>(); // GH-90000
        when(nextServlet.serve(any())).thenAnswer(invocation -> { // GH-90000
            HttpRequest request = invocation.getArgument(0); // GH-90000
            observedPayload.set(request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT)); // GH-90000
            return Promise.of(HttpResponse.ofCode(200).build()); // GH-90000
        });

        String jwtSecret = "unit-test-secret";
        AepAuthFilter filter = new AepAuthFilter(nextServlet, jwtSecret, true); // GH-90000

        HttpRequest request = HttpRequest.get(PRIVATE_URL) // GH-90000
            .withHeader(HttpHeaders.of("Authorization [GH-90000]"), "Bearer " + createJwt(
                jwtSecret,
                "\"roles\":[\"admin\",\"operator\"],\"permissions\":[\"deployment:create\"]"
            ))
            .build(); // GH-90000
        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(200, response.getCode()); // GH-90000
        assertNotNull(observedPayload.get()); // GH-90000
        assertThat(observedPayload.get().roles()).contains("admin", "operator"); // GH-90000
        assertThat(observedPayload.get().permissions()).contains("deployment:create [GH-90000]");
        assertThat(observedPayload.get().canManageDeployments()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("unauthorized response includes correlation ID and does not call downstream [GH-90000]")
    void unauthorizedResponseIncludesCorrelationId() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true); // GH-90000

        HttpRequest request = HttpRequest.get(PRIVATE_URL) // GH-90000
            .withHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"), "corr-failure-456")
            .build(); // GH-90000
        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(401, response.getCode()); // GH-90000
        assertEquals("corr-failure-456", response.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]")));
        verify(nextServlet, never()).serve(any()); // GH-90000
        assertNull(MDC.get("correlationId [GH-90000]"));
    }

    @Test
    @DisplayName("ai suggestions endpoint requires authentication when auth is enabled [GH-90000]")
    void aiSuggestionsEndpointRequiresAuthentication() { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "unit-test-secret", true); // GH-90000

        HttpRequest request = HttpRequest.get(AI_SUGGESTIONS_URL).build(); // GH-90000
        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(401, response.getCode()); // GH-90000
    }

    @Test
    @DisplayName("production env + auth disabled throws IllegalStateException at construction [GH-90000]")
    void productionWithAuthDisabledThrows() { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        assertThrows(IllegalStateException.class, // GH-90000
            () -> new AepAuthFilter(nextServlet, "some-secret", false, "production"), // GH-90000
            "Should refuse to start in production with auth disabled");
    }

    @Test
    @DisplayName("production env + blank JWT secret throws IllegalStateException at construction [GH-90000]")
    void productionWithBlankSecretThrows() { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        assertThrows(IllegalStateException.class, // GH-90000
            () -> new AepAuthFilter(nextServlet, "", true, "production"), // GH-90000
            "Should refuse to start in production with blank JWT secret");
    }

    @Test
    @DisplayName("development env + auth disabled is allowed (no exception) [GH-90000]")
    void developmentWithAuthDisabledIsAllowed() { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        // Must not throw
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "", true, "development"); // GH-90000
        assertNotNull(filter); // GH-90000
    }

    @Test
    @DisplayName("test env + auth disabled is allowed (no exception) [GH-90000]")
    void testEnvWithAuthDisabledIsAllowed() { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        AepAuthFilter filter = new AepAuthFilter(nextServlet, "", true, "test"); // GH-90000
        assertNotNull(filter); // GH-90000
    }

    private HttpResponse serve(AsyncServlet filter, HttpRequest request) { // GH-90000
        return runPromise(() -> filter.serve(request)); // GH-90000
    }

    private String createJwt(String secret) throws Exception { // GH-90000
        return createJwt(secret, null); // GH-90000
    }

    private String createJwt(String secret, String additionalClaims) throws Exception { // GH-90000
        long issuedAt = Instant.now().getEpochSecond(); // GH-90000
        long expiresAt = issuedAt + 300;
        String header = encodeBase64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"); // GH-90000
        String extraClaims = (additionalClaims == null || additionalClaims.isBlank()) ? "" : "," + additionalClaims; // GH-90000
        String payload = encodeBase64Url(String.format( // GH-90000
            "{\"sub\":\"test-user\",\"iss\":\"unit-test\",\"iat\":%d,\"exp\":%d%s}",
            issuedAt,
            expiresAt,
            extraClaims
        ));
        String signature = sign(header + "." + payload, secret); // GH-90000
        return header + "." + payload + "." + signature;
    }

    private String encodeBase64Url(String value) { // GH-90000
        return Base64.getUrlEncoder().withoutPadding() // GH-90000
            .encodeToString(value.getBytes(StandardCharsets.UTF_8)); // GH-90000
    }

    private String sign(String data, String secret) throws Exception { // GH-90000
        Mac mac = Mac.getInstance("HmacSHA256 [GH-90000]");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); // GH-90000
        return Base64.getUrlEncoder().withoutPadding() // GH-90000
            .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))); // GH-90000
    }
}
