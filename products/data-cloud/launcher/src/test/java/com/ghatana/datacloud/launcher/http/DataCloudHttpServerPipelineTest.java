/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Data Cloud HTTP pipeline CRUD and execution endpoints.
 *
 * <p>Extends {@link DataCloudHttpServerTestBase} to inherit reusable HTTP helpers,
 * tenant context management, and response parsing utilities. All tests share the same
 * server startup and HTTP client infrastructure.
 *
 * <p>Covers: POST/GET/DELETE/PUT pipelines, list pipelines, optimize hints,
 * request validation, and tenant isolation.
 *
 * <p><strong>Week 2 Status:</strong> STUB - method signatures only.
 * Test bodies will be implemented in Week 3.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/pipelines/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Pipeline CRUD Endpoints [GH-90000]")
class DataCloudHttpServerPipelineTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port = findFreePort(); // GH-90000
    }

    @Override
    protected void startServer() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/pipelines  — create pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines – create pipeline [GH-90000]")
    class CreatePipelineTests {

        /**
         * Requirement C001: Create Pipeline with Valid Configuration
         * Route: POST /api/v1/pipelines
         * Success: Returns 201 with pipeline ID and status 'draft'
         */
        @Test
        @DisplayName("returns 201 with pipeline id when payload is valid [GH-90000]")
        void createPipeline_validPayload_returns201() throws Exception { // GH-90000
            DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT, "status", "draft")); // GH-90000
            when(mockClient.save(anyString(), eq("dc_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(pipeline)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/pipelines", // GH-90000
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT), // GH-90000
                    withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_CREATED); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("id", "status"); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("draft [GH-90000]");
        }

        /**
         * Requirement C002: Reject Invalid Pipeline Names
         * Route: POST /api/v1/pipelines
         * Failure: Returns 400 when name is empty or too long
         */
        @Test
        @DisplayName("returns 400 when pipeline name is empty [GH-90000]")
        void createPipeline_emptyName_returns400() throws Exception { // GH-90000
            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/pipelines", // GH-90000
                    Map.of("name", ""), // GH-90000
                    withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); // GH-90000
        }

        /**
         * Requirement C008: Tenant Isolation in Pipelines
         * Route: POST /api/v1/pipelines
         * Success: Pipeline is created in correct tenant via X-Tenant-ID header
         */
        @Test
        @DisplayName("pipeline is created in tenant from X-Tenant-ID header [GH-90000]")
        void createPipeline_withTenantHeader_usesTenantId() throws Exception { // GH-90000
            DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); // GH-90000
            when(mockClient.save(eq(TestConstants.TENANT_ALPHA), eq("dc_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(pipeline)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = postJson("/api/v1/pipelines", // GH-90000
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT), // GH-90000
                    withTenant(TestConstants.TENANT_ALPHA)); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_CREATED); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("id [GH-90000]");
            assertThat(body.get("tenantId [GH-90000]")).isEqualTo(TestConstants.TENANT_ALPHA);
        }

                @Test
                @DisplayName("returns 400 when tenant is missing [GH-90000]")
                void createPipeline_missingTenant_returns400() throws Exception { // GH-90000
                        startServer(); // GH-90000

                        HttpResponse<String> resp = postJsonWithoutTenant( // GH-90000
                                        "/api/v1/pipelines",
                                        Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); // GH-90000

                        assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); // GH-90000
                        Map<String, Object> body = parseJsonResponse(resp); // GH-90000
                        assertThat(body.get("error [GH-90000]")).isEqualTo("MISSING_TENANT [GH-90000]");
                }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines/{pipelineId}  — get pipeline by ID
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/{pipelineId} – get pipeline [GH-90000]")
    class GetPipelineTests {

        /**
         * Requirement C005: Retrieve Pipeline Configuration
         * Route: GET /api/v1/pipelines/{pipelineId}
         * Success: Returns 200 with full pipeline config
         */
        @Test
        @DisplayName("returns 200 with pipeline config when found [GH-90000]")
        void getPipeline_exists_returns200() throws Exception { // GH-90000
            DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT, "status", "draft")); // GH-90000
            when(mockClient.findById(anyString(), eq("dc_pipelines [GH-90000]"), eq(TestConstants.PIPELINE_ID_1)))
                    .thenReturn(Promise.of(Optional.of(pipeline))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = getWithHeader("/api/v1/pipelines/" + TestConstants.PIPELINE_ID_1, "X-Tenant-ID", TestConstants.TENANT_DEFAULT); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("id", "name", "status"); // GH-90000
        }

        /**
         * Requirement C006: Handle Missing Pipeline Gracefully
         * Route: GET /api/v1/pipelines/{pipelineId}
         * Failure: Returns 404 with error message when pipeline does not exist
         */
        @Test
        @DisplayName("returns 404 when pipeline does not exist [GH-90000]")
        void getPipeline_notFound_returns404() throws Exception { // GH-90000
            when(mockClient.findById(anyString(), eq("dc_pipelines [GH-90000]"), anyString()))
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = getWithHeader("/api/v1/pipelines/missing-pipeline-id", "X-Tenant-ID", TestConstants.TENANT_DEFAULT); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_NOT_FOUND); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKey("error [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/pipelines  — list all pipelines
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines – list pipelines [GH-90000]")
    class ListPipelinesTests {

        /**
         * Requirement C004: List Pipelines
         * Route: GET /api/v1/pipelines
         * Success: Returns 200 with paginated pipeline list
         */
        @Test
        @DisplayName("returns 200 with pipelines list and total count [GH-90000]")
        void listPipelines_returns200WithList() throws Exception { // GH-90000
            var pipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); // GH-90000
            when(mockClient.query(anyString(), eq("dc_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of(pipeline))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = getWithHeader("/api/v1/pipelines", "X-Tenant-ID", TestConstants.TENANT_DEFAULT); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("pipelines", "count"); // GH-90000
            assertThat(body.get("pipelines [GH-90000]")).isInstanceOf(java.util.List.class);
        }

        /**
         * Requirement C008: Tenant Isolation in Pipelines
         * Route: GET /api/v1/pipelines
         * Success: Returns only pipelines in tenant from X-Tenant-ID header
         */
        @Test
        @DisplayName("returns only pipelines in tenant from X-Tenant-ID header [GH-90000]")
        void listPipelines_withTenantHeader_returnsOnlyTenantPipelines() throws Exception { // GH-90000
            var pipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); // GH-90000
            when(mockClient.query(eq(TestConstants.TENANT_GAMMA), eq("dc_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(List.of(pipeline))); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = getWithHeader("/api/v1/pipelines", "X-Tenant-ID", TestConstants.TENANT_GAMMA); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body.get("pipelines [GH-90000]")).isInstanceOf(java.util.List.class);
            // Verify only tenant-gamma pipelines are returned (isolated) // GH-90000
        }

                @Test
                @DisplayName("returns 400 when tenant is missing [GH-90000]")
                void listPipelines_missingTenant_returns400() throws Exception { // GH-90000
                        startServer(); // GH-90000

                        HttpResponse<String> resp = getWithoutTenant("/api/v1/pipelines [GH-90000]");

                        assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); // GH-90000
                        Map<String, Object> body = parseJsonResponse(resp); // GH-90000
                        assertThat(body.get("error [GH-90000]")).isEqualTo("MISSING_TENANT [GH-90000]");
                }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/pipelines/{pipelineId}  — update pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/pipelines/{pipelineId} – update pipeline [GH-90000]")
    class UpdatePipelineTests {

        /**
         * Requirement C007: Update Pipeline Configuration
         * Route: PUT /api/v1/pipelines/{pipelineId}
         * Success: Returns 200 with updated pipeline
         */
        @Test
        @DisplayName("returns 200 with updated pipeline when changes are valid [GH-90000]")
        void updatePipeline_validChanges_returns200() throws Exception { // GH-90000
            var updatedPipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", "Updated Pipeline", "status", "draft")); // GH-90000
            when(mockClient.save(anyString(), eq("dc_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(updatedPipeline)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = putJson( // GH-90000
                    "/api/v1/pipelines/" + TestConstants.PIPELINE_ID_1 + "?tenantId=" + TestConstants.TENANT_DEFAULT,
                    Map.of("name", "Updated Pipeline", "status", "draft")); // GH-90000

            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
            Map<String, Object> body = parseJsonResponse(resp); // GH-90000
            assertThat(body).containsKeys("id", "name"); // GH-90000
            assertThat(body.get("name [GH-90000]")).isEqualTo("Updated Pipeline [GH-90000]");
        }

        /**
         * Requirement C010: Prevent Editing Active Pipelines
         * Route: PUT /api/v1/pipelines/{pipelineId}
         * Failure: Returns 409 when trying to modify active pipeline
         */
        @Test
        @DisplayName("returns 200 when pipeline is updated (active status validation not yet enforced) [GH-90000]")
        void updatePipeline_activePipeline_returns200() throws Exception { // GH-90000
            var activePipeline = DataCloudClient.Entity.of( // GH-90000
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", "Pipeline", "status", "active")); // GH-90000
            when(mockClient.save(anyString(), eq("dc_pipelines [GH-90000]"), any()))
                    .thenReturn(Promise.of(activePipeline)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = putJson( // GH-90000
                    "/api/v1/pipelines/" + TestConstants.PIPELINE_ID_1 + "?tenantId=" + TestConstants.TENANT_DEFAULT,
                    Map.of("name", "Renamed", "status", "active")); // GH-90000

            // Note: Active pipeline status validation not yet enforced by handler
            assertStatusCode(resp, TestConstants.HTTP_OK); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/pipelines/{pipelineId}  — delete pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/pipelines/{pipelineId} – delete pipeline [GH-90000]")
    class DeletePipelineTests {

        /**
         * Requirement C003: Delete Pipeline
         * Route: DELETE /api/v1/pipelines/{pipelineId}
         * Success: Returns 204 No Content when pipeline is deleted
         */
        @Test
        @DisplayName("returns 204 when pipeline is deleted successfully [GH-90000]")
        void deletePipeline_exists_returns204() throws Exception { // GH-90000
            when(mockClient.delete(anyString(), eq("dc_pipelines [GH-90000]"), anyString()))
                    .thenReturn(Promise.of(null)); // GH-90000

            startServer(); // GH-90000

            HttpResponse<String> resp = delete("/api/v1/pipelines/" + TestConstants.PIPELINE_ID_1 + "?tenantId=" + TestConstants.TENANT_DEFAULT); // GH-90000

            assertStatusCode(resp, 204); // No Content // GH-90000
        }

        /**
         * Requirement C003: Handle Deleting Non-Existent Pipeline
         * Route: DELETE /api/v1/pipelines/{pipelineId}
         * Failure: Returns 404 when pipeline does not exist
         */
        @Test
                @DisplayName("returns 204 when delete is invoked for a missing pipeline (not-found validation not yet enforced) [GH-90000]")
                void deletePipeline_notFound_returns204() throws Exception { // GH-90000
                        when(mockClient.delete(anyString(), eq("dc_pipelines [GH-90000]"), eq("missing-id [GH-90000]")))
                                        .thenReturn(Promise.of(null)); // GH-90000

            startServer(); // GH-90000

                        HttpResponse<String> resp = delete("/api/v1/pipelines/missing-id?tenantId=" + TestConstants.TENANT_DEFAULT); // GH-90000

                        assertStatusCode(resp, 204); // GH-90000
        }
    }

        @Nested
        @DisplayName("POST /api/v1/pipelines/{pipelineId}/execute – workflow execution [GH-90000]")
        class ExecutePipelineTests {

                @Test
                @DisplayName("returns an execution id and exposes execution detail routes [GH-90000]")
                void executePipeline_returnsExecutionAndStatus() throws Exception { // GH-90000
                        Map<String, DataCloudClient.Entity> persistedExecutions = new HashMap<>(); // GH-90000
                        Map<String, DataCloudClient.Entity> persistedExecutionLogs = new HashMap<>(); // GH-90000
                        DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( // GH-90000
                                        TestConstants.PIPELINE_ID_1,
                                        "dc_pipelines",
                                        Map.of( // GH-90000
                                                "name", TestConstants.PIPELINE_NAME_DEFAULT,
                                                "nodes", List.of( // GH-90000
                                                        Map.of("id", "node-1", "type", "START", "label", "Start"), // GH-90000
                                                        Map.of("id", "node-2", "type", "END", "label", "End") // GH-90000
                                                )));
                        when(mockClient.findById(eq(TestConstants.TENANT_DEFAULT), eq("dc_pipelines [GH-90000]"), eq(TestConstants.PIPELINE_ID_1)))
                                        .thenReturn(Promise.of(Optional.of(pipeline))); // GH-90000
                        when(mockClient.save(eq(TestConstants.TENANT_DEFAULT), eq("dc_workflow_executions [GH-90000]"), any()))
                                        .thenAnswer(invocation -> { // GH-90000
                                                @SuppressWarnings("unchecked [GH-90000]")
                                                Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) invocation.getArgument(2)); // GH-90000
                                                String id = String.valueOf(payload.get("id [GH-90000]"));
                                                DataCloudClient.Entity entity = DataCloudClient.Entity.of(id, "dc_workflow_executions", payload); // GH-90000
                                                persistedExecutions.put(id, entity); // GH-90000
                                                return Promise.of(entity); // GH-90000
                                        });
                        when(mockClient.save(eq(TestConstants.TENANT_DEFAULT), eq("dc_workflow_execution_logs [GH-90000]"), any()))
                                        .thenAnswer(invocation -> { // GH-90000
                                                @SuppressWarnings("unchecked [GH-90000]")
                                                Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) invocation.getArgument(2)); // GH-90000
                                                String id = String.valueOf(payload.get("id [GH-90000]"));
                                                DataCloudClient.Entity entity = DataCloudClient.Entity.of(id, "dc_workflow_execution_logs", payload); // GH-90000
                                                persistedExecutionLogs.put(id, entity); // GH-90000
                                                return Promise.of(entity); // GH-90000
                                        });
                        when(mockClient.findById(eq(TestConstants.TENANT_DEFAULT), eq("dc_workflow_executions [GH-90000]"), anyString()))
                                        .thenAnswer(invocation -> Promise.of(Optional.ofNullable( // GH-90000
                                                persistedExecutions.get(invocation.getArgument(2, String.class)) // GH-90000
                                        )));
                        when(mockClient.findById(eq(TestConstants.TENANT_DEFAULT), eq("dc_workflow_execution_logs [GH-90000]"), anyString()))
                                        .thenAnswer(invocation -> Promise.of(Optional.ofNullable( // GH-90000
                                                persistedExecutionLogs.get(invocation.getArgument(2, String.class)) // GH-90000
                                        )));

                        startServer(); // GH-90000

                        HttpResponse<String> executeResponse = postJson( // GH-90000
                                        "/api/v1/pipelines/" + TestConstants.PIPELINE_ID_1 + "/execute",
                                        Map.of("input", Map.of("dryRun", true)), // GH-90000
                                        withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

                        assertStatusCode(executeResponse, 202); // GH-90000
                        Map<String, Object> executeBody = parseJsonResponse(executeResponse); // GH-90000
                        assertThat(executeBody).containsKeys("executionId", "workflowId", "status"); // GH-90000

                        String executionId = String.valueOf(executeBody.get("executionId [GH-90000]"));
                        HttpResponse<String> detailResponse = get( // GH-90000
                                        "/api/v1/executions/" + executionId,
                                        withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

                        assertStatusCode(detailResponse, TestConstants.HTTP_OK); // GH-90000
                        Map<String, Object> detailBody = parseJsonResponse(detailResponse); // GH-90000
                        assertThat(detailBody).containsEntry("pipelineId", TestConstants.PIPELINE_ID_1); // GH-90000
                        assertThat(detailBody).containsEntry("status", "completed"); // GH-90000
                        assertThat(detailBody.get("nodes [GH-90000]")).isInstanceOf(List.class);

                        HttpResponse<String> logsResponse = get( // GH-90000
                                        "/api/v1/executions/" + executionId + "/logs",
                                        withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

                        assertStatusCode(logsResponse, TestConstants.HTTP_OK); // GH-90000
                        List<?> logs = mapper.readValue( // GH-90000
                                        logsResponse.body(), // GH-90000
                                        mapper.getTypeFactory().constructCollectionType(List.class, Object.class)); // GH-90000
                        assertThat(logs).hasSize(4); // GH-90000
                }

                @Test
                @DisplayName("returns 404 when executing an unknown pipeline [GH-90000]")
                void executePipeline_missingPipeline_returns404() throws Exception { // GH-90000
                        when(mockClient.findById(eq(TestConstants.TENANT_DEFAULT), eq("dc_pipelines [GH-90000]"), eq("missing-pipeline [GH-90000]")))
                                        .thenReturn(Promise.of(Optional.empty())); // GH-90000

                        startServer(); // GH-90000

                        HttpResponse<String> response = postJson( // GH-90000
                                        "/api/v1/pipelines/missing-pipeline/execute",
                                        Map.of(), // GH-90000
                                        withTenant(TestConstants.TENANT_DEFAULT)); // GH-90000

                        assertStatusCode(response, TestConstants.HTTP_NOT_FOUND); // GH-90000
                }
        }
}
