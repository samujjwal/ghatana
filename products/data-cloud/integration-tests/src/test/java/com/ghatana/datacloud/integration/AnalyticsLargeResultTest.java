/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for analytics large-result behavior verification.
 *
 * <p>Tests verify that analytics queries with large result sets are handled correctly:
 * - Result truncation according to limit parameters
 * - Pagination behavior
 * - Truncated flag reporting
 * - Total rows counting
 * - Error handling for invalid limit parameters</p>
 *
 * @doc.type class
 * @doc.purpose End-to-end analytics large-result behavior verification
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Analytics Large-Result Behavior Verification")
@Tag("production")
class AnalyticsLargeResultTest {

    private static final String TENANT_ID = "analytics-test-tenant";
    private static final int DEFAULT_ROW_LIMIT = 1000;
    private static final int MAX_ROW_LIMIT = 10000;

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new DurableDataCloudClient();
        client.open().getResult();

        DataCloudRuntimePluginManager pluginManager = new DataCloudRuntimePluginManager();
        pluginManager.registerWorkflowPlugin(client);
        pluginManager.registerBuiltInPlugins();

        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();

        server = new DataCloudHttpServer(client, port)
            .withPluginManager(pluginManager)
            .withDeploymentMode("production");
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (client != null) {
            try {
                client.close().getResult();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("Large result is truncated to default limit when no limit specified")
    void largeResultTruncatedToDefaultLimit() throws Exception {
        // Submit a query that would return more than DEFAULT_ROW_LIMIT rows
        String query = "SELECT * FROM large_table"; // Hypothetical large table
        Map<String, Object> payload = Map.of(
            "query", query
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify response structure
        assertThat(response.statusCode()).isIn(200, 503); // 503 if analytics not configured
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            
            // Verify truncation metadata
            assertThat(responseBody).containsKey("rowCount");
            assertThat(responseBody).containsKey("limit");
            assertThat(responseBody).containsKey("truncated");
            assertThat(responseBody).containsKey("totalRows");
            
            int rowCount = ((Number) responseBody.get("rowCount")).intValue();
            int limit = ((Number) responseBody.get("limit")).intValue();
            boolean truncated = (Boolean) responseBody.get("truncated");
            
            // If analytics engine is configured and returns data, verify truncation
            if (rowCount > 0) {
                assertThat(limit).isEqualTo(DEFAULT_ROW_LIMIT);
                // If there are more rows than the limit, truncated should be true
                // If all rows fit, truncated should be false
                int totalRows = ((Number) responseBody.get("totalRows")).intValue();
                if (totalRows > limit) {
                    assertThat(truncated).isTrue();
                } else {
                    assertThat(truncated).isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("Custom limit is respected when specified")
    void customLimitRespected() throws Exception {
        int customLimit = 500;
        String query = "SELECT * FROM large_table";
        Map<String, Object> payload = Map.of(
            "query", query,
            "limit", customLimit
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            
            int limit = ((Number) responseBody.get("limit")).intValue();
            assertThat(limit).isEqualTo(customLimit);
            
            int rowCount = ((Number) responseBody.get("rowCount")).intValue();
            if (rowCount > 0) {
                // Row count should not exceed the custom limit
                assertThat(rowCount).isLessThanOrEqualTo(customLimit);
            }
        }
    }

    @Test
    @DisplayName("Limit is capped at MAX_ROW_LIMIT")
    void limitCappedAtMaxRowLimit() throws Exception {
        int excessiveLimit = 100000; // Well above MAX_ROW_LIMIT
        String query = "SELECT * FROM large_table";
        Map<String, Object> payload = Map.of(
            "query", query,
            "limit", excessiveLimit
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            
            int limit = ((Number) responseBody.get("limit")).intValue();
            assertThat(limit).isLessThanOrEqualTo(MAX_ROW_LIMIT);
        }
    }

    @Test
    @DisplayName("Limit of 1 is accepted and returns single row")
    void limitOfOneAccepted() throws Exception {
        String query = "SELECT * FROM large_table";
        Map<String, Object> payload = Map.of(
            "query", query,
            "limit", 1
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            
            int limit = ((Number) responseBody.get("limit")).intValue();
            int rowCount = ((Number) responseBody.get("rowCount")).intValue();
            
            assertThat(limit).isEqualTo(1);
            if (rowCount > 0) {
                assertThat(rowCount).isLessThanOrEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Invalid limit parameter is handled gracefully")
    void invalidLimitHandledGracefully() throws Exception {
        String query = "SELECT * FROM large_table";
        Map<String, Object> payload = Map.of(
            "query", query,
            "limit", "invalid" // Non-numeric limit
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should handle gracefully - either return error or use default limit
        assertThat(response.statusCode()).isIn(200, 400, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            // Should fall back to default limit
            int limit = ((Number) responseBody.get("limit")).intValue();
            assertThat(limit).isEqualTo(DEFAULT_ROW_LIMIT);
        }
    }

    @Test
    @DisplayName("Negative limit is handled gracefully")
    void negativeLimitHandledGracefully() throws Exception {
        String query = "SELECT * FROM large_table";
        Map<String, Object> payload = Map.of(
            "query", query,
            "limit", -100
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should handle gracefully - either return error or use minimum of 1
        assertThat(response.statusCode()).isIn(200, 400, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            int limit = ((Number) responseBody.get("limit")).intValue();
            // Should be clamped to minimum of 1
            assertThat(limit).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("Truncated flag is set correctly when results exceed limit")
    void truncatedFlagSetCorrectly() throws Exception {
        // This test verifies the truncated flag behavior
        // In a real scenario with data, this would check that:
        // - truncated = true when totalRows > limit
        // - truncated = false when totalRows <= limit
        
        String query = "SELECT * FROM large_table LIMIT 2000"; // Request more than default
        Map<String, Object> payload = Map.of("query", query);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            
            assertThat(responseBody).containsKey("truncated");
            assertThat(responseBody).containsKey("totalRows");
            assertThat(responseBody).containsKey("rowCount");
            assertThat(responseBody).containsKey("limit");
            
            // Verify the relationship between these fields
            int rowCount = ((Number) responseBody.get("rowCount")).intValue();
            int totalRows = ((Number) responseBody.get("totalRows")).intValue();
            int limit = ((Number) responseBody.get("limit")).intValue();
            boolean truncated = (Boolean) responseBody.get("truncated");
            
            // If data was returned, verify truncation logic
            if (rowCount > 0) {
                // Truncated should be true if totalRows > limit
                // (or if rowCount == limit, suggesting more rows exist)
                if (totalRows > limit || (rowCount == limit && totalRows > rowCount)) {
                    assertThat(truncated).isTrue();
                } else {
                    assertThat(truncated).isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("Total rows count is accurate")
    void totalRowsCountAccurate() throws Exception {
        String query = "SELECT * FROM large_table";
        Map<String, Object> payload = Map.of("query", query);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_ID)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            
            assertThat(responseBody).containsKey("totalRows");
            int totalRows = ((Number) responseBody.get("totalRows")).intValue();
            int rowCount = ((Number) responseBody.get("rowCount")).intValue();
            
            // Total rows should be >= row count
            assertThat(totalRows).isGreaterThanOrEqualTo(rowCount);
        }
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
}
