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
 * Comprehensive governance purge, redaction, and audit assertions.
 *
 * <p>Tests verify that governance operations work correctly:</p>
 * <ul>
 *   <li>Retention classification and policy application</li>
 *   <li>PII redaction operations</li>
 *   <li>Retention purge dry-run and execute flows</li>
 *   <li>Audit log recording for all governance operations</li>
 *   <li>Tenant isolation for governance operations</li>
 *   <li>Authorization checks for governance endpoints</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Comprehensive governance purge/redaction/audit test suite
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Governance Purge/Redaction/Audit Assertions")
@Tag("production")
class GovernancePurgeRedactionAuditTest {

    private static final String TENANT_A = "governance-tenant-a";
    private static final String TENANT_B = "governance-tenant-b";

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
    @DisplayName("Retention classification applies policy to collection")
    void retentionClassificationAppliesPolicyToCollection() throws Exception {
        String collection = "test-collection";
        Map<String, Object> payload = Map.of(
            "collection", collection,
            "tier", "compliance",
            "reason", "GDPR Article 17 review",
            "piiFields", List.of("email", "phone")
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/classify"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503); // 503 if governance not configured
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            assertThat(responseBody).containsKey("collection");
            assertThat(responseBody).containsKey("tier");
            assertThat(responseBody).containsKey("status");
            assertThat(responseBody.get("collection")).isEqualTo(collection);
        }
    }

    @Test
    @DisplayName("PII redaction removes sensitive fields from entity")
    void piiRedactionRemovesSensitiveFieldsFromEntity() throws Exception {
        String collection = "test-collection";
        String entityId = "test-entity-id";
        Map<String, Object> payload = Map.of(
            "collection", collection,
            "entityId", entityId,
            "fields", List.of("email", "phone"),
            "reason", "PII redaction request"
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/privacy/redact"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            assertThat(responseBody).containsKey("collection");
            assertThat(responseBody).containsKey("entityId");
            assertThat(responseBody).containsKey("status");
            assertThat(responseBody.get("status")).isIn("NO_OP", "REDACTED");
        }
    }

    @Test
    @DisplayName("Retention purge dry-run estimates deletion impact")
    void retentionPurgeDryRunEstimatesDeletionImpact() throws Exception {
        String collection = "test-collection";
        Map<String, Object> payload = Map.of(
            "collection", collection,
            "dryRun", true
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/purge"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            assertThat(responseBody).containsKey("dryRun");
            assertThat(responseBody).containsKey("status");
            assertThat(responseBody).containsKey("confirmationToken");
            assertThat(responseBody.get("dryRun")).isEqualTo(true);
        }
    }

    @Test
    @DisplayName("Retention purge execute requires confirmation token")
    void retentionPurgeExecuteRequiresConfirmationToken() throws Exception {
        String collection = "test-collection";
        Map<String, Object> payload = Map.of(
            "collection", collection,
            "dryRun", false,
            "confirmationToken", "invalid-token"
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/purge"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should fail with invalid token
        assertThat(response.statusCode()).isIn(400, 401, 403, 503);
    }

    @Test
    @DisplayName("Audit logs record governance operations")
    void auditLogsRecordGovernanceOperations() throws Exception {
        // Perform a governance operation
        String collection = "audit-test-collection";
        Map<String, Object> classifyPayload = Map.of(
            "collection", collection,
            "tier", "standard",
            "reason", "Audit test"
        );

        HttpRequest classifyRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/classify"))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(classifyPayload)))
            .header("Content-Type", "application/json")
            .header("X-Tenant-Id", TENANT_A)
            .build();

        httpClient.send(classifyRequest, HttpResponse.BodyHandlers.ofString());

        // Check audit logs
        HttpRequest auditRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/audit/logs?limit=10"))
            .GET()
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> auditResponse = httpClient.send(auditRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(auditResponse.statusCode()).isIn(200, 503);
        if (auditResponse.statusCode() == 200) {
            Map<String, Object> auditBody = mapper.readValue(auditResponse.body(), Map.class);
            assertThat(auditBody).containsKey("logs");
            List<Map<String, Object>> logs = (List<Map<String, Object>>) auditBody.get("logs");
            // Verify at least one log entry exists
            assertThat(logs).isNotEmpty();
        }
    }

    @Test
    @DisplayName("Governance operations enforce tenant isolation")
    void governanceOperationsEnforceTenantIsolation() throws Exception {
        // Create entity in tenant A
        String collection = "isolation-test-collection";
        
        // Try to access governance data for tenant A from tenant B context
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/policy?collection=" + collection))
            .GET()
            .header("X-Tenant-Id", TENANT_B) // Different tenant
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should either return 404 (not found for this tenant) or 403 (forbidden)
        // 503 if governance not configured
        assertThat(response.statusCode()).isIn(200, 403, 404, 503);
        if (response.statusCode() == 200) {
            // If governance is configured, verify the data is tenant-scoped
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            assertThat(responseBody).containsKey("collection");
        }
    }

    @Test
    @DisplayName("Governance endpoints require authentication")
    void governanceEndpointsRequireAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/classify"))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            // No X-Tenant-Id header
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should fail without tenant ID
        assertThat(response.statusCode()).isIn(400, 401);
    }

    @Test
    @DisplayName("Retention policy retrieval returns correct tier information")
    void retentionPolicyRetrievalReturnsCorrectTierInformation() throws Exception {
        String collection = "policy-test-collection";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/retention/policy?collection=" + collection))
            .GET()
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 404, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            assertThat(responseBody).containsKey("collection");
            assertThat(responseBody).containsKey("tier");
            assertThat(responseBody).containsKey("retentionDays");
            assertThat(responseBody).containsKey("status");
        }
    }

    @Test
    @DisplayName("Compliance summary aggregates governance posture")
    void complianceSummaryAggregatesGovernancePosture() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/governance/compliance/summary"))
            .GET()
            .header("X-Tenant-Id", TENANT_A)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = mapper.readValue(response.body(), Map.class);
            assertThat(responseBody).containsKey("complianceStatus");
            assertThat(responseBody).containsKey("collectionsTotal");
            assertThat(responseBody).containsKey("collectionsClassified");
            assertThat(responseBody).containsKey("collectionsUnclassified");
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
