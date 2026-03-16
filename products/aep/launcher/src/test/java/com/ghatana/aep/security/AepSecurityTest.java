/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.launcher.http.AepHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-level security tests for the AEP HTTP server.
 *
 * <p>Starts a live {@link AepHttpServer} on a random port and asserts that the
 * {@link AepSecurityFilter} injects the expected security headers and enforces
 * payload-size limits. Rate-limiting is not tested here because it would require
 * very tight timing control — it is covered by unit tests in
 * {@link AepInputValidatorTest}.
 *
 * @doc.type class
 * @doc.purpose Security integration tests: headers, CORS, payload enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP HTTP Security")
class AepSecurityTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // =========================================================================
    // Security Headers
    // =========================================================================

    @Nested
    @DisplayName("Security Headers")
    class SecurityHeaderTests {

        @Test
        @DisplayName("GET /health includes X-Content-Type-Options: nosniff")
        void get_health_hasNoSniff() throws Exception {
            HttpResponse<String> resp = get("/health");
            assertThat(resp.headers().firstValue("X-Content-Type-Options"))
                    .hasValue("nosniff");
        }

        @Test
        @DisplayName("GET /health includes X-Frame-Options: DENY")
        void get_health_hasXFrameOptionsDeny() throws Exception {
            HttpResponse<String> resp = get("/health");
            assertThat(resp.headers().firstValue("X-Frame-Options"))
                    .hasValue("DENY");
        }

        @Test
        @DisplayName("GET /health includes Strict-Transport-Security header")
        void get_health_hasHsts() throws Exception {
            HttpResponse<String> resp = get("/health");
            assertThat(resp.headers().firstValue("Strict-Transport-Security"))
                    .isPresent()
                    .hasValueSatisfying(v -> assertThat(v).contains("max-age=31536000"));
        }

        @Test
        @DisplayName("GET /health includes Content-Security-Policy with frame-ancestors none")
        void get_health_hasCsp() throws Exception {
            HttpResponse<String> resp = get("/health");
            assertThat(resp.headers().firstValue("Content-Security-Policy"))
                    .isPresent()
                    .hasValueSatisfying(v -> assertThat(v).contains("frame-ancestors 'none'"));
        }

        @Test
        @DisplayName("GET /health includes Referrer-Policy header")
        void get_health_hasReferrerPolicy() throws Exception {
            HttpResponse<String> resp = get("/health");
            assertThat(resp.headers().firstValue("Referrer-Policy"))
                    .isPresent();
        }

        @Test
        @DisplayName("GET /health includes X-Request-Id header")
        void get_health_hasXRequestId() throws Exception {
            HttpResponse<String> resp = get("/health");
            assertThat(resp.headers().firstValue("X-Request-Id"))
                    .isPresent();
        }
    }

    // =========================================================================
    // Payload Size Limit
    // =========================================================================

    @Nested
    @DisplayName("Payload Size Limit")
    class PayloadSizeTests {

        @Test
        @DisplayName("POST with oversized payload → 413 Payload Too Large")
        void post_oversizedPayload_returns413() throws Exception {
            // Send a body that is 1 byte over the enforced 16 MiB limit
            // (AepInputValidator.MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024).
            // The security filter loads up to (MAX+1) bytes and rejects the request
            // with 413 when the returned buffer equals MAX+1 (body ≥ limit+1).
            byte[] oversized = new byte[16 * 1024 * 1024 + 1]; // 16 MiB + 1 byte
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/events"))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(oversized))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(413);
        }

        @Test
        @DisplayName("POST with valid-size payload is forwarded to router")
        void post_validPayload_isForwarded() throws Exception {
            String body = "{\"tenantId\":\"t1\",\"type\":\"click\",\"payload\":{\"k\":\"v\"}}";
            HttpResponse<String> resp = post("/api/v1/events", body);
            // Any response other than 413 is acceptable here (could be 200 or 400)
            assertThat(resp.statusCode()).isNotEqualTo(413);
        }
    }

    // =========================================================================
    // CORS Preflight
    // =========================================================================

    @Nested
    @DisplayName("CORS Preflight")
    class CorsTests {

        @Test
        @DisplayName("OPTIONS preflight returns 204 with CORS headers")
        void options_preflight_returns204WithCorsHeaders() throws Exception {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/events"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "POST")
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(204);
            assertThat(resp.headers().firstValue("Access-Control-Allow-Methods"))
                    .isPresent();
        }
    }

    // =========================================================================
    // Input Validation (delegated to AepInputValidator)
    // =========================================================================

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("POST /api/v1/events with injection payload → 400")
        void post_withInjectionPayload_returns400() throws Exception {
            // payload with script-injection pattern — validator should reject
            String malicious = "{\"tenantId\":\"t1\",\"type\":\"click\",\"payload\":"
                    + "{\"data\":\"<script>alert(1)</script>\"}}";
            HttpResponse<String> resp = post("/api/v1/events", malicious);

            // The security filter + input validator will reject invalid payloads
            // (exact code depends on validation path; 400 or 422 expected)
            assertThat(resp.statusCode()).isIn(400, 422);
        }

        @Test
        @DisplayName("POST /api/v1/events with valid payload → 200")
        void post_withValidPayload_returns200() throws Exception {
            String valid = "{\"tenantId\":\"tenant-123\",\"type\":\"user-click\","
                    + "\"payload\":{\"itemId\":\"abc123\",\"screen\":\"home\"}}";
            HttpResponse<String> resp = post("/api/v1/events", valid);

            assertThat(resp.statusCode()).isEqualTo(200);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try (var socket = new java.net.Socket("localhost", port)) {
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new RuntimeException("Server did not start on port " + port + " within 5 s");
    }
}
