package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Data-Cloud HTTP learning endpoints (DC-8). // GH-90000
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes HTTP calls via the
 * Java standard HttpClient. {@link DataCloudClient} and {@link DataCloudLearningBridge}
 * are mocked so tests remain self-contained.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/learning/** HTTP endpoints (DC-8) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Learning Endpoints (DC-8) [GH-90000]")
class DataCloudHttpServerLearningTest {

    private DataCloudClient mockClient;
    private DataCloudLearningBridge mockBridge;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        mockBridge = mock(DataCloudLearningBridge.class); // GH-90000
        port       = findFreePort(); // GH-90000
        httpClient = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ==================== No bridge (null) ==================== // GH-90000

    @Nested
    @DisplayName("Learning bridge not wired (null) [GH-90000]")
    class NoBridgeTests {

        @Test
        @DisplayName("POST /trigger → 503 when bridge is null [GH-90000]")
        void trigger_noBridge_returns503() throws Exception { // GH-90000
            startWithoutBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/trigger", "{}"); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("message [GH-90000]").toString()).containsIgnoringCase("not available [GH-90000]");
        }

        @Test
        @DisplayName("GET /status → 503 when bridge is null [GH-90000]")
        void status_noBridge_returns503() throws Exception { // GH-90000
            startWithoutBridge(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/learning/status [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("GET /review → 503 when bridge is null [GH-90000]")
        void review_noBridge_returns503() throws Exception { // GH-90000
            startWithoutBridge(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/learning/review [GH-90000]");
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("POST /review/:id/approve → 503 when bridge is null [GH-90000]")
        void approve_noBridge_returns503() throws Exception { // GH-90000
            startWithoutBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-1/approve", ""); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }

        @Test
        @DisplayName("POST /review/:id/reject → 503 when bridge is null [GH-90000]")
        void reject_noBridge_returns503() throws Exception { // GH-90000
            startWithoutBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-1/reject", ""); // GH-90000
            assertThat(resp.statusCode()).isEqualTo(503); // GH-90000
        }
    }

    // ==================== POST /api/v1/learning/trigger ====================

    @Nested
    @DisplayName("POST /api/v1/learning/trigger [GH-90000]")
    class TriggerTests {

        @Test
        @DisplayName("successful trigger → 200 with COMPLETED result [GH-90000]")
        void trigger_success_returns200() throws Exception { // GH-90000
            Map<String, Object> result = Map.of( // GH-90000
                "status", "COMPLETED",
                "tenantId", "default",
                "manual", true,
                "patternsDiscovered", 3,
                "patternsUpdated", 1,
                "recordsAnalyzed", 200L,
                "durationMs", 42L,
                "ranAt", "2026-01-24T10:00:00Z"
            );
            when(mockBridge.runLearning(anyString(), org.mockito.ArgumentMatchers.eq(true))) // GH-90000
                .thenReturn(result); // GH-90000

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/trigger", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("COMPLETED [GH-90000]");
            assertThat(((Number) body.get("patternsDiscovered [GH-90000]")).intValue()).isEqualTo(3);
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("SKIPPED result → still 200 [GH-90000]")
        void trigger_skipped_returns200() throws Exception { // GH-90000
            when(mockBridge.runLearning(anyString(), org.mockito.ArgumentMatchers.eq(true))) // GH-90000
                .thenReturn(Map.of("status", "SKIPPED", "reason", "already running")); // GH-90000

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/trigger", "{}"); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("SKIPPED [GH-90000]");
        }
    }

    // ==================== GET /api/v1/learning/status ====================

    @Nested
    @DisplayName("GET /api/v1/learning/status [GH-90000]")
    class StatusTests {

        @Test
        @DisplayName("returns 200 with all required status fields [GH-90000]")
        void status_wiredBridge_returns200WithFields() throws Exception { // GH-90000
            when(mockBridge.getStatus()).thenReturn(Map.of( // GH-90000
                "running",          false,
                "lastRunTime",      "2026-01-24T09:00:00Z",
                "nextScheduledRun", "2026-01-24T09:05:00Z",
                "intervalMinutes",  5L,
                "pendingReviews",   2L,
                "lastResult",       Map.of("status", "COMPLETED") // GH-90000
            ));

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/learning/status [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("running [GH-90000]")).isEqualTo(false);
            assertThat(body.get("lastRunTime [GH-90000]")).isEqualTo("2026-01-24T09:00:00Z [GH-90000]");
            assertThat(body.get("intervalMinutes [GH-90000]")).isNotNull();
            assertThat(body.get("pendingReviews [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }
    }

    // ==================== GET /api/v1/learning/review ====================

    @Nested
    @DisplayName("GET /api/v1/learning/review [GH-90000]")
    class ReviewQueueTests {

        @Test
        @DisplayName("returns 200 with items and count [GH-90000]")
        void reviewQueue_returns200WithItemsAndCount() throws Exception { // GH-90000
            Map<String, Map<String, Object>> items = Map.of( // GH-90000
                "rev-1", Map.of("reviewId", "rev-1", "status", "PENDING", "confidence", 0.3f), // GH-90000
                "rev-2", Map.of("reviewId", "rev-2", "status", "APPROVED", "confidence", 0.5f) // GH-90000
            );
            when(mockBridge.getReviewQueue()).thenReturn(items); // GH-90000

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/learning/review [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(2);
            assertThat(body.get("items [GH-90000]")).isNotNull();
            assertThat(body.get("timestamp [GH-90000]")).isNotNull();
        }

        @Test
        @DisplayName("empty review queue → 200 with count=0 [GH-90000]")
        void reviewQueue_empty_returnsZeroCount() throws Exception { // GH-90000
            when(mockBridge.getReviewQueue()).thenReturn(Map.of()); // GH-90000

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = get("/api/v1/learning/review [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(((Number) body.get("count [GH-90000]")).intValue()).isEqualTo(0);
        }
    }

    // ==================== POST /api/v1/learning/review/:id/approve ====================

    @Nested
    @DisplayName("POST /api/v1/learning/review/:id/approve [GH-90000]")
    class ApproveTests {

        @Test
        @DisplayName("known review ID → 200 with APPROVED decision [GH-90000]")
        void approve_knownItem_returns200() throws Exception { // GH-90000
            when(mockBridge.approveReview("rev-abc [GH-90000]")).thenReturn(true);

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-abc/approve", ""); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("reviewId [GH-90000]")).isEqualTo("rev-abc [GH-90000]");
            assertThat(body.get("decision [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
        }

        @Test
        @DisplayName("unknown review ID → 404 [GH-90000]")
        void approve_unknownItem_returns404() throws Exception { // GH-90000
            when(mockBridge.approveReview("no-such [GH-90000]")).thenReturn(false);

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/review/no-such/approve", ""); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== POST /api/v1/learning/review/:id/reject ====================

    @Nested
    @DisplayName("POST /api/v1/learning/review/:id/reject [GH-90000]")
    class RejectTests {

        @Test
        @DisplayName("known review ID → 200 with REJECTED decision [GH-90000]")
        void reject_knownItem_returns200() throws Exception { // GH-90000
            when(mockBridge.rejectReview("rev-xyz [GH-90000]")).thenReturn(true);

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-xyz/reject", ""); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class); // GH-90000
            assertThat(body.get("reviewId [GH-90000]")).isEqualTo("rev-xyz [GH-90000]");
            assertThat(body.get("decision [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
        }

        @Test
        @DisplayName("unknown review ID → 404 [GH-90000]")
        void reject_unknownItem_returns404() throws Exception { // GH-90000
            when(mockBridge.rejectReview("ghost [GH-90000]")).thenReturn(false);

            startWithBridge(); // GH-90000
            HttpResponse<String> resp = post("/api/v1/learning/review/ghost/reject", ""); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // GH-90000
        }
    }

    // ==================== Helpers ====================

    private void startWithBridge() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port, null, mockBridge, null); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private void startWithoutBridge() throws Exception { // GH-90000
        server = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String body) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
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
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s"); // GH-90000
    }
}
