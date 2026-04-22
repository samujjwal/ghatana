/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AI-assist endpoints (DC-E3). // GH-90000
 *
 * <p>Verifies both LLM-backed and heuristic-fallback modes.  The
 * {@link CompletionService} is mocked; the server is started on a real port.
 *
 * <p><strong>Fixed (2026-04-03):</strong> The previous hang was caused by mocking // GH-90000
 * {@code Promise} objects directly and calling {@code .getResult()} on them. The fix // GH-90000
 * replaces mocked Promises with real {@link Promise#of(Object)} / {@link Promise#ofException} // GH-90000
 * so the ActiveJ Eventloop can process them correctly within the HTTP handler lifecycle.
 * Per-test timeouts guard against any future regressions.
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI assist HTTP endpoints (DC-E3) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@Timeout(value = 15, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("DataCloudHttpServer – AI Assist Endpoints (DC-E3) [GH-90000]")
class DataCloudHttpServerAiAssistTest {

    private DataCloudClient mockClient;
    private CompletionService mockCompletion;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient    = mock(DataCloudClient.class); // GH-90000
        mockCompletion = mock(CompletionService.class); // GH-90000
        port          = findFreePort(); // GH-90000
        httpClient    = HttpClient.newBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ──────────────────── POST /api/v1/entities/:collection/suggest ────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection/suggest [GH-90000]")
    class EntitySuggestTests {

        @Test
        @DisplayName("returns 200 with suggestion when LLM is wired [GH-90000]")
        void withLlm_returns200WithSuggestion() throws Exception { // GH-90000
            CompletionResult result = mock(CompletionResult.class); // GH-90000
            when(result.getText()).thenReturn("You could add an 'updatedAt' timestamp field. [GH-90000]");
            when(result.getFinishReason()).thenReturn("stop [GH-90000]");
            when(result.getModelUsed()).thenReturn("gpt-4o [GH-90000]");
            // Use real Promise.of() so the ActiveJ Eventloop can process it correctly // GH-90000
            when(mockCompletion.complete(any())).thenReturn(Promise.of(result)); // GH-90000

            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withCompletionService(mockCompletion); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/entities/users/suggest", // GH-90000
                "{\"collection\":\"users\",\"fields\":[\"id\",\"email\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
            Map<String, Object> data = readChildObject(body, "data"); // GH-90000
            // LLM response is wrapped as {"suggestions": [...]} by the handler
            assertThat(data).containsKey("suggestions [GH-90000]");
            assertThat(body).containsKey("ai [GH-90000]");
            Map<String, Object> ai = readChildObject(body, "ai"); // GH-90000
            assertThat((Double) ai.get("confidence [GH-90000]")).isGreaterThan(0.0);
            assertThat(ai).containsEntry("fallback", false); // GH-90000
        }

        @Test
        @DisplayName("returns 200 with heuristic fallback when LLM is not wired [GH-90000]")
        void withoutLlm_returns200WithHeuristicFallback() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // no LLM // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/entities/orders/suggest", // GH-90000
                "{\"collection\":\"orders\",\"fields\":[\"id\",\"amount\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
            assertThat(body).containsKey("ai [GH-90000]");
            Map<String, Object> ai = readChildObject(body, "ai"); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
            assertThat((Double) ai.get("confidence [GH-90000]")).isLessThan(0.5);
        }

        @Test
        @DisplayName("returns 200 with heuristic fallback when LLM throws [GH-90000]")
        void withLlmError_fallsBackGracefully() throws Exception { // GH-90000
            // Use Promise.ofException() so the Eventloop propagates the failure to the handler // GH-90000
            when(mockCompletion.complete(any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("LLM timeout [GH-90000]")));

            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withCompletionService(mockCompletion); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/entities/products/suggest", // GH-90000
                "{\"collection\":\"products\",\"fields\":[\"sku\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            Map<String, Object> ai = readChildObject(body, "ai"); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
        }
    }

    // ──────────────────── POST /api/v1/analytics/suggest ────────────────────

    @Nested
    @DisplayName("POST /api/v1/analytics/suggest [GH-90000]")
    class AnalyticsSuggestTests {

        @Test
        @DisplayName("returns 200 with heuristic when no LLM [GH-90000]")
        void noLlm_returns200WithHeuristic() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/analytics/suggest", // GH-90000
                "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
            Map<String, Object> ai = readChildObject(body, "ai"); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
        }

        @Test
        @DisplayName("quality summary exposes fallback rates and separates workflow draft telemetry [GH-90000]")
        @SuppressWarnings("unchecked [GH-90000]")
        void qualitySummary_reportsTypeMetrics() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            post("/api/v1/analytics/suggest", "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}"); // GH-90000
            post("/api/v1/pipelines/draft", "{\"prompt\":\"Load customer data and validate records\"}"); // GH-90000

            HttpResponse<String> resp = get("/api/v1/ai/quality-summary [GH-90000]");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            Map<String, Object> data = readChildObject(body, "data"); // GH-90000
            assertThat(data).containsEntry("scope", "launcher-process"); // GH-90000
            Map<String, Object> summary = readChildObject(data, "summary"); // GH-90000
            assertThat(summary).containsEntry("requestCount", 2); // GH-90000
            assertThat(summary).containsEntry("fallbackCount", 2); // GH-90000
            assertThat(summary).containsEntry("llmConfigured", false); // GH-90000

            java.util.List<Map<String, Object>> types = (java.util.List<Map<String, Object>>) data.get("types [GH-90000]");
            Map<String, Object> analyticsSuggest = types.stream() // GH-90000
                .filter(type -> "analytics_suggest".equals(type.get("type [GH-90000]")))
                .findFirst() // GH-90000
                .orElseThrow(); // GH-90000
            Map<String, Object> pipelineDraft = types.stream() // GH-90000
                .filter(type -> "pipeline_draft".equals(type.get("type [GH-90000]")))
                .findFirst() // GH-90000
                .orElseThrow(); // GH-90000

            assertThat(analyticsSuggest).containsEntry("fallbackCount", 1); // GH-90000
            assertThat(analyticsSuggest).containsEntry("requestCount", 1); // GH-90000
            assertThat(analyticsSuggest).containsEntry("route", "/api/v1/analytics/suggest"); // GH-90000
            assertThat(pipelineDraft).containsEntry("fallbackCount", 1); // GH-90000
            assertThat(pipelineDraft).containsEntry("route", "/api/v1/pipelines/draft"); // GH-90000
            assertThat(pipelineDraft).containsEntry("provenanceMode", "ai-envelope-and-draft-provenance"); // GH-90000
        }
    }

    // ──────────────────── POST /api/v1/pipelines/:id/optimise-hint ────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/draft [GH-90000]")
    class PipelineDraftTests {

        @Test
        @DisplayName("returns 200 with heuristic draft when no LLM [GH-90000]")
        void noLlm_returns200WithDraft() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/pipelines/draft", // GH-90000
                "{\"prompt\":\"Load customer data, validate records, save to warehouse\"}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            Map<String, Object> data = readChildObject(body, "data"); // GH-90000
            assertThat(data).containsKeys("workflowId", "name", "description", "steps", "provenance"); // GH-90000
            assertThat(data.get("steps [GH-90000]")).isInstanceOfAny(java.util.List.class);
            Map<String, Object> ai = readChildObject(body, "ai"); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
        }
    }

    @Nested
    @DisplayName("POST /api/v1/pipelines/:pipelineId/optimise-hint [GH-90000]")
    class PipelineOptimiseHintTests {

        @Test
        @DisplayName("returns 200 with heuristic hint when no LLM [GH-90000]")
        void noLlm_returns200WithHint() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/pipelines/etl-001/optimise-hint", // GH-90000
                "{\"pipelineId\":\"etl-001\",\"avgLatencyMs\":8000,\"stepCount\":12}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
        }
    }

    // ──────────────────── POST /api/v1/brain/explain ────────────────────

    @Nested
    @DisplayName("POST /api/v1/brain/explain [GH-90000]")
    class BrainExplainTests {

        @Test
        @DisplayName("returns 200 with heuristic explanation when no LLM [GH-90000]")
        void noLlm_returns200WithExplanation() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post("/api/v1/brain/explain", // GH-90000
                "{\"queryType\":\"SEARCH\",\"collection\":\"events\",\"resultCount\":42}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = readJsonObject(resp.body()); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
            assertThat(body).containsKey("ai [GH-90000]");
        }
    }

    // ──────────────────── Helpers ────────────────────

    private HttpResponse<String> post(String path, String jsonBody) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)) // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("Content-Type", "application/json") // GH-90000
            .header("X-Tenant-ID", "tenant-a") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .GET() // GH-90000
            .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
            .header("X-Tenant-ID", "tenant-a") // GH-90000
            .build(); // GH-90000
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> readJsonObject(String payload) throws IOException { // GH-90000
        return mapper.readValue(payload, Map.class); // GH-90000
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> readChildObject(Map<String, Object> parent, String key) { // GH-90000
        return (Map<String, Object>) parent.get(key); // GH-90000
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port); // GH-90000
    }
}
