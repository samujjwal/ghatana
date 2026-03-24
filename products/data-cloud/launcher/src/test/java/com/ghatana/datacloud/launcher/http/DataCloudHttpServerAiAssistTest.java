/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.DataCloudClient;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AI-assist endpoints (DC-E3).
 *
 * <p>Verifies both LLM-backed and heuristic-fallback modes.  The
 * {@link CompletionService} is mocked; the server is started on a real port.
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI assist HTTP endpoints (DC-E3)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – AI Assist Endpoints (DC-E3)")
class DataCloudHttpServerAiAssistTest {

    private DataCloudClient mockClient;
    private CompletionService mockCompletion;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient    = mock(DataCloudClient.class);
        mockCompletion = mock(CompletionService.class);
        port          = findFreePort();
        httpClient    = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ──────────────────── POST /api/v1/entities/:collection/suggest ────────────────────

    @Nested
    @DisplayName("POST /api/v1/entities/:collection/suggest")
    class EntitySuggestTests {

        @Test
        @DisplayName("returns 200 with suggestion when LLM is wired")
        @SuppressWarnings("unchecked")
        void withLlm_returns200WithSuggestion() throws Exception {
            CompletionResult result = mock(CompletionResult.class);
            when(result.getText()).thenReturn("You could add an 'updatedAt' timestamp field.");
            when(result.getFinishReason()).thenReturn("stop");
            when(result.getModelUsed()).thenReturn("gpt-4o");
            // Must mock the Promise returned by complete(), then stub getResult() on it
            io.activej.promise.Promise<CompletionResult> mockPromise = mock(io.activej.promise.Promise.class);
            when(mockPromise.getResult()).thenReturn(result);
            when(mockCompletion.complete(any())).thenReturn(mockPromise);

            server = new DataCloudHttpServer(mockClient, port)
                .withCompletionService(mockCompletion);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/entities/users/suggest",
                "{\"collection\":\"users\",\"fields\":[\"id\",\"email\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked") Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("data");
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) body.get("data");
            // LLM response is wrapped as {"suggestions": [...]} by the handler
            assertThat(data).containsKey("suggestions");
            assertThat(body).containsKey("ai");
            @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) body.get("ai");
            assertThat((Double) ai.get("confidence")).isGreaterThan(0.0);
            assertThat(ai).containsEntry("fallback", false);
        }

        @Test
        @DisplayName("returns 200 with heuristic fallback when LLM is not wired")
        void withoutLlm_returns200WithHeuristicFallback() throws Exception {
            server = new DataCloudHttpServer(mockClient, port); // no LLM
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/entities/orders/suggest",
                "{\"collection\":\"orders\",\"fields\":[\"id\",\"amount\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked") Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("data");
            assertThat(body).containsKey("ai");
            @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) body.get("ai");
            assertThat(ai).containsEntry("fallback", true);
            assertThat((Double) ai.get("confidence")).isLessThan(0.5);
        }

        @Test
        @DisplayName("returns 200 with heuristic fallback when LLM throws")
        @SuppressWarnings("unchecked")
        void withLlmError_fallsBackGracefully() throws Exception {
            io.activej.promise.Promise<CompletionResult> failingPromise = mock(io.activej.promise.Promise.class);
            when(failingPromise.getResult()).thenThrow(new RuntimeException("LLM timeout"));
            when(mockCompletion.complete(any())).thenReturn(failingPromise);

            server = new DataCloudHttpServer(mockClient, port)
                .withCompletionService(mockCompletion);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/entities/products/suggest",
                "{\"collection\":\"products\",\"fields\":[\"sku\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked") Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) body.get("ai");
            assertThat(ai).containsEntry("fallback", true);
        }
    }

    // ──────────────────── POST /api/v1/analytics/suggest ────────────────────

    @Nested
    @DisplayName("POST /api/v1/analytics/suggest")
    class AnalyticsSuggestTests {

        @Test
        @DisplayName("returns 200 with heuristic when no LLM")
        void noLlm_returns200WithHeuristic() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/analytics/suggest",
                "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked") Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("data");
            @SuppressWarnings("unchecked") Map<String, Object> ai = (Map<String, Object>) body.get("ai");
            assertThat(ai).containsEntry("fallback", true);
        }
    }

    // ──────────────────── POST /api/v1/pipelines/:id/optimise-hint ────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/:pipelineId/optimise-hint")
    class PipelineOptimiseHintTests {

        @Test
        @DisplayName("returns 200 with heuristic hint when no LLM")
        void noLlm_returns200WithHint() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/pipelines/etl-001/optimise-hint",
                "{\"pipelineId\":\"etl-001\",\"avgLatencyMs\":8000,\"stepCount\":12}");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked") Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("data");
        }
    }

    // ──────────────────── POST /api/v1/brain/explain ────────────────────

    @Nested
    @DisplayName("POST /api/v1/brain/explain")
    class BrainExplainTests {

        @Test
        @DisplayName("returns 200 with heuristic explanation when no LLM")
        void noLlm_returns200WithExplanation() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/brain/explain",
                "{\"queryType\":\"SEARCH\",\"collection\":\"events\",\"resultCount\":42}");

            assertThat(resp.statusCode()).isEqualTo(200);
            @SuppressWarnings("unchecked") Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("data");
            assertThat(body).containsKey("ai");
        }
    }

    // ──────────────────── Helpers ────────────────────

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port);
    }
}
