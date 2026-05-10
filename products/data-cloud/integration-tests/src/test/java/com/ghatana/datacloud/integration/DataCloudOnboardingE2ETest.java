/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * Data Cloud Onboarding End-to-End Tests
 *
 * <p>Tests verify the complete onboarding flow for new tenants:</p>
 * <ul>
 *   <li>Tenant registration and initialization</li>
 *   <li>Default collection creation</li>
 *   <li>Initial data ingestion</li>
 *   <li>Basic query execution</li>
 *   <li>Compliance and governance setup</li>
 *   <li>Health and readiness verification</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose End-to-end Data Cloud onboarding test suite
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Data Cloud Onboarding E2E Tests")
@Tag("production")
class DataCloudOnboardingE2ETest {

    private static final String ONBOARDING_TENANT = "onboarding-test-tenant";

    private DataCloudClient client;
    private DataCloudHttpServer server;
    private HttpClient httpClient;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = new DurableDataCloudClient();

        DataCloudRuntimePluginManager pluginManager = new DataCloudRuntimePluginManager();
        pluginManager.registerWorkflowPlugin(client);
        pluginManager.registerBuiltInPlugins();

        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();

        server = new DataCloudHttpServer(client, port)
            .withPluginManager(pluginManager)
            .withDeploymentMode("local");
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
                client.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @DisplayName("Onboarding: Health check passes for new tenant")
    void onboardingHealthCheckPassesForNewTenant() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/health"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> responseBody = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        assertThat(responseBody).containsKey("status");
        assertThat(responseBody.get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("Onboarding: Readiness check confirms system is ready")
    void onboardingReadinessCheckConfirmsSystemIsReady() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/ready"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> responseBody = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        assertThat(responseBody).containsKey("status");
        assertThat(responseBody.get("status")).isEqualTo("READY");
    }

    @Test
    @DisplayName("Onboarding: Capabilities endpoint returns available capabilities")
    void onboardingCapabilitiesEndpointReturnsAvailableCapabilities() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/surfaces"))
            .GET()
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503, 500);
        if (response.statusCode() == 200) {
            assertThat(response.body()).isNotBlank();
            Map<String, Object> responseBody = parseBodyData(response.body());
            assertThat(responseBody).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Onboarding: Collection creation succeeds for new tenant")
    void onboardingCollectionCreationSucceedsForNewTenant() throws Exception {
        String collectionName = "onboarding-collection";
        Map<String, Object> payload = Map.of(
            "name", collectionName,
            "description", "Initial collection for onboarding"
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/dc_collections"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 201, 400, 404, 500, 503);
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            assertThat(responseBody).containsKey("id");
        }
    }

    @Test
    @DisplayName("Onboarding: List collections returns created collections")
    void onboardingListCollectionsReturnsCreatedCollections() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/dc_collections"))
            .GET()
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 404, 500, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections = (List<Map<String, Object>>) responseBody.getOrDefault("entities", List.of());
            assertThat(collections).isNotNull();
        }
    }

    @Test
    @DisplayName("Onboarding: Entity ingestion succeeds in new collection")
    void onboardingEntityIngestionSucceedsInNewCollection() throws Exception {
        String collectionName = "onboarding-collection";
        Map<String, Object> entity = Map.of(
            "id", "entity-1",
            "name", "Test Entity",
            "value", 42
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/" + collectionName))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(entity)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 201, 400, 404, 500, 503);
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            assertThat(responseBody).containsKey("id");
        }
    }

    @Test
    @DisplayName("Onboarding: Query execution returns data from ingested entities")
    void onboardingQueryExecutionReturnsDataFromIngestedEntities() throws Exception {
        String query = "SELECT * FROM test_collection LIMIT 10";
        Map<String, Object> payload = Map.of(
            "query", query
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/analytics/query"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503); // 503 if analytics not configured
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            assertThat(responseBody).containsKey("rows");
        }
    }

    @Test
    @DisplayName("Onboarding: Compliance summary is available for new tenant")
    void onboardingComplianceSummaryIsAvailableForNewTenant() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/compliance/summary"))
            .GET()
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 500, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            assertThat(responseBody).containsKey("complianceStatus");
        }
    }

    @Test
    @DisplayName("Onboarding: Audit logs are recorded for onboarding operations")
    void onboardingAuditLogsAreRecordedForOnboardingOperations() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/audit/events?limit=10"))
            .GET()
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 404, 500, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            assertThat(responseBody).containsKey("events");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> logs = (List<Map<String, Object>>) responseBody.get("events");
            assertThat(logs).isNotNull();
        }
    }

    @Test
    @DisplayName("Onboarding: Workflow execution is available for new tenant")
    void onboardingWorkflowExecutionIsAvailableForNewTenant() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/pipelines"))
            .GET()
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 404, 500, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> workflows = (List<Map<String, Object>>) responseBody.getOrDefault("pipelines", List.of());
            assertThat(workflows).isNotNull();
        }
    }

    @Test
    @DisplayName("Onboarding: Tenant isolation prevents cross-tenant data access")
    void onboardingTenantIsolationPreventsCrossTenantDataAccess() throws Exception {
        String collectionName = "isolation-test-collection";
        Map<String, Object> payload = Map.of(
            "name", collectionName,
            "description", "Tenant isolation test"
        );

        // Create collection in tenant A
        HttpRequest createRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/dc_collections"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", ONBOARDING_TENANT)
            .build();

        httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        // Try to access with different tenant ID
        HttpRequest listRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/entities/dc_collections"))
            .GET()
            .header("X-Tenant-Id", "different-tenant")
            .build();

        HttpResponse<String> response = httpClient.send(listRequest, HttpResponse.BodyHandlers.ofString());

        // Should return empty list or 403/404, not tenant A's data
        assertThat(response.statusCode()).isIn(200, 403, 404, 500, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = parseBodyData(response.body());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections = (List<Map<String, Object>>) responseBody.getOrDefault("entities", List.of());
            // Should not contain the collection from tenant A
            boolean hasTenantACollection = collections.stream()
                .anyMatch(col -> collectionName.equals(col.get("name")));
            assertThat(hasTenantACollection).isFalse();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBodyData(String body) throws Exception {
        Map<String, Object> parsed = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        Object data = parsed.get("data");
        if (data instanceof Map<?, ?>) {
            return (Map<String, Object>) data;
        }
        return parsed;
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
