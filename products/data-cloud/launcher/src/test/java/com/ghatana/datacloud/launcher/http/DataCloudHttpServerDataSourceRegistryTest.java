/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data Cloud HTTP data source registry endpoints.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/data-sources/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Data Source Registry Endpoints")
@Disabled("Data source registry routes not implemented in current server - requires route implementation")
class DataCloudHttpServerDataSourceRegistryTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ==================== Helper Methods ====================

    protected static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
    }

    protected HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("X-Tenant-Id", "test-tenant")
            .GET()
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("X-Tenant-Id", "test-tenant")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // ==================== Data Fabric Metrics Tests (TEST-1) ====================

    @Nested
    @DisplayName("GET /api/v1/data-sources/fabric/metrics")
    class FabricMetricsTests {

        @Test
        @DisplayName("returns 200 with fabric metrics when client is available")
        void getFabricMetrics_returns200WithMetrics() throws Exception {
            // TEST-1: Data Fabric API integration test
            startServer();

            // Mock the client query for DC_CONNECTIONS
            lenient().when(mockClient.query(anyString(), anyString(), any()))
                .thenReturn(Promise.of(List.of()));

            HttpResponse<String> resp = get("/api/v1/data-sources/fabric/metrics");
            assertThat(resp.statusCode()).isEqualTo(200);

            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.containsKey("tiers")).isTrue();
            assertThat(body.containsKey("totalEventsPerSec")).isTrue();
            assertThat(body.containsKey("totalStorageGb")).isTrue();
            assertThat(body.containsKey("lastUpdated")).isTrue();
        }

        @Test
        @DisplayName("returns 400 when X-Tenant-Id header is missing")
        void getFabricMetrics_missingTenantHeader_returns400() throws Exception {
            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/data-sources/fabric/metrics"))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns degraded response on client error")
        void getFabricMetrics_clientError_returnsDegradedResponse() throws Exception {
            startServer();

            // Mock client to throw an error
            lenient().when(mockClient.query(anyString(), anyString(), any()))
                .thenReturn(Promise.ofException(new RuntimeException("Database error")));

            HttpResponse<String> resp = get("/api/v1/data-sources/fabric/metrics");
            assertThat(resp.statusCode()).isEqualTo(200); // Should return 200 with degraded response

            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.containsKey("tiers")).isTrue();
        }
    }

    // ==================== Data Source Connection Tests ====================

    @Nested
    @DisplayName("GET /api/v1/data-sources")
    class ListConnectionsTests {

        @Test
        @DisplayName("returns 200 with connections list")
        void listConnections_returns200WithList() throws Exception {
            startServer();

            // Mock the client query for DC_CONNECTIONS
            lenient().when(mockClient.query(anyString(), anyString(), any()))
                .thenReturn(Promise.of(List.of()));

            HttpResponse<String> resp = get("/api/v1/data-sources");
            assertThat(resp.statusCode()).isEqualTo(200);

            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.containsKey("connections")).isTrue();
        }

        @Test
        @DisplayName("returns 400 when X-Tenant-Id header is missing")
        void listConnections_missingTenantHeader_returns400() throws Exception {
            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/data-sources"))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(400);
        }
    }
}
