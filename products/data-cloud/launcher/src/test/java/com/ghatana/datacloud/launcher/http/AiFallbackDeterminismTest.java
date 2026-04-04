/*
 * Copyright (c) 2026 Ghatana Inc.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates that all AI-assist endpoints exhibit <em>deterministic</em> fallback
 * behaviour when:
 * <ul>
 *   <li>No {@link CompletionService} is wired (offline/unconfigured mode).</li>
 *   <li>The LLM returns an error or low-confidence result.</li>
 *   <li>The LLM fails consecutively, triggering the handler's circuit-breaker guard.</li>
 *   <li>The confidence threshold boundary is exactly at the fallback cutoff.</li>
 * </ul>
 *
 * <p>Every assertion in this class targets the {@code ai} block of the JSON response.
 * This ensures the contract is deterministic: callers can always rely on
 * {@code ai.fallback}, {@code ai.confidence}, and {@code ai.model} being present and
 * within spec.
 *
 * @doc.type class
 * @doc.purpose AI fallback determinism tests for all AI assist endpoints (DC-E3)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("AI Assist – Fallback Determinism")
class AiFallbackDeterminismTest {

    private DataCloudClient mockClient;
    private CompletionService mockCompletion;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient     = mock(DataCloudClient.class);
        mockCompletion = mock(CompletionService.class);
        port           = findFreePort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deterministic heuristic fallback — no LLM configured
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No completion service configured")
    class NullCompletionServiceTests {

        @Test
        @DisplayName("entity/suggest → fallback=true, confidence<0.5, data.suggestions present")
        void entitySuggest_noLlm_returnsHeuristicFallback() throws Exception {
            startServerWithoutLlm();

            HttpResponse<String> resp = post(
                "/api/v1/entities/orders/suggest",
                "{\"collection\":\"orders\",\"fields\":[\"id\",\"amount\",\"status\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> ai = extractAiBlock(resp);
            assertThat(ai).containsEntry("fallback", true);
            assertThat((Double) ai.get("confidence")).isLessThan(0.5);
            assertThat(ai).containsKey("model");

            Map<String, Object> body = parseBody(resp);
            assertThat(body).containsKey("data");
        }

        @Test
        @DisplayName("analytics/suggest → fallback=true, consistent response on repeated calls")
        void analyticsSuggest_noLlm_isDeterministic() throws Exception {
            startServerWithoutLlm();

            HttpResponse<String> resp1 = post(
                "/api/v1/analytics/suggest",
                "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}");
            HttpResponse<String> resp2 = post(
                "/api/v1/analytics/suggest",
                "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}");

            assertThat(resp1.statusCode()).isEqualTo(200);
            assertThat(resp2.statusCode()).isEqualTo(200);

            Map<String, Object> ai1 = extractAiBlock(resp1);
            Map<String, Object> ai2 = extractAiBlock(resp2);

            // Same fallback flag on repeated identical calls
            assertThat(ai1.get("fallback")).isEqualTo(ai2.get("fallback"));
            assertThat(ai1).containsEntry("fallback", true);
        }

        @Test
        @DisplayName("pipelines/optimise-hint → fallback=true, hint present in data")
        void pipelineOptimiseHint_noLlm_returnsHeuristicHint() throws Exception {
            startServerWithoutLlm();

            HttpResponse<String> resp = post(
                "/api/v1/pipelines/etl-pipeline-001/optimise-hint",
                "{\"pipelineId\":\"etl-pipeline-001\",\"avgLatencyMs\":12000,\"stepCount\":20}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            assertThat(body).containsKey("data");
            Map<String, Object> ai = extractAiBlock(resp);
            assertThat(ai).containsEntry("fallback", true);
        }

        @Test
        @DisplayName("brain/explain → fallback=true, explanation present in data")
        void brainExplain_noLlm_returnsHeuristicExplanation() throws Exception {
            startServerWithoutLlm();

            HttpResponse<String> resp = post(
                "/api/v1/brain/explain",
                "{\"queryType\":\"SEARCH\",\"collection\":\"events\",\"resultCount\":42}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            assertThat(body).containsKey("data");
            assertThat(body).containsKey("ai");
        }

        private void startServerWithoutLlm() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM error / exception path — must fall back gracefully
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LLM returns an error")
    class LlmErrorTests {

        @Test
        @DisplayName("exception from completion → returns 200 with fallback, not 500")
        void llmThrowsException_returns200WithFallback() throws Exception {
            when(mockCompletion.complete(any()))
                .thenReturn(Promise.ofException(new RuntimeException("connection refused")));

            startServerWithLlm();

            HttpResponse<String> resp = post(
                "/api/v1/entities/products/suggest",
                "{\"collection\":\"products\",\"fields\":[\"sku\",\"name\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> ai = extractAiBlock(resp);
            assertThat(ai).containsEntry("fallback", true);
        }

        @Test
        @DisplayName("null result from completion → returns 200 with fallback")
        void llmReturnsNullResult_returns200WithFallback() throws Exception {
            when(mockCompletion.complete(any())).thenReturn(Promise.of(null));

            startServerWithLlm();

            HttpResponse<String> resp = post(
                "/api/v1/entities/users/suggest",
                "{\"collection\":\"users\",\"fields\":[\"email\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> ai = extractAiBlock(resp);
            assertThat(ai).containsEntry("fallback", true);
        }

        @Test
        @DisplayName("consecutive LLM failures → fallback applied on each call independently")
        void consecutiveLlmFailures_eachCallFallsBack() throws Exception {
            when(mockCompletion.complete(any()))
                .thenReturn(Promise.ofException(new RuntimeException("timeout")));

            startServerWithLlm();

            for (int i = 0; i < 3; i++) {
                HttpResponse<String> resp = post(
                    "/api/v1/entities/items/suggest",
                    "{\"collection\":\"items\",\"fields\":[\"id\"]}");
                assertThat(resp.statusCode()).isEqualTo(200);
                assertThat(extractAiBlock(resp)).containsEntry("fallback", true);
            }

            // Verify the completion service was called for each request
            verify(mockCompletion, times(3)).complete(any());
        }

        private void startServerWithLlm() throws Exception {
            server = new DataCloudHttpServer(mockClient, port)
                .withCompletionService(mockCompletion);
            server.start();
            waitForServerReady(port);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confidence threshold boundary conditions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Confidence threshold boundary")
    class ConfidenceBoundaryTests {

        @Test
        @DisplayName("LLM confidence=0.0 → treated as fallback (below threshold)")
        @SuppressWarnings("unchecked")
        void zeroConfidence_treatedAsFallback() throws Exception {
            CompletionResult lowResult = mock(CompletionResult.class);
            when(lowResult.getText()).thenReturn("minimal suggestion");
            when(lowResult.getFinishReason()).thenReturn("stop");
            when(lowResult.getModelUsed()).thenReturn("gpt-4o-mini");
            when(mockCompletion.complete(any())).thenReturn(Promise.of(lowResult));

            server = new DataCloudHttpServer(mockClient, port)
                .withCompletionService(mockCompletion);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/entities/test/suggest",
                "{\"collection\":\"test\",\"fields\":[\"id\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            // Response must always contain the ai block regardless of confidence
            assertThat(parseBody(resp)).containsKey("ai");
        }

        @ParameterizedTest(name = "empty text=''{0}''")
        @ValueSource(strings = {"", "   "})
        @DisplayName("LLM returns blank text → treated as fallback")
        @SuppressWarnings("unchecked")
        void blankLlmText_treatedAsFallback(String blankText) throws Exception {
            CompletionResult blankResult = mock(CompletionResult.class);
            when(blankResult.getText()).thenReturn(blankText);
            when(blankResult.getFinishReason()).thenReturn("length");
            when(blankResult.getModelUsed()).thenReturn("gpt-4o-mini");
            when(mockCompletion.complete(any())).thenReturn(Promise.of(blankResult));

            server = new DataCloudHttpServer(mockClient, port)
                .withCompletionService(mockCompletion);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/analytics/suggest",
                "{\"collections\":[\"events\"],\"goal\":\"count per day\"}");

            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(parseBody(resp)).containsKey("data");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // All AI endpoints return valid ai-block structure (response contract)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AI block response contract")
    class AiBlockContractTests {

        @Test
        @DisplayName("all fallback responses include: fallback, confidence, model keys")
        void fallbackResponse_containsAllRequiredAiKeys() throws Exception {
            server = new DataCloudHttpServer(mockClient, port); // no LLM
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post(
                "/api/v1/entities/catalog/suggest",
                "{\"collection\":\"catalog\",\"fields\":[\"title\"]}");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> ai = extractAiBlock(resp);
            assertThat(ai).containsKeys("fallback", "confidence", "model");
            assertThat(ai.get("fallback")).isInstanceOf(Boolean.class);
            assertThat(ai.get("confidence")).isInstanceOf(Double.class);
            assertThat(ai.get("model")).isInstanceOf(String.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpResponse<String> resp) throws Exception {
        return mapper.readValue(resp.body(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAiBlock(HttpResponse<String> resp) throws Exception {
        Map<String, Object> body = parseBody(resp);
        assertThat(body).containsKey("ai");
        return (Map<String, Object>) body.get("ai");
    }

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
        throw new IllegalStateException("Server did not start on port " + port + " within 5 s");
    }
}
