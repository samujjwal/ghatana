/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.security;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.http.AepHttpServer;
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
@DisplayName("AEP HTTP Security [GH-90000]")
class AepSecurityTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
        httpClient = HttpClient.newBuilder() // GH-90000
                .followRedirects(HttpClient.Redirect.NEVER) // GH-90000
                .build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // =========================================================================
    // Security Headers
    // =========================================================================

    @Nested
    @DisplayName("Security Headers [GH-90000]")
    class SecurityHeaderTests {

        @Test
        @DisplayName("GET /health includes X-Content-Type-Options: nosniff [GH-90000]")
        void get_health_hasNoSniff() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");
            assertThat(resp.headers().firstValue("X-Content-Type-Options [GH-90000]"))
                    .hasValue("nosniff [GH-90000]");
        }

        @Test
        @DisplayName("GET /health includes X-Frame-Options: DENY [GH-90000]")
        void get_health_hasXFrameOptionsDeny() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");
            assertThat(resp.headers().firstValue("X-Frame-Options [GH-90000]"))
                    .hasValue("DENY [GH-90000]");
        }

        @Test
        @DisplayName("GET /health includes Strict-Transport-Security header [GH-90000]")
        void get_health_hasHsts() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");
            assertThat(resp.headers().firstValue("Strict-Transport-Security [GH-90000]"))
                    .isPresent() // GH-90000
                    .hasValueSatisfying(v -> assertThat(v).contains("max-age=31536000 [GH-90000]"));
        }

        @Test
        @DisplayName("GET /health includes Content-Security-Policy with frame-ancestors none [GH-90000]")
        void get_health_hasCsp() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");
            assertThat(resp.headers().firstValue("Content-Security-Policy [GH-90000]"))
                    .isPresent() // GH-90000
                    .hasValueSatisfying(v -> assertThat(v).contains("frame-ancestors 'none' [GH-90000]"));
        }

        @Test
        @DisplayName("GET /health includes Referrer-Policy header [GH-90000]")
        void get_health_hasReferrerPolicy() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");
            assertThat(resp.headers().firstValue("Referrer-Policy [GH-90000]"))
                    .isPresent(); // GH-90000
        }

        @Test
        @DisplayName("GET /health includes X-Request-Id header [GH-90000]")
        void get_health_hasXRequestId() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/health [GH-90000]");
            assertThat(resp.headers().firstValue("X-Request-Id [GH-90000]"))
                    .isPresent(); // GH-90000
        }
    }

    // =========================================================================
    // Payload Size Limit
    // =========================================================================

    @Nested
    @DisplayName("Payload Size Limit [GH-90000]")
    class PayloadSizeTests {

        @Test
        @DisplayName("POST with oversized payload → 413 Payload Too Large [GH-90000]")
        void post_oversizedPayload_returns413() throws Exception { // GH-90000
            // Send a body that is 1 byte over the enforced 16 MiB limit
            // (AepInputValidator.MAX_REQUEST_BODY_BYTES = 16 * 1024 * 1024). // GH-90000
            // The security filter loads up to (MAX+1) bytes and rejects the request // GH-90000
            // with 413 when the returned buffer equals MAX+1 (body ≥ limit+1). // GH-90000
            byte[] oversized = new byte[16 * 1024 * 1024 + 1]; // 16 MiB + 1 byte
            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .uri(URI.create("http://localhost:" + port + "/api/v1/events")) // GH-90000
                    .header("Content-Type", "application/octet-stream") // GH-90000
                    .POST(HttpRequest.BodyPublishers.ofByteArray(oversized)) // GH-90000
                    .build(); // GH-90000

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(413); // GH-90000
        }

        @Test
        @DisplayName("POST with valid-size payload is forwarded to router [GH-90000]")
        void post_validPayload_isForwarded() throws Exception { // GH-90000
            String body = "{\"tenantId\":\"t1\",\"type\":\"click\",\"payload\":{\"k\":\"v\"}}";
            HttpResponse<String> resp = post("/api/v1/events", body); // GH-90000
            // Any response other than 413 is acceptable here (could be 200 or 400) // GH-90000
            assertThat(resp.statusCode()).isNotEqualTo(413); // GH-90000
        }
    }

    // =========================================================================
    // CORS Preflight
    // =========================================================================

    @Nested
    @DisplayName("CORS Preflight [GH-90000]")
    class CorsTests {

        @Test
        @DisplayName("OPTIONS preflight returns 204 with CORS headers [GH-90000]")
        void options_preflight_returns204WithCorsHeaders() throws Exception { // GH-90000
            HttpRequest req = HttpRequest.newBuilder() // GH-90000
                    .uri(URI.create("http://localhost:" + port + "/api/v1/events")) // GH-90000
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()) // GH-90000
                    .header("Origin", "http://localhost:3000") // GH-90000
                    .header("Access-Control-Request-Method", "POST") // GH-90000
                    .build(); // GH-90000

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(204); // GH-90000
            assertThat(resp.headers().firstValue("Access-Control-Allow-Methods [GH-90000]"))
                    .isPresent(); // GH-90000
        }
    }

    // =========================================================================
    // Input Validation (delegated to AepInputValidator) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Input Validation [GH-90000]")
    class InputValidationTests {

        @Test
        @DisplayName("POST /api/v1/events with injection payload → 400 [GH-90000]")
        void post_withInjectionPayload_returns400() throws Exception { // GH-90000
            // payload with script-injection pattern — validator should reject
            String malicious = "{\"tenantId\":\"t1\",\"type\":\"click\",\"payload\":"
                    + "{\"data\":\"<script>alert(1)</script>\"}}"; // GH-90000
            HttpResponse<String> resp = post("/api/v1/events", malicious); // GH-90000

            // The security filter + input validator will reject invalid payloads
            // (exact code depends on validation path; 400 or 422 expected) // GH-90000
            assertThat(resp.statusCode()).isIn(400, 422); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/events with valid payload → 200 [GH-90000]")
        void post_withValidPayload_returns200() throws Exception { // GH-90000
            String valid = "{\"tenantId\":\"tenant-123\",\"type\":\"user-click\","
                    + "\"payload\":{\"itemId\":\"abc123\",\"screen\":\"home\"}}";
            HttpResponse<String> resp = post("/api/v1/events", valid); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .GET() // GH-90000
                .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket s = new ServerSocket(0)) { // GH-90000
            return s.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try (var socket = new java.net.Socket("localhost", port)) { // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new RuntimeException("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
