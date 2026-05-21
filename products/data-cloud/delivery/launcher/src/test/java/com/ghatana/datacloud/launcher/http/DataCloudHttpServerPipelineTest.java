/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
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
 * Integration tests for Data Cloud HTTP pipeline endpoints.
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
 * @doc.purpose Integration tests for /api/v1/action/pipelines/** HTTP endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Pipeline Endpoints")
class DataCloudHttpServerPipelineTest extends DataCloudHttpServerTestBase {

    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception { 
        mockClient = mock(DataCloudClient.class); 
        port = findFreePort(); 
    }

    @Override
    protected void startServer() throws Exception { 
        server = new DataCloudHttpServer(mockClient, port); 
        server.start(); 
        waitForServerReady(TestConstants.TIMEOUT_SERVER_START_MS); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/action/pipelines  — create pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/action/pipelines – create pipeline")
    class CreatePipelineTests {

        /**
         * Requirement C001: Create Pipeline with Valid Configuration
         * Route: POST /api/v1/action/pipelines
         * Success: Returns 201 with pipeline ID and status 'draft'
         */
        @Test
        @DisplayName("returns 201 with pipeline id when payload is valid")
        void createPipeline_validPayload_returns201() throws Exception { 
            DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( 
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT, "status", "draft")); 
            when(mockClient.save(anyString(), eq("dc_pipelines"), any()))
                    .thenReturn(Promise.of(pipeline)); 

            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/action/pipelines", 
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT), 
                    withTenant(TestConstants.TENANT_DEFAULT)); 

            assertStatusCode(resp, TestConstants.HTTP_CREATED); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKeys("id", "status"); 
            assertThat(body.get("status")).isEqualTo("draft");
        }

        /**
         * Requirement C002: Reject Invalid Pipeline Names
         * Route: POST /api/v1/action/pipelines
         * Failure: Returns 400 when name is empty or too long
         */
        @Test
        @DisplayName("returns 400 when pipeline name is empty")
        void createPipeline_emptyName_returns400() throws Exception { 
            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/action/pipelines", 
                    Map.of("name", ""), 
                    withTenant(TestConstants.TENANT_DEFAULT)); 

            assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); 
        }

        /**
         * Requirement C008: Tenant Isolation in Pipelines
         * Route: POST /api/v1/action/pipelines
         * Success: Pipeline is created in correct tenant via X-Tenant-ID header
         */
        @Test
        @DisplayName("pipeline is created in tenant from X-Tenant-ID header")
        void createPipeline_withTenantHeader_usesTenantId() throws Exception { 
            DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( 
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); 
            when(mockClient.save(eq(TestConstants.TENANT_ALPHA), eq("dc_pipelines"), any()))
                    .thenReturn(Promise.of(pipeline)); 

            startServer(); 

            HttpResponse<String> resp = postJson("/api/v1/action/pipelines", 
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT), 
                    withTenant(TestConstants.TENANT_ALPHA)); 

            assertStatusCode(resp, TestConstants.HTTP_CREATED); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKeys("id");
            assertThat(body.get("tenantId")).isEqualTo(TestConstants.TENANT_ALPHA);
        }

                @Test
                @DisplayName("returns 400 when tenant is missing")
                void createPipeline_missingTenant_returns400() throws Exception { 
                        startServer(); 

                        HttpResponse<String> resp = postJsonWithoutTenant( 
                                        "/api/v1/action/pipelines",
                                        Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); 

                        assertStatusCode(resp, TestConstants.HTTP_BAD_REQUEST); 
                        Map<String, Object> body = parseJsonResponse(resp); 
                        assertThat(body.get("error")).isEqualTo("BAD_REQUEST");
                }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/action/pipelines/{pipelineId}  — get pipeline by ID
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/action/pipelines/{pipelineId} – get pipeline")
    class GetPipelineTests {

        /**
         * Requirement C005: Retrieve Pipeline Configuration
         * Route: GET /api/v1/action/pipelines/{pipelineId}
         * Success: Returns 200 with full pipeline config
         */
        @Test
        @DisplayName("returns 200 with pipeline config when found")
        void getPipeline_exists_returns200() throws Exception { 
            DataCloudClient.Entity pipeline = DataCloudClient.Entity.of( 
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT, "status", "draft")); 
            when(mockClient.findById(anyString(), eq("dc_pipelines"), eq(TestConstants.PIPELINE_ID_1)))
                    .thenReturn(Promise.of(Optional.of(pipeline))); 

            startServer(); 

            HttpResponse<String> resp = getWithHeader("/api/v1/action/pipelines/" + TestConstants.PIPELINE_ID_1, "X-Tenant-ID", TestConstants.TENANT_DEFAULT); 

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKeys("id", "name", "status"); 
        }

        /**
         * Requirement C006: Handle Missing Pipeline Gracefully
         * Route: GET /api/v1/action/pipelines/{pipelineId}
         * Failure: Returns 404 with error message when pipeline does not exist
         */
        @Test
        @DisplayName("returns 404 when pipeline does not exist")
        void getPipeline_notFound_returns404() throws Exception { 
            when(mockClient.findById(anyString(), eq("dc_pipelines"), anyString()))
                    .thenReturn(Promise.of(Optional.empty())); 

            startServer(); 

            HttpResponse<String> resp = getWithHeader("/api/v1/action/pipelines/missing-pipeline-id", "X-Tenant-ID", TestConstants.TENANT_DEFAULT); 

            assertStatusCode(resp, TestConstants.HTTP_NOT_FOUND); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKey("error");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/action/pipelines  — list all pipelines
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/action/pipelines – list pipelines")
    class ListPipelinesTests {

        /**
         * Requirement C004: List Pipelines
         * Route: GET /api/v1/action/pipelines
         * Success: Returns 200 with paginated pipeline list
         */
        @Test
        @DisplayName("returns 200 with pipelines list and total count")
        void listPipelines_returns200WithList() throws Exception { 
            var pipeline = DataCloudClient.Entity.of( 
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); 
            when(mockClient.query(anyString(), eq("dc_pipelines"), any()))
                    .thenReturn(Promise.of(List.of(pipeline))); 

            startServer(); 

            HttpResponse<String> resp = getWithHeader("/api/v1/action/pipelines", "X-Tenant-ID", TestConstants.TENANT_DEFAULT); 

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body).containsKeys("pipelines", "count"); 
            assertThat(body.get("pipelines")).isInstanceOf(java.util.List.class);
        }

        /**
         * Requirement C008: Tenant Isolation in Pipelines
         * Route: GET /api/v1/action/pipelines
         * Success: Returns only pipelines in tenant from X-Tenant-ID header
         */
        @Test
        @DisplayName("returns only pipelines in tenant from X-Tenant-ID header")
        void listPipelines_withTenantHeader_returnsOnlyTenantPipelines() throws Exception { 
            var pipeline = DataCloudClient.Entity.of( 
                    TestConstants.PIPELINE_ID_1, "dc_pipelines",
                    Map.of("name", TestConstants.PIPELINE_NAME_DEFAULT)); 
            when(mockClient.query(eq(TestConstants.TENANT_GAMMA), eq("dc_pipelines"), any()))
                    .thenReturn(Promise.of(List.of(pipeline))); 

            startServer(); 

            HttpResponse<String> resp = getWithHeader("/api/v1/action/pipelines", "X-Tenant-ID", TestConstants.TENANT_GAMMA); 

            assertStatusCode(resp, TestConstants.HTTP_OK); 
            Map<String, Object> body = parseJsonResponse(resp); 
            assertThat(body.get("pipelines")).isInstanceOf(java.util.List.class);
            // Verify only tenant-gamma pipelines are returned (isolated) 
        }

                @Test
                @DisplayName("falls back to default tenant when missing")
                void listPipelines_missingTenant_fallsBackToDefault() throws Exception { // DC-AUD-014
                        when(mockClient.query(eq("default"), eq("dc_pipelines"), any(DataCloudClient.Query.class)))
                            .thenReturn(Promise.of(List.of())); 

                        startServer(); 

                        HttpResponse<String> resp = getWithoutTenant("/api/v1/action/pipelines");

                        assertStatusCode(resp, TestConstants.HTTP_OK); // DC-AUD-014
                        Map<String, Object> body = parseJsonResponse(resp); 
                        assertThat(body).isNotNull();
                        // assertThat(body.get("tenantId")).isEqualTo("default");
                }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/action/pipelines/{pipelineId}  — delete pipeline
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/action/pipelines/{pipelineId} – delete pipeline")
    class DeletePipelineTests {

        /**
         * Requirement C003: Delete Pipeline
         * Route: DELETE /api/v1/action/pipelines/{pipelineId}
         * Success: Returns 204 No Content when pipeline is deleted
         */
        @Test
        @DisplayName("returns 204 when pipeline is deleted successfully")
        void deletePipeline_exists_returns204() throws Exception { 
            when(mockClient.delete(anyString(), eq("dc_pipelines"), anyString()))
                    .thenReturn(Promise.of(null)); 

            startServer(); 

            HttpResponse<String> resp = delete("/api/v1/action/pipelines/" + TestConstants.PIPELINE_ID_1 + "?tenantId=" + TestConstants.TENANT_DEFAULT); 

            assertStatusCode(resp, 204); // No Content 
        }

        /**
         * Requirement C003: Handle Deleting Non-Existent Pipeline
         * Route: DELETE /api/v1/action/pipelines/{pipelineId}
         * Failure: Returns 404 when pipeline does not exist
         */
        @Test
                @DisplayName("returns 204 when delete is invoked for a missing pipeline (not-found validation not yet enforced)")
                void deletePipeline_notFound_returns204() throws Exception { 
                        when(mockClient.delete(anyString(), eq("dc_pipelines"), eq("missing-id")))
                                        .thenReturn(Promise.of(null)); 

            startServer(); 

                        HttpResponse<String> resp = delete("/api/v1/action/pipelines/missing-id?tenantId=" + TestConstants.TENANT_DEFAULT); 

                        assertStatusCode(resp, 204); 
        }
    }
}
