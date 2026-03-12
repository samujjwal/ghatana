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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Data-Cloud HTTP learning endpoints (DC-8).
 *
 * <p>Starts a real {@link DataCloudHttpServer} on a random port and makes HTTP calls via the
 * Java standard HttpClient. {@link DataCloudClient} and {@link DataCloudLearningBridge}
 * are mocked so tests remain self-contained.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/learning/** HTTP endpoints (DC-8)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Learning Endpoints (DC-8)")
class DataCloudHttpServerLearningTest {

    private DataCloudClient mockClient;
    private DataCloudLearningBridge mockBridge;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        mockBridge = mock(DataCloudLearningBridge.class);
        port       = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ==================== No bridge (null) ====================

    @Nested
    @DisplayName("Learning bridge not wired (null)")
    class NoBridgeTests {

        @Test
        @DisplayName("POST /trigger → 503 when bridge is null")
        void trigger_noBridge_returns503() throws Exception {
            startWithoutBridge();
            HttpResponse<String> resp = post("/api/v1/learning/trigger", "{}");
            assertThat(resp.statusCode()).isEqualTo(503);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("error").toString()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("GET /status → 503 when bridge is null")
        void status_noBridge_returns503() throws Exception {
            startWithoutBridge();
            HttpResponse<String> resp = get("/api/v1/learning/status");
            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("GET /review → 503 when bridge is null")
        void review_noBridge_returns503() throws Exception {
            startWithoutBridge();
            HttpResponse<String> resp = get("/api/v1/learning/review");
            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("POST /review/:id/approve → 503 when bridge is null")
        void approve_noBridge_returns503() throws Exception {
            startWithoutBridge();
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-1/approve", "");
            assertThat(resp.statusCode()).isEqualTo(503);
        }

        @Test
        @DisplayName("POST /review/:id/reject → 503 when bridge is null")
        void reject_noBridge_returns503() throws Exception {
            startWithoutBridge();
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-1/reject", "");
            assertThat(resp.statusCode()).isEqualTo(503);
        }
    }

    // ==================== POST /api/v1/learning/trigger ====================

    @Nested
    @DisplayName("POST /api/v1/learning/trigger")
    class TriggerTests {

        @Test
        @DisplayName("successful trigger → 200 with COMPLETED result")
        void trigger_success_returns200() throws Exception {
            Map<String, Object> result = Map.of(
                "status", "COMPLETED",
                "tenantId", "default",
                "manual", true,
                "patternsDiscovered", 3,
                "patternsUpdated", 1,
                "recordsAnalyzed", 200L,
                "durationMs", 42L,
                "ranAt", "2026-01-24T10:00:00Z"
            );
            when(mockBridge.runLearning(anyString(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(result);

            startWithBridge();
            HttpResponse<String> resp = post("/api/v1/learning/trigger", "{}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("COMPLETED");
            assertThat(((Number) body.get("patternsDiscovered")).intValue()).isEqualTo(3);
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("SKIPPED result → still 200")
        void trigger_skipped_returns200() throws Exception {
            when(mockBridge.runLearning(anyString(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(Map.of("status", "SKIPPED", "reason", "already running"));

            startWithBridge();
            HttpResponse<String> resp = post("/api/v1/learning/trigger", "{}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("status")).isEqualTo("SKIPPED");
        }
    }

    // ==================== GET /api/v1/learning/status ====================

    @Nested
    @DisplayName("GET /api/v1/learning/status")
    class StatusTests {

        @Test
        @DisplayName("returns 200 with all required status fields")
        void status_wiredBridge_returns200WithFields() throws Exception {
            when(mockBridge.getStatus()).thenReturn(Map.of(
                "running",          false,
                "lastRunTime",      "2026-01-24T09:00:00Z",
                "nextScheduledRun", "2026-01-24T09:05:00Z",
                "intervalMinutes",  5L,
                "pendingReviews",   2L,
                "lastResult",       Map.of("status", "COMPLETED")
            ));

            startWithBridge();
            HttpResponse<String> resp = get("/api/v1/learning/status");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("running")).isEqualTo(false);
            assertThat(body.get("lastRunTime")).isEqualTo("2026-01-24T09:00:00Z");
            assertThat(body.get("intervalMinutes")).isNotNull();
            assertThat(body.get("pendingReviews")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }
    }

    // ==================== GET /api/v1/learning/review ====================

    @Nested
    @DisplayName("GET /api/v1/learning/review")
    class ReviewQueueTests {

        @Test
        @DisplayName("returns 200 with items and count")
        void reviewQueue_returns200WithItemsAndCount() throws Exception {
            Map<String, Map<String, Object>> items = Map.of(
                "rev-1", Map.of("reviewId", "rev-1", "status", "PENDING", "confidence", 0.3f),
                "rev-2", Map.of("reviewId", "rev-2", "status", "APPROVED", "confidence", 0.5f)
            );
            when(mockBridge.getReviewQueue()).thenReturn(items);

            startWithBridge();
            HttpResponse<String> resp = get("/api/v1/learning/review");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(2);
            assertThat(body.get("items")).isNotNull();
            assertThat(body.get("timestamp")).isNotNull();
        }

        @Test
        @DisplayName("empty review queue → 200 with count=0")
        void reviewQueue_empty_returnsZeroCount() throws Exception {
            when(mockBridge.getReviewQueue()).thenReturn(Map.of());

            startWithBridge();
            HttpResponse<String> resp = get("/api/v1/learning/review");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(((Number) body.get("count")).intValue()).isEqualTo(0);
        }
    }

    // ==================== POST /api/v1/learning/review/:id/approve ====================

    @Nested
    @DisplayName("POST /api/v1/learning/review/:id/approve")
    class ApproveTests {

        @Test
        @DisplayName("known review ID → 200 with APPROVED decision")
        void approve_knownItem_returns200() throws Exception {
            when(mockBridge.approveReview("rev-abc")).thenReturn(true);

            startWithBridge();
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-abc/approve", "");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("reviewId")).isEqualTo("rev-abc");
            assertThat(body.get("decision")).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("unknown review ID → 404")
        void approve_unknownItem_returns404() throws Exception {
            when(mockBridge.approveReview("no-such")).thenReturn(false);

            startWithBridge();
            HttpResponse<String> resp = post("/api/v1/learning/review/no-such/approve", "");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ==================== POST /api/v1/learning/review/:id/reject ====================

    @Nested
    @DisplayName("POST /api/v1/learning/review/:id/reject")
    class RejectTests {

        @Test
        @DisplayName("known review ID → 200 with REJECTED decision")
        void reject_knownItem_returns200() throws Exception {
            when(mockBridge.rejectReview("rev-xyz")).thenReturn(true);

            startWithBridge();
            HttpResponse<String> resp = post("/api/v1/learning/review/rev-xyz/reject", "");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<?, ?> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body.get("reviewId")).isEqualTo("rev-xyz");
            assertThat(body.get("decision")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("unknown review ID → 404")
        void reject_unknownItem_returns404() throws Exception {
            when(mockBridge.rejectReview("ghost")).thenReturn(false);

            startWithBridge();
            HttpResponse<String> resp = post("/api/v1/learning/review/ghost/reject", "");

            assertThat(resp.statusCode()).isEqualTo(404);
        }
    }

    // ==================== Helpers ====================

    private void startWithBridge() throws Exception {
        server = new DataCloudHttpServer(mockClient, port, null, mockBridge, null);
        server.start();
        waitForServerReady(port);
    }

    private void startWithoutBridge() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(port);
    }

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
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s");
    }
}
