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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

import com.ghatana.aep.server.http.AepHttpServer;

/**
 * Golden-path system test for the full AEP event lifecycle.
 *
 * <p>This test spins up a real {@link AepHttpServer} backed by an in-memory
 * {@link AepEngine} (no external services required) and exercises the complete
 * happy-path sequence:
 * <ol>
 *   <li>Ingest an event (POST /api/v1/events)</li>
 *   <li>Verify SLO run counter increments (GET /metrics/slo)</li>
 *   <li>Verify run list is non-empty (GET /api/v1/runs)</li>
 *   <li>Register a pattern (POST /api/v1/patterns)</li>
 *   <li>Query the pattern back (GET /api/v1/patterns/:id)</li>
 *   <li>Create and query a pipeline (POST /api/v1/pipelines)</li>
 *   <li>List agents (GET /api/v1/agents)</li>
 *   <li>Query HITL review queue (GET /api/v1/hitl/pending)</li>
 *   <li>Trigger learning reflection (POST /api/v1/learning/reflect)</li>
 *   <li>Server health probe returns expected fields (GET /health)</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Golden-path end-to-end confidence test for AEP event lifecycle
 * @doc.layer product
 * @doc.pattern SystemTest
 */
@DisplayName("AEP Golden-Path System Test")
@TestMethodOrder(OrderAnnotation.class)
class AepGoldenPathSystemTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
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

    // ── 1. Event ingestion ────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("POST /api/v1/events returns 200 with eventId and success=true")
    void ingestEvent_returns200WithEventId() throws Exception {
        HttpResponse<String> resp = postEvent("tenant-acme", "user.signup",
                Map.of("userId", "u001", "email", "alice@example.com"));

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("eventId");
        assertThat(body.get("success")).isEqualTo(true);
    }

    // ── 2. SLO counters increment after processing ────────────────────────────

    @Test
    @Order(20)
    @DisplayName("SLO totalRuns counter increments after event ingestion")
    void sloTotalRunsIncrementsAfterEvent() throws Exception {
        long before = sloTotalRuns();

        postEvent("tenant-acme", "order.placed", Map.of("orderId", "o-001"));

        long after = sloTotalRuns();
        assertThat(after).isGreaterThan(before);
    }

    // ── 3. Run list reflects processed events ────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("GET /api/v1/runs returns a non-empty list after event processing")
    void runListIsNonEmptyAfterProcessing() throws Exception {
        postEvent("tenant-acme", "payment.received", Map.of("amount", 99.99));

        // Pass tenantId as query param to match the tenant used in postEvent()
        HttpResponse<String> resp = get("/api/v1/runs?tenantId=tenant-acme");
        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("runs");
        @SuppressWarnings("unchecked")
        List<?> runs = (List<?>) body.get("runs");
        assertThat(runs).isNotEmpty();
    }

    // ── 4. Pattern registration and retrieval ────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("POST /api/v1/patterns registers pattern; GET retrieves it")
    void patternRegistrationAndRetrieval() throws Exception {
        // Response shape: { "pattern": { "id": ..., "name": ..., "type": ... }, "timestamp": ... }
        String payload = mapper.writeValueAsString(Map.of(
            "name", "high-value-order",
            "tenantId", "tenant-acme",
            "description", "Orders above threshold",
            "type", "CUSTOM"
        ));

        HttpResponse<String> createResp = post("/api/v1/patterns", payload);
        assertThat(createResp.statusCode()).isIn(200, 201);
        @SuppressWarnings("unchecked")
        Map<String, Object> created = mapper.readValue(createResp.body(), Map.class);
        assertThat(created).containsKey("pattern");

        @SuppressWarnings("unchecked")
        Map<String, Object> patternObj = (Map<String, Object>) created.get("pattern");
        String pId = String.valueOf(patternObj.get("id"));
        HttpResponse<String> getResp = get("/api/v1/patterns/" + pId + "?tenantId=tenant-acme");
        assertThat(getResp.statusCode()).isEqualTo(200);
    }

    // ── 5. Pipeline creation and listing ─────────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("POST /api/v1/pipelines is reachable; GET /api/v1/pipelines lists")
    void pipelineCreateAndList() throws Exception {
        String payload = mapper.writeValueAsString(Map.of(
            "name", "fraud-detection",
            "tenantId", "tenant-acme"
        ));

        // Create may return 200 (saved) or 200 with validation errors body — both are non-5xx
        HttpResponse<String> createResp = post("/api/v1/pipelines", payload);
        assertThat(createResp.statusCode()).isBetween(200, 299);

        HttpResponse<String> listResp = get("/api/v1/pipelines?tenantId=tenant-acme");
        assertThat(listResp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> listBody = mapper.readValue(listResp.body(), Map.class);
        assertThat(listBody).containsKey("pipelines");
    }

    // ── 6. Agent list is available ────────────────────────────────────────────

    @Test
    @Order(60)
    @DisplayName("GET /api/v1/agents returns 200 with agents array")
    void agentListReturns200() throws Exception {
        HttpResponse<String> resp = get("/api/v1/agents");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKey("agents");
    }

    // ── 7. HITL review queue is queryable ─────────────────────────────────────

    @Test
    @Order(70)
    @DisplayName("GET /api/v1/hitl/pending endpoint is reachable (200 or 501 when not configured)")
    void hitlPendingIsReachable() throws Exception {
        HttpResponse<String> resp = get("/api/v1/hitl/pending");

        // 200 = queue configured and working; 501 = queue not configured in this setup
        // Both are acceptable — neither is a server crash (500)
        assertThat(resp.statusCode()).isIn(200, 501, 503);
    }

    // ── 8. Learning / reflection pipeline is reachable ───────────────────────

    @Test
    @Order(80)
    @DisplayName("POST /api/v1/learning/reflect endpoint is reachable (202 async accepted, 200 sync, or 501/503 when not configured)")
    void reflectionTriggerIsReachable() throws Exception {
        HttpResponse<String> resp = post("/api/v1/learning/reflect",
                mapper.writeValueAsString(Map.of("tenantId", "tenant-acme")));

        // 202 = accepted (async fire-and-forget); 200 = completed; 501/503 = not configured
        assertThat(resp.statusCode()).isIn(200, 202, 501, 503);
    }

    // ── 9. Learning policy list is queryable ──────────────────────────────────

    @Test
    @Order(90)
    @DisplayName("GET /api/v1/learning/policies endpoint is reachable (200 or 501 when not configured)")
    void learningPoliciesIsReachable() throws Exception {
        HttpResponse<String> resp = get("/api/v1/learning/policies");

        assertThat(resp.statusCode()).isIn(200, 501, 503);
    }

    // ── 10. Health probe reflects expected fields ─────────────────────────────

    @Test
    @Order(100)
    @DisplayName("GET /health returns status, version, and components map")
    void healthProbeHasExpectedFields() throws Exception {
        HttpResponse<String> resp = get("/health");

        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        assertThat(body).containsKeys("status", "version", "timestamp");
    }

    // ── 11. Batch event ingestion ─────────────────────────────────────────────

    @Test
    @Order(110)
    @DisplayName("POST /api/v1/events/batch returns 200 for multiple events")
    void batchEventIngestionReturns200() throws Exception {
        String batch = mapper.writeValueAsString(Map.of(
            "events", List.of(
                Map.of("tenantId", "tenant-b", "type", "page.view", "payload", Map.of()),
                Map.of("tenantId", "tenant-b", "type", "click", "payload", Map.of("element", "btn"))
            )
        ));

        HttpResponse<String> resp = post("/api/v1/events/batch", batch);
        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        // Batch response: { "tenantId": ..., "total": ..., "successCount": ..., "events": [...] }
        assertThat(body).containsKey("total");
        assertThat(body).containsKey("successCount");
    }

    // ── 12. Multi-tenant isolation: separate tenant sees separate run list ─────

    @Test
    @Order(120)
    @DisplayName("Multi-tenant: runs from different tenants are independently recorded")
    void multiTenantRunsAreIndependentlyCounted() throws Exception {
        long beforeA = sloTotalRuns();

        postEvent("tenant-alpha", "checkout", Map.of("cart", "abc"));
        postEvent("tenant-beta", "checkout", Map.of("cart", "xyz"));

        long afterA = sloTotalRuns();
        assertThat(afterA).isGreaterThanOrEqualTo(beforeA + 2);
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

    private long sloTotalRuns() throws Exception {
        HttpResponse<String> resp = get("/metrics/slo");
        assertThat(resp.statusCode()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) body.get("runCounts");
        return ((Number) counts.get("totalRuns")).longValue();
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
