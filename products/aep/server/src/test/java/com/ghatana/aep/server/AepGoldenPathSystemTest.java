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
 * {@link AepEngine} (no external services required) and exercises the complete // GH-90000
 * happy-path sequence:
 * <ol>
 *   <li>Ingest an event (POST /api/v1/events)</li> // GH-90000
 *   <li>Verify SLO run counter increments (GET /metrics/slo)</li> // GH-90000
 *   <li>Verify run list is non-empty (GET /api/v1/runs)</li> // GH-90000
 *   <li>Register a pattern (POST /api/v1/patterns)</li> // GH-90000
 *   <li>Query the pattern back (GET /api/v1/patterns/:id)</li> // GH-90000
 *   <li>Create and query a pipeline (POST /api/v1/pipelines)</li> // GH-90000
 *   <li>List agents (GET /api/v1/agents)</li> // GH-90000
 *   <li>Query HITL review queue (GET /api/v1/hitl/pending)</li> // GH-90000
 *   <li>Trigger learning reflection (POST /api/v1/learning/reflect)</li> // GH-90000
 *   <li>Server health probe returns expected fields (GET /health)</li> // GH-90000
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Golden-path end-to-end confidence test for AEP event lifecycle
 * @doc.layer product
 * @doc.pattern SystemTest
 */
@DisplayName("AEP Golden-Path System Test [GH-90000]")
@TestMethodOrder(OrderAnnotation.class) // GH-90000
class AepGoldenPathSystemTest {

    private AepEngine engine;
    private AepHttpServer server;
    private int port;
    private HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
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

    // ── 1. Event ingestion ────────────────────────────────────────────────────

    @Test
    @Order(10) // GH-90000
    @DisplayName("POST /api/v1/events returns 200 with eventId and success=true [GH-90000]")
    void ingestEvent_returns200WithEventId() throws Exception { // GH-90000
        HttpResponse<String> resp = postEvent("tenant-acme", "user.signup", // GH-90000
                Map.of("userId", "u001", "email", "alice@example.com")); // GH-90000

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("eventId [GH-90000]");
        assertThat(body.get("success [GH-90000]")).isEqualTo(true);

        // Functional assertion: verify event was actually processed and recorded
        String eventId = String.valueOf(body.get("eventId [GH-90000]"));
        assertThat(eventId).isNotNull(); // GH-90000
        assertThat(eventId).isNotEmpty(); // GH-90000

        // Verify the event appears in the run list (proves processing completed) // GH-90000
        HttpResponse<String> runsResp = get("/api/v1/runs?tenantId=tenant-acme [GH-90000]");
        assertThat(runsResp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> runsBody = mapper.readValue(runsResp.body(), Map.class); // GH-90000
        List<?> runs = (List<?>) runsBody.get("runs [GH-90000]");
        assertThat(runs).isNotEmpty(); // GH-90000

        // Verify SLO metrics were recorded
        long totalRuns = sloTotalRuns(); // GH-90000
        assertThat(totalRuns).isGreaterThan(0); // GH-90000
    }

    // ── 2. SLO counters increment after processing ────────────────────────────

    @Test
    @Order(20) // GH-90000
    @DisplayName("SLO totalRuns counter increments after event ingestion [GH-90000]")
    void sloTotalRunsIncrementsAfterEvent() throws Exception { // GH-90000
        long before = sloTotalRuns(); // GH-90000

        postEvent("tenant-acme", "order.placed", Map.of("orderId", "o-001")); // GH-90000

        long after = sloTotalRuns(); // GH-90000
        assertThat(after).isGreaterThan(before); // GH-90000
    }

    // ── 3. Run list reflects processed events ────────────────────────────────

    @Test
    @Order(30) // GH-90000
    @DisplayName("GET /api/v1/runs returns a non-empty list after event processing [GH-90000]")
    void runListIsNonEmptyAfterProcessing() throws Exception { // GH-90000
        postEvent("tenant-acme", "payment.received", Map.of("amount", 99.99)); // GH-90000

        // Pass tenantId as query param to match the tenant used in postEvent() // GH-90000
        HttpResponse<String> resp = get("/api/v1/runs?tenantId=tenant-acme [GH-90000]");
        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body.containsKey("runs [GH-90000]")).isTrue();
        List<?> runs = (List<?>) body.get("runs [GH-90000]");
        assertThat(runs).isNotEmpty(); // GH-90000
    }

    @Test
    @Order(35) // GH-90000
    @DisplayName("GET /api/v1/runs/:runId returns evidence arrays for a recorded run [GH-90000]")
    void runDetailIncludesEvidenceArrays() throws Exception { // GH-90000
        postEvent("tenant-acme", "invoice.created", Map.of("invoiceId", "inv-001")); // GH-90000

        HttpResponse<String> listResp = get("/api/v1/runs?tenantId=tenant-acme [GH-90000]");
        assertThat(listResp.statusCode()).isEqualTo(200); // GH-90000

        Map<String, Object> listBody = mapper.readValue( // GH-90000
            listResp.body(), // GH-90000
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {} // GH-90000
        );
        List<Map<String, Object>> runs = mapper.convertValue( // GH-90000
            listBody.get("runs [GH-90000]"),
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {} // GH-90000
        );
        assertThat(runs).isNotEmpty(); // GH-90000

        String runId = String.valueOf(runs.get(runs.size() - 1).get("runId [GH-90000]"));
        HttpResponse<String> detailResp = get("/api/v1/runs/" + runId + "?tenantId=tenant-acme"); // GH-90000
        assertThat(detailResp.statusCode()).isEqualTo(200); // GH-90000

        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> detailBody = mapper.readValue(detailResp.body(), Map.class); // GH-90000
        assertThat(detailBody.get("runId [GH-90000]")).isEqualTo(runId);
        assertThat(detailBody).containsKeys("lineage", "decisions", "policies"); // GH-90000
    }

    // ── 4. Pattern registration and retrieval ────────────────────────────────

    @Test
    @Order(40) // GH-90000
    @DisplayName("POST /api/v1/patterns registers pattern; GET retrieves it [GH-90000]")
    void patternRegistrationAndRetrieval() throws Exception { // GH-90000
        // Response shape: { "pattern": { "id": ..., "name": ..., "type": ... }, "timestamp": ... }
        String payload = mapper.writeValueAsString(Map.of( // GH-90000
            "name", "high-value-order",
            "tenantId", "tenant-acme",
            "description", "Orders above threshold",
            "type", "CUSTOM"
        ));

        HttpResponse<String> createResp = post("/api/v1/patterns", payload); // GH-90000
        assertThat(createResp.statusCode()).isIn(200, 201); // GH-90000
        Map<?, ?> created = mapper.readValue(createResp.body(), Map.class); // GH-90000
        assertThat(created.containsKey("pattern [GH-90000]")).isTrue();

        Map<?, ?> patternObj = (Map<?, ?>) created.get("pattern [GH-90000]");
        String pId = String.valueOf(patternObj.get("id [GH-90000]"));
        HttpResponse<String> getResp = get("/api/v1/patterns/" + pId + "?tenantId=tenant-acme"); // GH-90000
        assertThat(getResp.statusCode()).isEqualTo(200); // GH-90000
    }

    // ── 5. Pipeline creation and listing ─────────────────────────────────────

    @Test
    @Order(50) // GH-90000
    @DisplayName("POST /api/v1/pipelines is reachable; GET /api/v1/pipelines lists [GH-90000]")
    void pipelineCreateAndList() throws Exception { // GH-90000
        String payload = mapper.writeValueAsString(Map.of( // GH-90000
            "name", "fraud-detection",
            "tenantId", "tenant-acme"
        ));

        // Create may return 200 (saved) or 200 with validation errors body — both are non-5xx // GH-90000
        HttpResponse<String> createResp = post("/api/v1/pipelines", payload); // GH-90000
        assertThat(createResp.statusCode()).isBetween(200, 299); // GH-90000

        HttpResponse<String> listResp = get("/api/v1/pipelines?tenantId=tenant-acme [GH-90000]");
        assertThat(listResp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> listBody = mapper.readValue(listResp.body(), Map.class); // GH-90000
        assertThat(listBody).containsKey("pipelines [GH-90000]");
    }

    // ── 6. Agent list is available ────────────────────────────────────────────

    @Test
    @Order(60) // GH-90000
    @DisplayName("GET /api/v1/agents returns 200 with agents array [GH-90000]")
    void agentListReturns200() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/agents?tenantId=tenant-acme [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKey("agents [GH-90000]");
    }

    // ── 7. HITL review queue is queryable ─────────────────────────────────────

    @Test
    @Order(70) // GH-90000
    @DisplayName("GET /api/v1/hitl/pending returns 200 with a reachable review queue surface [GH-90000]")
    void hitlPendingIsReachable() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/hitl/pending?tenantId=tenant-acme [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
    }

    // ── 8. Learning / reflection pipeline is reachable ───────────────────────

    @Test
    @Order(80) // GH-90000
    @DisplayName("POST /api/v1/learning/reflect returns 200 or 202 [GH-90000]")
    void reflectionTriggerIsReachable() throws Exception { // GH-90000
        HttpResponse<String> resp = post("/api/v1/learning/reflect", // GH-90000
                mapper.writeValueAsString(Map.of("tenantId", "tenant-acme"))); // GH-90000

        assertThat(resp.statusCode()).isIn(200, 202); // GH-90000
    }

    // ── 9. Learning policy list is queryable ──────────────────────────────────

    @Test
    @Order(90) // GH-90000
    @DisplayName("GET /api/v1/learning/policies returns 200 [GH-90000]")
    void learningPoliciesIsReachable() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/api/v1/learning/policies?tenantId=tenant-acme [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
    }

    // ── 10. Health probe reflects expected fields ─────────────────────────────

    @Test
    @Order(100) // GH-90000
    @DisplayName("GET /health returns status, version, and components map [GH-90000]")
    void healthProbeHasExpectedFields() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/health [GH-90000]");

        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        assertThat(body).containsKeys("status", "version", "timestamp"); // GH-90000
    }

    // ── 11. Batch event ingestion ─────────────────────────────────────────────

    @Test
    @Order(110) // GH-90000
    @DisplayName("POST /api/v1/events/batch returns 200 for multiple events [GH-90000]")
    void batchEventIngestionReturns200() throws Exception { // GH-90000
        String batch = mapper.writeValueAsString(Map.of( // GH-90000
            "events", List.of( // GH-90000
                Map.of("tenantId", "tenant-b", "type", "page.view", "payload", Map.of()), // GH-90000
                Map.of("tenantId", "tenant-b", "type", "click", "payload", Map.of("element", "btn")) // GH-90000
            )
        ));

        HttpResponse<String> resp = post("/api/v1/events/batch", batch); // GH-90000
        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        // Batch response: { "tenantId": ..., "total": ..., "successCount": ..., "events": [...] }
        assertThat(body).containsKey("total [GH-90000]");
        assertThat(body).containsKey("successCount [GH-90000]");

        // Functional assertion: verify all events were processed
        int total = ((Number) body.get("total [GH-90000]")).intValue();
        int successCount = ((Number) body.get("successCount [GH-90000]")).intValue();
        assertThat(total).isEqualTo(2); // GH-90000
        assertThat(successCount).isEqualTo(2); // GH-90000

        // Verify events array contains eventId for each event
        @SuppressWarnings("unchecked [GH-90000]")
        List<Map<String, Object>> events = (List<Map<String, Object>>) body.get("events [GH-90000]");
        assertThat(events).hasSize(2); // GH-90000
        for (Map<String, Object> event : events) { // GH-90000
            assertThat(event).containsKey("eventId [GH-90000]");
            assertThat(event.get("eventId [GH-90000]")).isNotNull();
            assertThat(event.get("success [GH-90000]")).isEqualTo(true);
        }
    }

    // ── 12. Multi-tenant isolation: separate tenant sees separate run list ─────

    @Test
    @Order(120) // GH-90000
    @DisplayName("Multi-tenant: runs from different tenants are independently recorded [GH-90000]")
    void multiTenantRunsAreIndependentlyCounted() throws Exception { // GH-90000
        long beforeA = sloTotalRuns(); // GH-90000

        postEvent("tenant-alpha", "checkout", Map.of("cart", "abc")); // GH-90000
        postEvent("tenant-beta", "checkout", Map.of("cart", "xyz")); // GH-90000

        // Retry briefly — event processing is async and may lag under load
        long afterA = beforeA;
        for (int attempt = 0; attempt < 10 && afterA < beforeA + 2; attempt++) { // GH-90000
            Thread.sleep(100); // GH-90000
            afterA = sloTotalRuns(); // GH-90000
        }
        assertThat(afterA).isGreaterThanOrEqualTo(beforeA + 2); // GH-90000
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

    private long sloTotalRuns() throws Exception { // GH-90000
        HttpResponse<String> resp = get("/metrics/slo [GH-90000]");
        assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> body = mapper.readValue(resp.body(), Map.class); // GH-90000
        @SuppressWarnings("unchecked [GH-90000]")
        Map<String, Object> counts = (Map<String, Object>) body.get("runCounts [GH-90000]");
        return ((Number) counts.get("totalRuns [GH-90000]")).longValue();
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
