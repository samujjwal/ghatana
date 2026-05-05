/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.aep.security.AepSecurityFilter;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AEP Gateway Integration Tests (JWT, CORS, Tenant Isolation)
 *
 * <p>Tests verify full gateway security and routing:</p>
 * <ul>
 *   <li>JWT authentication (valid/invalid tokens, missing auth, expired tokens)</li>
 *   <li>CORS (allowed origins, preflight OPTIONS, credentials)</li>
 *   <li>Tenant isolation (tenant mismatch, cross-tenant rejection)</li>
 *   <li>Correlation ID propagation (X-Correlation-ID header, MDC propagation)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Comprehensive gateway security and routing integration tests
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("AEP Gateway Integration Tests")
@Tag("production")
class AepGatewayIntegrationTest {

    private AepEngine engine;
    private HttpClient httpClient;
    private int port;
    private static final String TEST_JWT_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String TEST_TENANT_A = "aep-tenant-a";
    private static final String TEST_TENANT_B = "aep-tenant-b";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://malicious-site.com";

    @BeforeEach
    void setUp() throws Exception {
        // Set up test environment with JWT secret
        System.setProperty("AEP_JWT_SECRET", TEST_JWT_SECRET);
        System.setProperty("AEP_ENV", "test");
        System.setProperty("AEP_CORS_ORIGINS", ALLOWED_ORIGIN);
        
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
        
        engine = Aep.create(Aep.AepConfig.defaults());
        // Note: Port configuration and engine lifecycle managed by AEP framework
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
        System.clearProperty("AEP_JWT_SECRET");
        System.clearProperty("AEP_ENV");
        System.clearProperty("AEP_CORS_ORIGINS");
    }

    // ==================== JWT Authentication Tests ====================

    @Test
    @DisplayName("Valid JWT token allows access to protected endpoints")
    void validJwtTokenAllowsAccess() throws Exception {
        String validToken = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Should return 200 or 404 (endpoint exists but data not found), not 401
        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("Invalid JWT token is rejected with 401")
    void invalidJwtTokenRejected() throws Exception {
        String invalidToken = "invalid.jwt.token";
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + invalidToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Missing JWT token is rejected for protected endpoints")
    void missingJwtTokenRejectedForProtectedEndpoints() throws Exception {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("X-Tenant-Id", TEST_TENANT_A)
            // No Authorization header
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Expired JWT token is rejected with 401")
    void expiredJwtTokenRejected() throws Exception {
        String expiredToken = generateExpiredJwtToken();
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + expiredToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("JWT with wrong tenant is rejected with 403")
    void jwtWithWrongTenantRejected() throws Exception {
        String tokenForTenantA = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + tokenForTenantA)
            .header("X-Tenant-Id", TEST_TENANT_B) // Different tenant in header
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Should return 403 for tenant mismatch
        assertThat(response.statusCode()).isIn(403, 401);
    }

    // ==================== CORS Tests ====================

    @Test
    @DisplayName("CORS preflight OPTIONS request returns proper headers")
    void corsPreflightOptionsHandled() throws Exception {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody())
            .header("Origin", ALLOWED_ORIGIN)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization")
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 204);
        assertThat(response.headers().map())
            .containsKey("access-control-allow-origin")
            .containsKey("access-control-allow-methods")
            .containsKey("access-control-allow-headers");
    }

    @Test
    @DisplayName("CORS allows configured origin")
    void corsAllowsConfiguredOrigin() throws Exception {
        String validToken = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Origin", ALLOWED_ORIGIN)
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.headers().map())
            .containsKey("access-control-allow-origin");
        assertThat(response.headers().firstValue("access-control-allow-origin").orElse(""))
            .isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    @DisplayName("CORS rejects disallowed origin")
    void corsRejectsDisallowedOrigin() throws Exception {
        String validToken = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Origin", DISALLOWED_ORIGIN)
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Should either not have CORS headers or reject the request
        assertThat(response.headers().allValues("access-control-allow-origin"))
            .doesNotContain(DISALLOWED_ORIGIN);
    }

    // ==================== Tenant Isolation Tests ====================

    @Test
    @DisplayName("Tenant mismatch is rejected with 403")
    void tenantMismatchRejected() throws Exception {
        String tokenForTenantA = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + tokenForTenantA)
            .header("X-Tenant-Id", TEST_TENANT_B) // Different tenant
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(403, 401);
    }

    @Test
    @DisplayName("Cross-tenant data access is prevented")
    void crossTenantDataAccessPrevented() throws Exception {
        String tokenForTenantA = generateValidJwtToken(TEST_TENANT_A);
        
        // Create a resource in tenant A
        java.net.http.HttpRequest createRequest = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"name\":\"test-pipeline\"}"))
            .header("Authorization", "Bearer " + tokenForTenantA)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .header("Content-Type", "application/json")
            .build();

        httpClient.send(createRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Try to access with tenant B token
        String tokenForTenantB = generateValidJwtToken(TEST_TENANT_B);
        java.net.http.HttpRequest readRequest = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + tokenForTenantB)
            .header("X-Tenant-Id", TEST_TENANT_B)
            .build();

        java.net.http.HttpResponse<String> readResponse = httpClient.send(readRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Tenant B should not see tenant A's resources
        assertThat(readResponse.statusCode()).isIn(200, 404);
        if (readResponse.statusCode() == 200) {
            // Verify the response doesn't contain tenant A's data
            assertThat(readResponse.body()).doesNotContain("test-pipeline");
        }
    }

    // ==================== Correlation ID Tests ====================

    @Test
    @DisplayName("Correlation ID is propagated from request header")
    void correlationIdPropagatedFromRequest() throws Exception {
        String correlationId = java.util.UUID.randomUUID().toString();
        String validToken = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("X-Correlation-Id", correlationId)
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Response should include the correlation ID
        assertThat(response.headers().firstValue("X-Correlation-Id"))
            .isPresent()
            .hasValue(correlationId);
    }

    @Test
    @DisplayName("Correlation ID is generated when missing from request")
    void correlationIdGeneratedWhenMissing() throws Exception {
        String validToken = generateValidJwtToken(TEST_TENANT_A);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("Authorization", "Bearer " + validToken)
            .header("X-Tenant-Id", TEST_TENANT_A)
            // No X-Correlation-Id header
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Response should include a generated correlation ID
        assertThat(response.headers().firstValue("X-Correlation-Id"))
            .isPresent();
    }

    @Test
    @DisplayName("Correlation ID is present in error responses")
    void correlationIdPresentInErrorResponse() throws Exception {
        String correlationId = java.util.UUID.randomUUID().toString();
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("X-Correlation-Id", correlationId)
            .header("X-Tenant-Id", TEST_TENANT_A)
            // No Authorization header - will trigger 401 error
            .build();

        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("X-Correlation-Id"))
            .isPresent()
            .hasValue(correlationId);
    }

    // ==================== Helper Methods ====================

    private static int findFreePort() throws java.io.IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new java.net.Socket("127.0.0.1", port).close();
                return;
            } catch (java.io.IOException ignored) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Server did not start within 10 seconds on port " + port);
    }

    private String generateValidJwtToken(String tenantId) {
        try {
            // JWT header
            String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            
            // JWT payload
            long now = Instant.now().getEpochSecond();
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                    "{\"sub\":\"user\",\"tenantId\":\"%s\",\"iat\":%d,\"exp\":%d}",
                    tenantId, now, now + 3600
                ).getBytes(StandardCharsets.UTF_8));
            
            // JWT signature
            String data = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String signatureEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            
            return data + "." + signatureEncoded;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    private String generateExpiredJwtToken() {
        try {
            // JWT header
            String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            
            // JWT payload with expired exp claim
            long past = Instant.now().minusSeconds(3600).getEpochSecond();
            String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                    "{\"sub\":\"user\",\"tenantId\":\"%s\",\"iat\":%d,\"exp\":%d}",
                    TEST_TENANT_A, past - 7200, past
                ).getBytes(StandardCharsets.UTF_8));
            
            // JWT signature
            String data = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String signatureEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            
            return data + "." + signatureEncoded;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate expired JWT token", e);
        }
    }
}
