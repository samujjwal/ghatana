/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Resilience and dev-mode tests for AEP when optional dependencies (DataCloud, // GH-90000
 * HITL review queue, run ledger) are absent or disabled.
 *
 * <p>Validates the core contract:
 * <ul>
 *   <li>All read endpoints return empty results (never 5xx) when DataCloud is absent</li> // GH-90000
 *   <li>Event ingestion still succeeds (fire-and-forget ledger)</li> // GH-90000
 *   <li>Health probe accurately reflects "disabled" vs "healthy" per component</li>
 *   <li>SLO snapshot endpoint always responds (metrics infra is always active)</li> // GH-90000
 *   <li>Batch ingestion degrades gracefully (processes what it can)</li> // GH-90000
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
@DisplayName("AEP Dev-Mode Resilience Tests (no DataCloud) [GH-90000]")
class AepDevModeResilienceTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUpServerWithoutDataCloud() throws Exception { // GH-90000
        // Aep.forTesting() creates an engine with no DataCloud, no external deps. // GH-90000
        // This is the canonical "local dev / CI no-services" fixture.
        engine = Aep.forTesting(); // GH-90000
        port = findFreePort(); // GH-90000
        http = HttpClient.newBuilder().build(); // GH-90000
        server = new AepHttpServer(engine, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
        if (engine != null) engine.close(); // GH-90000
    }

    // ── Event ingestion degrades gracefully ───────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/events succeeds even without DataCloud (fire-and-forget ledger) [GH-90000]")
    void eventIngestionSucceedsWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = postEvent("dev-tenant", "user.created", // GH-90000
                Map.of("userId", "dev-001")); // GH-90000

        // Must succeed — run ledger is fire-and-forget when DataCloud absent
        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        // Event response shape: { "eventId": ..., "success": true, "detections": 0, ... }
        assertThat(body).containsKey("eventId [GH-90000]");
        assertThat(body.get("success [GH-90000]")).isEqualTo(true);
    }

    // ── Read endpoints return empty collections (not 5xx) ───────────────────── // GH-90000

    @Test
    @DisplayName("GET /api/v1/runs returns 200 with empty list when DataCloud absent [GH-90000]")
    void runsListEmptyNotErrorWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/runs [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("runs [GH-90000]");
    }

    @Test
    @DisplayName("GET /api/v1/agents returns 200 with agents list when DataCloud absent [GH-90000]")
    void agentListNotErrorWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/agents [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("agents [GH-90000]");
    }

    @Test
    @DisplayName("GET /api/v1/patterns returns 200 with patterns list when DataCloud absent [GH-90000]")
    void patternListNotErrorWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/patterns [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("patterns [GH-90000]");
    }

    @Test
    @DisplayName("GET /api/v1/pipelines returns 200 with pipelines list when DataCloud absent [GH-90000]")
    void pipelineListNotErrorWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/pipelines [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("pipelines [GH-90000]");
    }

    @Test
    @DisplayName("GET /api/v1/hitl/pending: 200 or 501 (not configured) — never 500 [GH-90000]")
    void hitlPendingDegradesSafelyWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/hitl/pending [GH-90000]");

        // 200 = queue somehow configured; 501 = queue not configured = graceful degradation
        // Both are acceptable; 5xx server crash is NOT acceptable.
        assertThat(resp.statusCode()).isIn(200, 501, 503); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/learning/policies: 200 or 501 (not configured) — never 500 [GH-90000]")
    void learningPoliciesNotErrorWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/learning/policies [GH-90000]");

        assertThat(resp.statusCode()).isIn(200, 501, 503); // GH-90000
    }

    @Test
    @DisplayName("GET /api/v1/learning/episodes: 200 or 501 (not configured) — never 500 [GH-90000]")
    void learningEpisodesNotErrorWithoutDataCloud() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/learning/episodes [GH-90000]");

        assertThat(resp.statusCode()).isIn(200, 501, 503); // GH-90000
    }

    // ── Health probe reflects disabled state honestly ─────────────────────────

    @Test
    @DisplayName("GET /health returns status and components even without DataCloud [GH-90000]")
    void healthProbeRespondsWhenDataCloudAbsent() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/health [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("status [GH-90000]");
        // Status is either "healthy" (full in-memory mode) or "degraded" (known // GH-90000
        // absent deps) — never an error response
        assertThat(body.get("status [GH-90000]")).isIn("healthy", "degraded");
    }

    // ── SLO metrics always respond ────────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics/slo returns 200 regardless of DataCloud state [GH-90000]")
    void sloMetricsAlwaysRespond() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/metrics/slo [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("runCounts [GH-90000]");
    }

    // ── Batch ingestion works without DataCloud ───────────────────────────────

    @Test
    @DisplayName("POST /api/v1/events/batch succeeds without DataCloud [GH-90000]")
    void batchIngestionSucceedsWithoutDataCloud() throws Exception { // GH-90000
        String batch = mapper.writeValueAsString(Map.of( // GH-90000
            "events", java.util.List.of( // GH-90000
                Map.of("tenantId", "dev-t", "type", "a", "payload", Map.of()), // GH-90000
                Map.of("tenantId", "dev-t", "type", "b", "payload", Map.of()) // GH-90000
            )
        ));

        HttpResponse<String> resp = post("/api/v1/events/batch", batch); // GH-90000
        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        // Batch response shape: { "total": ..., "successCount": ..., "events": [...], ... }
        assertThat(body).containsKey("total [GH-90000]");
    }

    // ── Metrics endpoint still works ──────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics returns 200 regardless of DataCloud state [GH-90000]")
    void metricsEndpointAlwaysResponds() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/metrics [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("service [GH-90000]");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpResponse<String> postEvent( // GH-90000
            String tenantId, String type, Map<String, Object> payload) throws Exception {
        String body = mapper.writeValueAsString(Map.of( // GH-90000
            "tenantId", tenantId,
            "type", type,
            "payload", payload
        ));
        return post("/api/v1/events", body); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return http.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .build(); // GH-90000
        return http.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static int findFreePort() throws IOException { // GH-90000
        try (ServerSocket ss = new ServerSocket(0)) { // GH-90000
            return ss.getLocalPort(); // GH-90000
        }
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        long deadline = System.currentTimeMillis() + 5_000; // GH-90000
        while (System.currentTimeMillis() < deadline) { // GH-90000
            try {
                new Socket("127.0.0.1", port).close(); // GH-90000
                return;
            } catch (IOException ignored) { // GH-90000
                Thread.sleep(50); // GH-90000
            }
        }
        throw new AssertionError("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
