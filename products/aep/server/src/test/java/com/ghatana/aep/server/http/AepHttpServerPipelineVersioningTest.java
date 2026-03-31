/*
 * Copyright (c) 2026 Ghatana Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AEP pipeline versioning endpoints (AEP-07).
 *
 * <p>Covers the DRAFT → PUBLISHED → ARCHIVED state machine:
 * <ul>
 *   <li>GET /api/v1/pipelines/:id/versions — version history</li>
 *   <li>POST /api/v1/pipelines/:id/publish — publish a named version</li>
 *   <li>POST /api/v1/pipelines/:id/rollback?toVersion=N — rollback to a snapshot</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for pipeline versioning (DRAFT → PUBLISHED → ARCHIVED, rollback)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Pipeline Versioning (AEP-07)")
class AepHttpServerPipelineVersioningTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ─── GET /api/v1/pipelines/:id/versions ──────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/pipelines/:id/versions — version history")
    class GetVersionHistory {

        @Test
        @DisplayName("returns empty history for a newly created pipeline")
        void returnsEmptyHistoryForNewPipeline() throws Exception {
            String pipelineId = createPipelineAndGetId("versioning-test-pipeline");

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            assertThat(body).containsKey("versions");
            List<?> versions = (List<?>) body.get("versions");
            assertThat(versions).isEmpty();
        }

        @Test
        @DisplayName("returns 200 with pipelineId in response")
        void includesPipelineIdInResponse() throws Exception {
            String pipelineId = createPipelineAndGetId("versions-response-test");

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            assertThat(body.get("pipelineId")).isEqualTo(pipelineId);
            assertThat(body).containsKey("count");
        }

        @Test
        @DisplayName("returns version snapshot after publish")
        void returnsVersionAfterPublish() throws Exception {
            String pipelineId = createPipelineAndGetId("publish-history-test");

            post("/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0")));

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions");
            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            List<?> versions = (List<?>) body.get("versions");
            assertThat(versions).hasSize(1);

            @SuppressWarnings("unchecked")
            Map<String, Object> snap = (Map<String, Object>) versions.get(0);
            assertThat(snap.get("versionLabel")).isEqualTo("v1.0.0");
            assertThat(snap.get("versionStatus")).isEqualTo("PUBLISHED");
        }

        @Test
        @DisplayName("version history accumulates multiple publishes in order")
        void accumulatesMultiplePublishesInOrder() throws Exception {
            String pipelineId = createPipelineAndGetId("multi-publish-test");

            post("/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0")));
            post("/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v2.0.0")));

            HttpResponse<String> resp = get("/api/v1/pipelines/" + pipelineId + "/versions");
            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            List<?> versions = (List<?>) body.get("versions");
            assertThat(versions).hasSize(2);
        }
    }

    // ─── POST /api/v1/pipelines/:id/publish ──────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/:id/publish — publish named version")
    class PublishVersion {

        @Test
        @DisplayName("returns 200 with published=true and versionLabel")
        void publishesDraftWithLabel() throws Exception {
            String pipelineId = createPipelineAndGetId("publish-test");

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0")));

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            assertThat(body.get("published")).isEqualTo(true);
            assertThat(body.get("versionLabel")).isEqualTo("v1.0.0");
            assertThat(body.get("pipelineId")).isEqualTo(pipelineId);
        }

        @Test
        @DisplayName("sets pipeline versionStatus to PUBLISHED after publish")
        void setsPublishedStatus() throws Exception {
            String pipelineId = createPipelineAndGetId("status-after-publish");

            post("/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "release-1")));

            HttpResponse<String> pipelineResp = get("/api/v1/pipelines/" + pipelineId);
            assertThat(pipelineResp.statusCode()).isEqualTo(200);
            Map<String, Object> pipeline = parseBody(pipelineResp);
            assertThat(pipeline.get("versionLabel")).isEqualTo("release-1");
            assertThat(pipeline.get("versionStatus")).isEqualTo("PUBLISHED");
        }

        @Test
        @DisplayName("returns 400 when versionLabel is missing")
        void rejects400WhenLabelMissing() throws Exception {
            String pipelineId = createPipelineAndGetId("publish-no-label");

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of()));

            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 400 when versionLabel is blank")
        void rejects400WhenLabelBlank() throws Exception {
            String pipelineId = createPipelineAndGetId("publish-blank-label");

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "   ")));

            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 404 for unknown pipeline ID")
        void returns404ForUnknownPipeline() throws Exception {
            HttpResponse<String> resp = post(
                "/api/v1/pipelines/does-not-exist/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1.0.0")));

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ─── POST /api/v1/pipelines/:id/rollback ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/:id/rollback — restore prior version")
    class RollbackVersion {

        @Test
        @DisplayName("rolls back pipeline to a published snapshot version")
        void rollsBackToPublishedSnapshot() throws Exception {
            String pipelineId = createPipelineAndGetId("rollback-test");

            // Capture version number from create
            HttpResponse<String> created = get("/api/v1/pipelines/" + pipelineId);
            int originalVersion = ((Number) parseBody(created).get("version")).intValue();

            // Publish to create a snapshot
            post("/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "stable-v1")));

            // Rollback to the published snapshot
            HttpResponse<String> rollbackResp = post(
                "/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=" + originalVersion,
                "{}");

            assertThat(rollbackResp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(rollbackResp);
            assertThat(body.get("rolledBack")).isEqualTo(true);
            assertThat(body.get("pipelineId")).isEqualTo(pipelineId);
            assertThat(((Number) body.get("restoredVersion")).intValue()).isEqualTo(originalVersion);
        }

        @Test
        @DisplayName("rolled-back pipeline has DRAFT status")
        void rolledBackPipelineIsDraft() throws Exception {
            String pipelineId = createPipelineAndGetId("rollback-draft-status");

            HttpResponse<String> created = get("/api/v1/pipelines/" + pipelineId);
            int originalVersion = ((Number) parseBody(created).get("version")).intValue();

            post("/api/v1/pipelines/" + pipelineId + "/publish",
                mapper.writeValueAsString(Map.of("versionLabel", "v1")));

            post("/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=" + originalVersion, "{}");

            HttpResponse<String> pipeline = get("/api/v1/pipelines/" + pipelineId);
            Map<String, Object> pipelineBody = parseBody(pipeline);
            assertThat(pipelineBody.get("versionStatus")).isEqualTo("DRAFT");
            assertThat(pipelineBody.get("versionLabel")).isEqualTo("");
        }

        @Test
        @DisplayName("returns 400 when toVersion parameter is missing")
        void returns400WhenToVersionMissing() throws Exception {
            String pipelineId = createPipelineAndGetId("rollback-no-version");

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/" + pipelineId + "/rollback",
                "{}");

            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 400 when toVersion is not a number")
        void returns400WhenToVersionNotNumeric() throws Exception {
            String pipelineId = createPipelineAndGetId("rollback-bad-version");

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=abc",
                "{}");

            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 404 when version snapshot does not exist")
        void returns404WhenSnapshotMissing() throws Exception {
            String pipelineId = createPipelineAndGetId("rollback-missing-snap");

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/" + pipelineId + "/rollback?toVersion=999",
                "{}");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String createPipelineAndGetId(String name) throws Exception {
        String config = "{\"stages\":[{\"name\":\"step1\",\"type\":\"transform\"}]}";
        HttpResponse<String> resp = post("/api/v1/pipelines",
            mapper.writeValueAsString(Map.of(
                "name", name,
                "tenantId", "test-tenant",
                "config", config
            )));
        assertThat(resp.statusCode()).isIn(200, 201);
        Map<String, Object> body = parseBody(resp);
        assertThat(body.get("id")).as("Pipeline create response must contain 'id'; body=%s", resp.body())
            .isNotNull();
        return (String) body.get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpResponse<String> resp) throws Exception {
        return mapper.readValue(resp.body(), Map.class);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("X-Tenant-Id", "test-tenant")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", "test-tenant")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private int findFreePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private void waitForServerReady(int targetPort) throws Exception {
        for (int i = 0; i < 50; i++) {
            try (Socket ignored = new Socket("localhost", targetPort)) {
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Server did not start within 5 seconds on port " + targetPort);
    }
}
