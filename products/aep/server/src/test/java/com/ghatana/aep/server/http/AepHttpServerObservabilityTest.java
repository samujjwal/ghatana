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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Phase-6 observability endpoints.
 *
 * <p>Covers {@code GET /health} (dependency aggregation), {@code GET /metrics/slo}
 * (SLO snapshot), and basic event-processing → SLO counter wiring.
 *
 * @doc.type class
 * @doc.purpose Integration tests for Phase-6 health + SLO metric endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepHttpServer – Observability (Phase-6)")
class AepHttpServerObservabilityTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        engine = Aep.forTesting();
        port = findFreePort();
        httpClient = HttpClient.newBuilder().build();
        server = new AepHttpServer(engine, port);
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
        if (engine != null) engine.close();
    }

    // ──────────────────────────────────────────────────────────────
    // /health — dependency probe responses
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /health")
    class HealthEndpoint {

        @Test
        @DisplayName("returns 200 with status field")
        void returns200WithStatus() throws Exception {
            HttpResponse<String> resp = get("/health");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("status");
            assertThat(body).containsKey("version");
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("reports component statuses for no-DataCloud setup")
        void reportsComponentStatusesWithNoDataCloud() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(get("/health").body(), Map.class);
            // Without DataCloud, data-cloud and run-ledger are "disabled" → degraded overall
            assertThat(body.get("status")).isIn("healthy", "degraded");
            if (body.containsKey("components")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> components = (Map<String, Object>) body.get("components");
                assertThat(components).containsKey("data-cloud");
                assertThat(components).containsKey("review-queue");
                assertThat(components).containsKey("run-ledger");
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // /metrics/slo — SLO snapshot endpoint
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /metrics/slo")
    class SloMetricsEndpoint {

        @Test
        @DisplayName("returns 200 with runCounts snapshot")
        void returns200WithRunCounts() throws Exception {
            HttpResponse<String> resp = get("/metrics/slo");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("runCounts");
            assertThat(body).containsKey("metricsLink");
            assertThat(body).containsKey("timestamp");
        }

        @Test
        @DisplayName("runCounts contains totalRuns, failedRuns, runFailureRate")
        void runCountsHasExpectedFields() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> runCounts = (Map<String, Object>) body.get("runCounts");
            assertThat(runCounts).containsKeys("totalRuns", "failedRuns", "runFailureRate");
        }

        @Test
        @DisplayName("run counters increment after processing an event")
        void runCountersIncrementAfterEventProcessing() throws Exception {
            // Baseline
            @SuppressWarnings("unchecked")
            Map<String, Object> before = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> beforeCounts = (Map<String, Object>) before.get("runCounts");
            long totalBefore = ((Number) beforeCounts.get("totalRuns")).longValue();

            // Process an event
            String event = mapper.writeValueAsString(Map.of(
                "tenantId", "test-tenant",
                "type", "user.action",
                "payload", Map.of("key", "value")
            ));
            HttpResponse<String> eventResp = post("/api/v1/events", event);
            assertThat(eventResp.statusCode()).isEqualTo(200);

            // Check counters increased
            @SuppressWarnings("unchecked")
            Map<String, Object> after = mapper.readValue(get("/metrics/slo").body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> afterCounts = (Map<String, Object>) after.get("runCounts");
            long totalAfter = ((Number) afterCounts.get("totalRuns")).longValue();
            assertThat(totalAfter).isGreaterThan(totalBefore);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // /metrics/slo should be accessible alongside /metrics
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /metrics")
    class MetricsEndpoint {

        @Test
        @DisplayName("still returns 200 with JVM stats after Phase-6 changes")
        void returnsJvmStats() throws Exception {
            HttpResponse<String> resp = get("/metrics");
            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("service");
            assertThat(body).containsKey("memory_used_mb");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json")
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
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
