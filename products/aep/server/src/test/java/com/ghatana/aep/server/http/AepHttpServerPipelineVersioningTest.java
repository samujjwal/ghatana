/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AEP pipeline versioning endpoints (AEP-07). // GH-90000
 *
 * <p>Covers the DRAFT → PUBLISHED → ARCHIVED state machine:
 * <ul>
 *   <li>GET /api/v1/pipelines/:id/versions — version history</li>
 *   <li>POST /api/v1/pipelines/:id/publish — publish a named version</li>
 *   <li>POST /api/v1/pipelines/:id/rollback?toVersion=N — rollback to a snapshot</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for pipeline versioning (DRAFT → PUBLISHED → ARCHIVED, rollback) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Pipeline Versioning (AEP-07) [GH-90000]")
class AepHttpServerPipelineVersioningTest {

    private static final String DEFAULT_TENANT_ID = "test-tenant";

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ─── GET /api/v1/pipelines/:id/versions ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/:id/versions — version history [GH-90000]")
    class GetVersionHistory {

        @Test
        @DisplayName("returns empty history for a newly created pipeline [GH-90000]")
        void returnsEmptyHistoryForNewPipeline() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("versioning-test-pipeline [GH-90000]");

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            assertThat(body).containsKey("versions [GH-90000]");
            List<?> versions = (List<?>) body.get("versions [GH-90000]");
            assertThat(versions).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns 200 with pipelineId in response [GH-90000]")
        void includesPipelineIdInResponse() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("versions-response-test [GH-90000]");

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            assertThat(body.get("pipelineId [GH-90000]")).isEqualTo(pipelineId);
            assertThat(body).containsKey("count [GH-90000]");
        }

        @Test
        @DisplayName("returns version snapshot after publish [GH-90000]")
        void returnsVersionAfterPublish() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("publish-history-test [GH-90000]");

            post("/api/v1/pipelines/" + pipelineId + "/publish", // GH-90000
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0"))); // GH-90000

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            List<?> versions = (List<?>) body.get("versions [GH-90000]");
            assertThat(versions).hasSize(1); // GH-90000

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> snap = (Map<String, Object>) versions.get(0); // GH-90000
            assertThat(snap.get("versionLabel [GH-90000]")).isEqualTo("v1.0.0 [GH-90000]");
            assertThat(snap.get("versionStatus [GH-90000]")).isEqualTo("PUBLISHED [GH-90000]");
        }

        @Test
        @DisplayName("version history accumulates multiple publishes in order [GH-90000]")
        void accumulatesMultiplePublishesInOrder() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("multi-publish-test [GH-90000]");

            post("/api/v1/pipelines/" + pipelineId + "/publish", // GH-90000
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0"))); // GH-90000
            post("/api/v1/pipelines/" + pipelineId + "/publish", // GH-90000
                mapper.writeValueAsString(Map.of("versionLabel", "v2.0.0"))); // GH-90000

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            List<?> versions = (List<?>) body.get("versions [GH-90000]");
            assertThat(versions).hasSize(2); // GH-90000
        }
    }

    // ─── POST /api/v1/pipelines/:id/publish ──────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/:id/publish — publish named version [GH-90000]")
    class PublishVersion {

        @Test
        @DisplayName("returns 200 with published=true and versionLabel [GH-90000]")
        void publishesDraftWithLabel() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("publish-test [GH-90000]");

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            assertThat(body.get("published [GH-90000]")).isEqualTo(true);
            assertThat(body.get("versionLabel [GH-90000]")).isEqualTo("v1.0.0 [GH-90000]");
            assertThat(body.get("pipelineId [GH-90000]")).isEqualTo(pipelineId);
        }

        @Test
        @DisplayName("sets pipeline versionStatus to PUBLISHED after publish [GH-90000]")
        void setsPublishedStatus() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("status-after-publish [GH-90000]");

            post("/api/v1/pipelines/" + pipelineId + "/publish", // GH-90000
                mapper.writeValueAsString(Map.of("versionLabel", "release-1"))); // GH-90000

            HttpResponse<String> pipelineResp = get("/api/v1/pipelines/" + pipelineId); // GH-90000
            assertThat(pipelineResp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> pipeline = parseBody(pipelineResp); // GH-90000
            assertThat(pipeline.get("versionLabel [GH-90000]")).isEqualTo("release-1 [GH-90000]");
            assertThat(pipeline.get("versionStatus [GH-90000]")).isEqualTo("PUBLISHED [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when versionLabel is missing [GH-90000]")
        void rejects400WhenLabelMissing() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("publish-no-label [GH-90000]");

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of())); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when versionLabel is blank [GH-90000]")
        void rejects400WhenLabelBlank() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("publish-blank-label [GH-90000]");

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "   "))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 404 for unknown pipeline ID [GH-90000]")
        void returns404ForUnknownPipeline() throws Exception { // GH-90000
            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/does-not-exist/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0"))); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ─── POST /api/v1/pipelines/:id/rollback ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/:id/rollback — restore prior version [GH-90000]")
    class RollbackVersion {

        @Test
        @DisplayName("rolls back pipeline to a published snapshot version [GH-90000]")
        void rollsBackToPublishedSnapshot() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("rollback-test [GH-90000]");

            // Capture version number from create
            HttpResponse<String> created = get("/api/v1/pipelines/" + pipelineId); // GH-90000
            int originalVersion = ((Number) parseBody(created).get("version [GH-90000]")).intValue();

            // Publish to create a snapshot
            post("/api/v1/pipelines/" + pipelineId + "/publish", // GH-90000
                mapper.writeValueAsString(Map.of("versionLabel", "stable-v1"))); // GH-90000

            // Rollback to the published snapshot
            HttpResponse<String> rollbackResp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=" + originalVersion,
                "{}");

            assertThat(rollbackResp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(rollbackResp); // GH-90000
            assertThat(body.get("rolledBack [GH-90000]")).isEqualTo(true);
            assertThat(body.get("pipelineId [GH-90000]")).isEqualTo(pipelineId);
            assertThat(((Number) body.get("restoredVersion [GH-90000]")).intValue()).isEqualTo(originalVersion);
        }

        @Test
        @DisplayName("rolled-back pipeline has DRAFT status [GH-90000]")
        void rolledBackPipelineIsDraft() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("rollback-draft-status [GH-90000]");

            HttpResponse<String> created = get("/api/v1/pipelines/" + pipelineId); // GH-90000
            int originalVersion = ((Number) parseBody(created).get("version [GH-90000]")).intValue();

            post("/api/v1/pipelines/" + pipelineId + "/publish", // GH-90000
                mapper.writeValueAsString(Map.of("versionLabel", "v1"))); // GH-90000

            post("/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=" + originalVersion, "{}"); // GH-90000

            HttpResponse<String> pipeline = get("/api/v1/pipelines/" + pipelineId); // GH-90000
            Map<String, Object> pipelineBody = parseBody(pipeline); // GH-90000
            assertThat(pipelineBody.get("versionStatus [GH-90000]")).isEqualTo("DRAFT [GH-90000]");
            assertThat(pipelineBody.get("versionLabel [GH-90000]")).isEqualTo(" [GH-90000]");
        }

        @Test
        @DisplayName("returns 400 when toVersion parameter is missing [GH-90000]")
        void returns400WhenToVersionMissing() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("rollback-no-version [GH-90000]");

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/rollback",
                "{}");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 400 when toVersion is not a number [GH-90000]")
        void returns400WhenToVersionNotNumeric() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("rollback-bad-version [GH-90000]");

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=abc",
                "{}");

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("returns 404 when version snapshot does not exist [GH-90000]")
        void returns404WhenSnapshotMissing() throws Exception { // GH-90000
            String pipelineId = createPipelineAndGetId("rollback-missing-snap [GH-90000]");

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=999",
                "{}");

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    @Nested
    @DisplayName("Tenant isolation [GH-90000]")
    class TenantIsolation {

        @Test
        @DisplayName("list endpoint only returns pipelines for the requested tenant [GH-90000]")
        void listEndpointIsTenantScoped() throws Exception { // GH-90000
            String tenantOnePipelineId = createPipelineAndGetId("tenant-one-pipeline", "tenant-one"); // GH-90000
            String tenantTwoPipelineId = createPipelineAndGetId("tenant-two-pipeline", "tenant-two"); // GH-90000

            HttpResponse<String> tenantOneResponse = get("/api/v1/pipelines", "tenant-one"); // GH-90000
            HttpResponse<String> tenantTwoResponse = get("/api/v1/pipelines", "tenant-two"); // GH-90000

            assertThat(tenantOneResponse.statusCode()).isEqualTo(200); // GH-90000
            assertThat(tenantTwoResponse.statusCode()).isEqualTo(200); // GH-90000

            assertThat(pipelineIds(tenantOneResponse)).contains(tenantOnePipelineId).doesNotContain(tenantTwoPipelineId); // GH-90000
            assertThat(pipelineIds(tenantTwoResponse)).contains(tenantTwoPipelineId).doesNotContain(tenantOnePipelineId); // GH-90000
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String createPipelineAndGetId(String name) throws Exception { // GH-90000
        return createPipelineAndGetId(name, DEFAULT_TENANT_ID); // GH-90000
    }

    private String createPipelineAndGetId(String name, String tenantId) throws Exception { // GH-90000
        String config = "{\"stages\":[{\"name\":\"step1\",\"type\":\"transform\"}]}";
        HttpResponse<String> resp = post("/api/v1/pipelines", // GH-90000
            mapper.writeValueAsString(Map.of( // GH-90000
                "name", name,
                "tenantId", tenantId,
                "config", config
            )), tenantId);
        assertThat(resp.statusCode()).isIn(200, 201); // GH-90000
        Map<String, Object> body = parseBody(resp); // GH-90000
        assertThat(body.get("id [GH-90000]")).as("Pipeline create response must contain 'id'; body=%s", resp.body())
            .isNotNull(); // GH-90000
        return (String) body.get("id [GH-90000]");
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> parseBody(HttpResponse<String> resp) throws Exception { // GH-90000
        return mapper.readValue(resp.body(), Map.class); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return get(path, DEFAULT_TENANT_ID); // GH-90000
    }

    private HttpResponse<String> get(String path, String tenantId) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("X-Tenant-Id", tenantId) // GH-90000
                .GET() // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        return post(path, body, DEFAULT_TENANT_ID); // GH-90000
    }

    private HttpResponse<String> post(String path, String body, String tenantId) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .uri(URI.create("http://localhost:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .header("X-Tenant-Id", tenantId) // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private List<String> pipelineIds(HttpResponse<String> response) throws Exception { // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        List<Map<String, Object>> pipelines = (List<Map<String, Object>>) parseBody(response).get("pipelines [GH-90000]");
        return pipelines.stream() // GH-90000
            .map(pipeline -> (String) pipeline.get("id [GH-90000]"))
            .collect(Collectors.toList()); // GH-90000
    }

    private int findFreePort() throws IOException { // GH-90000
        try (ServerSocket s = new ServerSocket(0)) { // GH-90000
            return s.getLocalPort(); // GH-90000
        }
    }

    private void waitForServerReady(int targetPort) throws Exception { // GH-90000
        for (int i = 0; i < 50; i++) { // GH-90000
            try (Socket ignored = new Socket("localhost", targetPort)) { // GH-90000
                return;
            } catch (IOException e) { // GH-90000
                Thread.sleep(100); // GH-90000
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + targetPort); // GH-90000
    }
}
