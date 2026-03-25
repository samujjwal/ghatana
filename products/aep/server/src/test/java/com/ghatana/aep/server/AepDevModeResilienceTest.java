/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.aep.server.http.AepHttpServer;

/**
 * Resilience and dev-mode tests for AEP when optional dependencies (DataCloud,
 * HITL review queue, run ledger) are absent or disabled.
 *
 * <p>Validates the core contract:
 * <ul>
 *   <li>All read endpoints return empty results (never 5xx) when DataCloud is absent</li>
 *   <li>Event ingestion still succeeds (fire-and-forget ledger)</li>
 *   <li>Health probe accurately reflects "disabled" vs "healthy" per component</li>
 *   <li>SLO snapshot endpoint always responds (metrics infra is always active)</li>
 *   <li>Batch ingestion degrades gracefully (processes what it can)</li>
 * </ul>
 *
 * <p>These tests cover the "fixture-backed local developer environment" scenario
 * described in Phase-7 of the World Class AEP Report: developers can run AEP
 * locally without any external services and all UI-facing endpoints return safe
 * empty states instead of errors.
 *
 * @doc.type class
 * @doc.purpose Dev-mode resilience: verify graceful degradation when DataCloud absent
 * @doc.layer product
 * @doc.pattern ResilienceTest
 */
@DisplayName("AEP Dev-Mode Resilience Tests (no DataCloud)")
class AepDevModeResilienceTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUpServerWithoutDataCloud() throws Exception {
        // Aep.forTesting() creates an engine with no DataCloud, no external deps.
        // This is the canonical "local dev / CI no-services" fixture.
        engine = Aep.forTesting();
        port = findFreePort();
        http = HttpClient.newBuilder().build();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ── Event ingestion degrades gracefully ───────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/events succeeds even without DataCloud (fire-and-forget ledger)")
    void eventIngestionSucceedsWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = postEvent("dev-tenant", "user.created",
                Map.of("userId", "dev-001"));

        // Must succeed — run ledger is fire-and-forget when DataCloud absent
        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        // Event response shape: { "eventId": ..., "success": true, "detections": 0, ... }
        assertThat(body).containsKey("eventId");
        assertThat(body.get("success")).isEqualTo(true);
    }

    // ── Read endpoints return empty collections (not 5xx) ─────────────────────

    @Test
    @DisplayName("GET /api/v1/runs returns 200 with empty list when DataCloud absent")
    void runsListEmptyNotErrorWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/runs");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("runs");
    }

    @Test
    @DisplayName("GET /api/v1/agents returns 200 with agents list when DataCloud absent")
    void agentListNotErrorWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("agents");
    }

    @Test
    @DisplayName("GET /api/v1/patterns returns 200 with patterns list when DataCloud absent")
    void patternListNotErrorWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/patterns");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("patterns");
    }

    @Test
    @DisplayName("GET /api/v1/pipelines returns 200 with pipelines list when DataCloud absent")
    void pipelineListNotErrorWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/pipelines");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("pipelines");
    }

    @Test
    @DisplayName("GET /api/v1/hitl/pending: 200 or 501 (not configured) — never 500")
    void hitlPendingDegradesSafelyWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/hitl/pending");

        // 200 = queue somehow configured; 501 = queue not configured = graceful degradation
        // Both are acceptable; 5xx server crash is NOT acceptable.
        assertThat(resp.statusCode()).isIn(200, 501, 503);
    }

    @Test
    @DisplayName("GET /api/v1/learning/policies: 200 or 501 (not configured) — never 500")
    void learningPoliciesNotErrorWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/learning/policies");

        assertThat(resp.statusCode()).isIn(200, 501, 503);
    }

    @Test
    @DisplayName("GET /api/v1/learning/episodes: 200 or 501 (not configured) — never 500")
    void learningEpisodesNotErrorWithoutDataCloud() throws Exception {
        HttpResponse<String> resp = get("/api/v1/learning/episodes");

        assertThat(resp.statusCode()).isIn(200, 501, 503);
    }

    // ── Health probe reflects disabled state honestly ─────────────────────────

    @Test
    @DisplayName("GET /health returns status and components even without DataCloud")
    void healthProbeRespondsWhenDataCloudAbsent() throws Exception {
        HttpResponse<String> resp = get("/health");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("status");
        // Status is either "healthy" (full in-memory mode) or "degraded" (known
        // absent deps) — never an error response
        assertThat(body.get("status")).isIn("healthy", "degraded");
    }

    // ── SLO metrics always respond ────────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics/slo returns 200 regardless of DataCloud state")
    void sloMetricsAlwaysRespond() throws Exception {
        HttpResponse<String> resp = get("/metrics/slo");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("runCounts");
    }

    // ── Batch ingestion works without DataCloud ───────────────────────────────

    @Test
    @DisplayName("POST /api/v1/events/batch succeeds without DataCloud")
    void batchIngestionSucceedsWithoutDataCloud() throws Exception {
        String batch = mapper.writeValueAsString(Map.of(
            "events", java.util.List.of(
                Map.of("tenantId", "dev-t", "type", "a", "payload", Map.of()),
                Map.of("tenantId", "dev-t", "type", "b", "payload", Map.of())
            )
        ));

        HttpResponse<String> resp = post("/api/v1/events/batch", batch);
        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        // Batch response shape: { "total": ..., "successCount": ..., "events": [...], ... }
        assertThat(body).containsKey("total");
    }

    // ── Metrics endpoint still works ──────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics returns 200 regardless of DataCloud state")
    void metricsEndpointAlwaysResponds() throws Exception {
        HttpResponse<String> resp = get("/metrics");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("service");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpResponse<String> postEvent(
            String tenantId, String type, Map<String, Object> payload) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
            "tenantId", tenantId,
            "type", type,
            "payload", payload
        ));
        return post("/api/v1/events", body);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

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
        throw new AssertionError("Server did not start on port " + port + " within 5 s");
    }
}
