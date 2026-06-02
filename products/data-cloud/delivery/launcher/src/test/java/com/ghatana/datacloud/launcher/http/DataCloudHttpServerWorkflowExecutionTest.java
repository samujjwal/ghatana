/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability.ExecutionSnapshot;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability.ExecutionLogEntry;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data-Cloud HTTP workflow execution endpoints.
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port with a mocked
 * {@link DataCloudClient} and mocked {@link WorkflowExecutionCapability}.
 * Covers pipeline execution lifecycle, cancellation, retry, rollback,
 * checkpoint management, and explain-query.
 *
 * @doc.type class
 * @doc.purpose Integration tests for workflow execution HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Workflow Execution Endpoints")
class DataCloudHttpServerWorkflowExecutionTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String PIPELINE_ID = "pipe-abc";
    private static final String EXECUTION_ID = "exec-123";
    private static final String TEST_PERMISSIONS = String.join(",",
            "action:pipeline:execute",
            "action:pipeline:read",
            "action:pipeline:cancel",
            "action:pipeline:retry",
            "action:pipeline:rollback",
            "action:pipeline:checkpoint",
            "action:checkpoint:read",
            "action:pipeline:restore",
            "action:query:explain");

    private DataCloudClient mockClient;
    private WorkflowExecutionCapability mockCapability;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        mockCapability = mock(WorkflowExecutionCapability.class);
        // Set up default mock returns to prevent NullPointerException
        when(mockCapability.execute(anyString(), anyString(), any()))
                .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "RUNNING")));
        when(mockCapability.cancelExecution(anyString(), anyString()))
                .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "CANCELLED")));
        when(mockCapability.retryExecution(anyString(), anyString()))
                .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "RUNNING")));
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    private void startServer() throws Exception {
        DataCloudFeatureFlags.override(DataCloudFeature.LEGACY_ACTION_ROUTES, true);
        server = new DataCloudHttpServer(mockClient, port)
                .withWorkflowExecutionCapability(mockCapability)
                .withDeploymentMode("local");
        server.start();
        waitForServerReady(port);
    }

    private void startServerWithoutCapability() throws Exception {
        server = new DataCloudHttpServer(mockClient, port)
                .withDeploymentMode("local");
        server.start();
        waitForServerReady(port);
    }

    // ==================== Helpers ====================

    private ExecutionSnapshot makeSnapshot(String id, String status) {
        return new ExecutionSnapshot(
                id, TENANT_ID, PIPELINE_ID, "Test Workflow",
                status, 100,
                "2026-05-01T10:00:00Z", "2026-05-01T10:05:00Z",
                300, List.of(), null, null,
                null, null, null, null, null, null, null, null);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return getWithTenant(path, TENANT_ID);
    }

    private HttpResponse<String> getWithTenant(String path, String tenantId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Tenant-Id", tenantId)
                .header("X-Permissions", TEST_PERMISSIONS)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) throws Exception {
        String json = mapper.writeValueAsString(body);
        return postRaw(path, json, TENANT_ID);
    }

    private HttpResponse<String> postEmpty(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Permissions", TEST_PERMISSIONS)
                .header("Content-Type", "application/json")
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postRaw(String path, String body, String tenantId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", tenantId)
                .header("X-Permissions", TEST_PERMISSIONS)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Permissions", TEST_PERMISSIONS)
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    // ==================== POST /api/v1/action/pipelines/{pipelineId}/execute ====================

    @Nested
    @DisplayName("POST /api/v1/action/pipelines/{pipelineId}/execute – execute pipeline")
    class ExecutePipelineTests {

        @Test
        @DisplayName("returns 200 with executionId when capability present")
        void executePipeline_withCapability_returns200() throws Exception {
            when(mockCapability.execute(eq(TENANT_ID), eq(PIPELINE_ID), any()))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "RUNNING")));

            startServer();

            HttpResponse<String> resp = post(
                    "/api/v1/action/pipelines/" + PIPELINE_ID + "/execute",
                    Map.of("param1", "value1"));

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKeys("executionId", "pipelineId", "tenantId", "status");
            assertThat(body.get("executionId")).isEqualTo(EXECUTION_ID);
            assertThat(body.get("status")).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("returns 200 with empty body (no input params)")
        void executePipeline_emptyBody_returns200() throws Exception {
            when(mockCapability.execute(eq(TENANT_ID), eq(PIPELINE_ID), any()))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "RUNNING")));

            startServer();

            HttpResponse<String> resp = postEmpty("/api/v1/action/pipelines/" + PIPELINE_ID + "/execute");

            assertThat(resp.statusCode()).isEqualTo(200);
            verify(mockCapability).execute(eq(TENANT_ID), eq(PIPELINE_ID), eq(Map.of()));
        }

        @Test
        @DisplayName("returns 503 when capability not present")
        void executePipeline_noCapability_returns501() throws Exception {
            startServerWithoutCapability();

            HttpResponse<String> resp = post(
                    "/api/v1/action/pipelines/" + PIPELINE_ID + "/execute",
                    Map.of());

            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("returns 200 when tenant header is missing in local profile")
        void executePipeline_missingTenant_returns200InLocalProfile() throws Exception {
            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/action/pipelines/" + PIPELINE_ID + "/execute"))
                    .header("Content-Type", "application/json")
                    .header("X-Permissions", TEST_PERMISSIONS)
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
        }
    }

    // ==================== GET /api/v1/action/pipelines/{pipelineId}/executions ====================

    @Nested
    @DisplayName("GET /api/v1/action/pipelines/{pipelineId}/executions – list executions")
    class ListExecutionsTests {

        @Test
        @DisplayName("returns 200 with execution list when capability present")
        void listExecutions_withCapability_returns200() throws Exception {
            when(mockCapability.listExecutions(eq(TENANT_ID), eq(PIPELINE_ID)))
                    .thenReturn(Promise.of(List.of(
                            makeSnapshot(EXECUTION_ID, "COMPLETED"),
                            makeSnapshot("exec-456", "FAILED"))));

            startServer();

            HttpResponse<String> resp = get("/api/v1/action/pipelines/" + PIPELINE_ID + "/executions");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKeys("executions", "count");
            assertThat(((List<?>) body.get("executions"))).hasSize(2);
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 503 with typed unavailable state when capability is absent")
        void listExecutions_noCapability_returnsUnavailable() throws Exception {
            startServerWithoutCapability();

            HttpResponse<String> resp = get("/api/v1/action/pipelines/" + PIPELINE_ID + "/executions");

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsEntry("status", "unavailable");
            assertThat(body).containsEntry("capability", "workflow-execution");
            assertThat(body).containsEntry("runtimeState", "UNAVAILABLE");
        }
    }

    // ==================== GET /api/v1/action/pipelines/{pipelineId}/executions/{executionId} ====================

    @Nested
    @DisplayName("GET /api/v1/action/pipelines/{pipelineId}/executions/{id} – get execution")
    class GetPipelineExecutionTests {

        @Test
        @DisplayName("returns 200 with execution snapshot when found")
        void getExecution_found_returns200() throws Exception {
            when(mockCapability.getExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(Optional.of(makeSnapshot(EXECUTION_ID, "COMPLETED"))));

            startServer();

            HttpResponse<String> resp = get(
                    "/api/v1/action/pipelines/" + PIPELINE_ID + "/executions/" + EXECUTION_ID);

            assertThat(resp.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 404 when execution not found")
        void getExecution_notFound_returns404() throws Exception {
            when(mockCapability.getExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(Optional.empty()));

            startServer();

            HttpResponse<String> resp = get(
                    "/api/v1/action/pipelines/" + PIPELINE_ID + "/executions/" + EXECUTION_ID);

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ==================== POST /api/v1/action/pipelines/{pipelineId}/executions/{executionId}/cancel ====================

    @Nested
    @DisplayName("POST /api/v1/action/pipelines/{pipelineId}/executions/{id}/cancel – cancel execution")
    class CancelPipelineExecutionTests {

        @Test
        @DisplayName("returns 200 with CANCELLED status when capability present")
        void cancelExecution_withCapability_returns200() throws Exception {
            when(mockCapability.cancelExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "CANCELLED")));

            startServer();

            HttpResponse<String> resp = postEmpty(
                    "/api/v1/action/pipelines/" + PIPELINE_ID + "/executions/" + EXECUTION_ID + "/cancel");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("CANCELLED");
            verify(mockCapability).cancelExecution(TENANT_ID, EXECUTION_ID);
        }

        @Test
        @DisplayName("returns 503 when capability not present")
        void cancelExecution_noCapability_returns503() throws Exception {
            startServerWithoutCapability();

            HttpResponse<String> resp = postEmpty(
                    "/api/v1/action/pipelines/" + PIPELINE_ID + "/executions/" + EXECUTION_ID + "/cancel");

            assertThat(resp.statusCode()).isEqualTo(503);
        }
    }

    // ==================== POST /api/v1/action/executions/{executionId}/cancel ====================

    @Nested
    @DisplayName("POST /api/v1/action/executions/{id}/cancel – cancel execution (flat route)")
    class CancelExecutionFlatTests {

        @Test
        @DisplayName("returns 200 with cancelled snapshot when capability present")
        void cancelExecution_flatRoute_returns200() throws Exception {
            when(mockCapability.cancelExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "CANCELLED")));

            startServer();

            HttpResponse<String> resp = postEmpty("/api/v1/action/executions/" + EXECUTION_ID + "/cancel");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("CANCELLED");
        }
    }

    // ==================== POST /api/v1/action/executions/{executionId}/retry ====================

    @Nested
    @DisplayName("POST /api/v1/action/executions/{id}/retry – retry execution")
    class RetryExecutionTests {

        @Test
        @DisplayName("returns 200 with new startedAt when capability present")
        void retryExecution_withCapability_returns200() throws Exception {
            when(mockCapability.retryExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "RUNNING")));

            startServer();

            HttpResponse<String> resp = postEmpty("/api/v1/action/executions/" + EXECUTION_ID + "/retry");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKeys("executionId", "status", "startedAt");
            assertThat(body.get("executionId")).isEqualTo(EXECUTION_ID);
        }

        @Test
        @DisplayName("returns 503 when capability not present")
        void retryExecution_noCapability_returns503() throws Exception {
            startServerWithoutCapability();

            HttpResponse<String> resp = postEmpty("/api/v1/action/executions/" + EXECUTION_ID + "/retry");

            assertThat(resp.statusCode()).isEqualTo(503);
        }
    }

    // ==================== POST /api/v1/action/executions/{executionId}/rollback ====================

    @Nested
    @DisplayName("POST /api/v1/action/executions/{id}/rollback – rollback execution")
    class RollbackExecutionTests {

        @Test
        @DisplayName("returns 200 with rolled_back status when capability present")
        void rollbackExecution_withCapability_returns200() throws Exception {
            when(mockCapability.cancelExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "CANCELLED")));

            startServer();

            HttpResponse<String> resp = postEmpty("/api/v1/action/executions/" + EXECUTION_ID + "/rollback");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("executionId")).isEqualTo(EXECUTION_ID);
            assertThat(body.get("status")).isEqualTo("rolled_back");
        }

        @Test
        @DisplayName("returns 200 with rollback metadata in body")
        void rollbackExecution_withJsonBody_returns200() throws Exception {
            when(mockCapability.cancelExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "CANCELLED")));

            startServer();

            HttpResponse<String> resp = post(
                    "/api/v1/action/executions/" + EXECUTION_ID + "/rollback",
                    Map.of("reason", "data-corruption", "targetVersion", "v1.2"));

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("rolled_back");
        }
    }

    // ==================== POST /api/v1/action/executions/{executionId}/checkpoint ====================

    @Nested
    @DisplayName("POST /api/v1/action/executions/{id}/checkpoint – create checkpoint")
    class CheckpointExecutionTests {

        @Test
        @DisplayName("returns 200 with checkpointId when client saves successfully")
        void createCheckpoint_returnsCheckpointId() throws Exception {
            when(mockClient.save(eq(TENANT_ID), eq("dc_execution_checkpoints"), any()))
                    .thenReturn(Promise.of(
                            DataCloudClient.Entity.of("cp-generated", "dc_execution_checkpoints", Map.of())));

            startServer();

            HttpResponse<String> resp = post(
                    "/api/v1/action/executions/" + EXECUTION_ID + "/checkpoint",
                    Map.of("state", "after-step-3"));

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("checkpointId")).isNotNull();
            assertThat(body.get("executionId")).isEqualTo(EXECUTION_ID);
            assertThat(body.get("status")).isEqualTo("checkpointed");
        }

        @Test
        @DisplayName("returns 200 with empty checkpoint body")
        void createCheckpoint_emptyBody_returns200() throws Exception {
            when(mockClient.save(eq(TENANT_ID), eq("dc_execution_checkpoints"), any()))
                    .thenReturn(Promise.of(
                            DataCloudClient.Entity.of("cp-gen", "dc_execution_checkpoints", Map.of())));

            startServer();

            HttpResponse<String> resp = postEmpty("/api/v1/action/executions/" + EXECUTION_ID + "/checkpoint");

            assertThat(resp.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 403 when tenant header and permissions are missing")
        void createCheckpoint_noTenant_returns403() throws Exception {
            startServer();

            HttpRequest req = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .uri(URI.create("http://127.0.0.1:" + port
                            + "/api/v1/action/executions/" + EXECUTION_ID + "/checkpoint"))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(403);
        }
    }

    // ==================== GET /api/v1/action/executions/{executionId}/checkpoints ====================

    @Nested
    @DisplayName("GET /api/v1/action/executions/{id}/checkpoints – list checkpoints")
    class ListExecutionCheckpointsTests {

        @Test
        @DisplayName("returns 200 with checkpoint list")
        void listCheckpoints_returns200() throws Exception {
            List<DataCloudClient.Entity> stored = List.of(
                    DataCloudClient.Entity.of("cp-1", "dc_execution_checkpoints",
                            Map.of("executionId", EXECUTION_ID)),
                    DataCloudClient.Entity.of("cp-2", "dc_execution_checkpoints",
                            Map.of("executionId", EXECUTION_ID)));
            when(mockClient.query(eq(TENANT_ID), eq("dc_execution_checkpoints"),
                    any(DataCloudClient.Query.class)))
                    .thenReturn(Promise.of(stored));

            startServer();

            HttpResponse<String> resp = get("/api/v1/action/executions/" + EXECUTION_ID + "/checkpoints");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKeys("checkpoints", "count");
            assertThat(((List<?>) body.get("checkpoints"))).hasSize(2);
        }

        @Test
        @DisplayName("returns 200 with empty list when no checkpoints stored")
        void listCheckpoints_empty_returns200() throws Exception {
            when(mockClient.query(eq(TENANT_ID), eq("dc_execution_checkpoints"),
                    any(DataCloudClient.Query.class)))
                    .thenReturn(Promise.of(List.of()));

            startServer();

            HttpResponse<String> resp = get("/api/v1/action/executions/" + EXECUTION_ID + "/checkpoints");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(((List<?>) body.get("checkpoints"))).isEmpty();
        }
    }

    // ==================== POST /api/v1/action/executions/{executionId}/restore ====================

    @Nested
    @DisplayName("POST /api/v1/action/executions/{id}/restore – restore execution")
    class RestoreExecutionTests {

        @Test
        @DisplayName("returns 200 with restored execution when capability present")
        void restoreExecution_withCapability_returns200() throws Exception {
            when(mockCapability.retryExecution(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(makeSnapshot(EXECUTION_ID, "RUNNING")));

            startServer();

            HttpResponse<String> resp = post(
                    "/api/v1/action/executions/" + EXECUTION_ID + "/restore",
                    Map.of("checkpointId", "cp-99"));

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("executionId")).isEqualTo(EXECUTION_ID);
        }

        @Test
        @DisplayName("returns 503 when capability not present")
        void restoreExecution_noCapability_returns503() throws Exception {
            startServerWithoutCapability();

            HttpResponse<String> resp = post(
                    "/api/v1/action/executions/" + EXECUTION_ID + "/restore",
                    Map.of("checkpointId", "cp-99"));

            assertThat(resp.statusCode()).isEqualTo(503);
        }
    }

    // ==================== GET /api/v1/action/executions/{executionId}/logs ====================

    @Nested
    @DisplayName("GET /api/v1/action/executions/{id}/logs – get execution logs")
    class GetExecutionLogsTests {

        @Test
        @DisplayName("returns 200 with logs when capability present")
        void getLogs_withCapability_returns200() throws Exception {
            List<ExecutionLogEntry> logs = List.of(
                    new ExecutionLogEntry("2026-05-01T10:00:00Z", "INFO", "Step started", "node-1", Map.of()),
                    new ExecutionLogEntry("2026-05-01T10:01:00Z", "INFO", "Step complete", "node-1", Map.of()));
            when(mockCapability.getExecutionLogs(eq(TENANT_ID), eq(EXECUTION_ID)))
                    .thenReturn(Promise.of(logs));

            startServer();

            HttpResponse<String> resp = get("/api/v1/action/executions/" + EXECUTION_ID + "/logs");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKeys("logs", "executionId");
            assertThat(((List<?>) body.get("logs"))).hasSize(2);
        }

        @Test
        @DisplayName("returns 503 with typed unavailable state when capability absent")
        void getLogs_noCapability_returnsUnavailable() throws Exception {
            startServerWithoutCapability();

            HttpResponse<String> resp = get("/api/v1/action/executions/" + EXECUTION_ID + "/logs");

            assertThat(resp.statusCode()).isEqualTo(503);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsEntry("status", "unavailable");
            assertThat(body).containsEntry("capability", "workflow-execution");
            assertThat(body).containsEntry("runtimeState", "UNAVAILABLE");
        }
    }

    // ==================== POST /api/v1/queries/explain ====================

    @Nested
    @DisplayName("POST /api/v1/queries/explain – explain query plan")
    class ExplainQueryTests {

        @Test
        @DisplayName("returns 200 with query plan when type and collections provided")
        void explainQuery_returns200WithPlan() throws Exception {
            startServer();

            HttpResponse<String> resp = post("/api/v1/queries/explain",
                    Map.of("type", "SELECT", "collections", List.of("orders", "customers")));

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKeys("queryId", "queryType", "dataSources",
                    "estimatedCost", "optimized", "explain", "timestamp");
            assertThat(body.get("queryType")).isEqualTo("SELECT");
        }

        @Test
        @DisplayName("returns 200 with UNKNOWN type when type not provided in body")
        void explainQuery_missingType_returnsUnknown() throws Exception {
            startServer();

            HttpResponse<String> resp = post("/api/v1/queries/explain", Map.of());

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("queryId");
        }
    }

    // ==================== Infrastructure ====================

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitForServerReady(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                new Socket("127.0.0.1", port).close();
                return;
            } catch (IOException ignored) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port);
    }
}
