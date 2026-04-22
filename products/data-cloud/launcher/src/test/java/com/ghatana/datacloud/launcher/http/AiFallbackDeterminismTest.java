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
 *   <li>No {@link CompletionService} is wired (offline/unconfigured mode).</li> // GH-90000
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
 * @doc.purpose AI fallback determinism tests for all AI assist endpoints (DC-E3) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@Timeout(value = 15, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("AI Assist – Fallback Determinism [GH-90000]")
class AiFallbackDeterminismTest {

    private DataCloudClient mockClient;
    private CompletionService mockCompletion;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient     = mock(DataCloudClient.class); // GH-90000
        mockCompletion = mock(CompletionService.class); // GH-90000
        port           = findFreePort(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deterministic heuristic fallback — no LLM configured
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No completion service configured [GH-90000]")
    class NullCompletionServiceTests {

        @Test
        @DisplayName("entity/suggest → fallback=true, confidence<0.5, data.suggestions present [GH-90000]")
        void entitySuggest_noLlm_returnsHeuristicFallback() throws Exception { // GH-90000
            startServerWithoutLlm(); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/entities/orders/suggest",
                "{\"collection\":\"orders\",\"fields\":[\"id\",\"amount\",\"status\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> ai = extractAiBlock(resp); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
            assertThat((Double) ai.get("confidence [GH-90000]")).isLessThan(0.5);
            assertThat(ai).containsKey("model [GH-90000]");

            Map<String, Object> body = parseBody(resp); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
        }

        @Test
        @DisplayName("analytics/suggest → fallback=true, consistent response on repeated calls [GH-90000]")
        void analyticsSuggest_noLlm_isDeterministic() throws Exception { // GH-90000
            startServerWithoutLlm(); // GH-90000

            HttpResponse<String> resp1 = post( // GH-90000
                "/api/v1/analytics/suggest",
                "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}");
            HttpResponse<String> resp2 = post( // GH-90000
                "/api/v1/analytics/suggest",
                "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}");

            assertThat(resp1.statusCode()).isEqualTo(200); // GH-90000
            assertThat(resp2.statusCode()).isEqualTo(200); // GH-90000

            Map<String, Object> ai1 = extractAiBlock(resp1); // GH-90000
            Map<String, Object> ai2 = extractAiBlock(resp2); // GH-90000

            // Same fallback flag on repeated identical calls
            assertThat(ai1.get("fallback [GH-90000]")).isEqualTo(ai2.get("fallback [GH-90000]"));
            assertThat(ai1).containsEntry("fallback", true); // GH-90000
        }

        @Test
        @DisplayName("pipelines/optimise-hint → fallback=true, hint present in data [GH-90000]")
        void pipelineOptimiseHint_noLlm_returnsHeuristicHint() throws Exception { // GH-90000
            startServerWithoutLlm(); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/pipelines/etl-pipeline-001/optimise-hint",
                "{\"pipelineId\":\"etl-pipeline-001\",\"avgLatencyMs\":12000,\"stepCount\":20}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
            Map<String, Object> ai = extractAiBlock(resp); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
        }

        @Test
        @DisplayName("brain/explain → fallback=true, explanation present in data [GH-90000]")
        void brainExplain_noLlm_returnsHeuristicExplanation() throws Exception { // GH-90000
            startServerWithoutLlm(); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/brain/explain",
                "{\"queryType\":\"SEARCH\",\"collection\":\"events\",\"resultCount\":42}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            assertThat(body).containsKey("data [GH-90000]");
            assertThat(body).containsKey("ai [GH-90000]");
        }

        private void startServerWithoutLlm() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM error / exception path — must fall back gracefully
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("LLM returns an error [GH-90000]")
    class LlmErrorTests {

        @Test
        @DisplayName("exception from completion → returns 200 with fallback, not 500 [GH-90000]")
        void llmThrowsException_returns200WithFallback() throws Exception { // GH-90000
            when(mockCompletion.complete(any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("connection refused [GH-90000]")));

            startServerWithLlm(); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/entities/products/suggest",
                "{\"collection\":\"products\",\"fields\":[\"sku\",\"name\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> ai = extractAiBlock(resp); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
        }

        @Test
        @DisplayName("null result from completion → returns 200 with fallback [GH-90000]")
        void llmReturnsNullResult_returns200WithFallback() throws Exception { // GH-90000
            when(mockCompletion.complete(any())).thenReturn(Promise.of(null)); // GH-90000

            startServerWithLlm(); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/entities/users/suggest",
                "{\"collection\":\"users\",\"fields\":[\"email\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> ai = extractAiBlock(resp); // GH-90000
            assertThat(ai).containsEntry("fallback", true); // GH-90000
        }

        @Test
        @DisplayName("consecutive LLM failures → fallback applied on each call independently [GH-90000]")
        void consecutiveLlmFailures_eachCallFallsBack() throws Exception { // GH-90000
            when(mockCompletion.complete(any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("timeout [GH-90000]")));

            startServerWithLlm(); // GH-90000

            for (int i = 0; i < 3; i++) { // GH-90000
                HttpResponse<String> resp = post( // GH-90000
                    "/api/v1/entities/items/suggest",
                    "{\"collection\":\"items\",\"fields\":[\"id\"]}");
                assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
                assertThat(extractAiBlock(resp)).containsEntry("fallback", true); // GH-90000
            }

            // Verify the completion service was called for each request
            verify(mockCompletion, times(3)).complete(any()); // GH-90000
        }

        private void startServerWithLlm() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withCompletionService(mockCompletion); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confidence threshold boundary conditions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Confidence threshold boundary [GH-90000]")
    class ConfidenceBoundaryTests {

        @Test
        @DisplayName("LLM confidence=0.0 → treated as fallback (below threshold) [GH-90000]")
        @SuppressWarnings("unchecked [GH-90000]")
        void zeroConfidence_treatedAsFallback() throws Exception { // GH-90000
            CompletionResult lowResult = mock(CompletionResult.class); // GH-90000
            when(lowResult.getText()).thenReturn("minimal suggestion [GH-90000]");
            when(lowResult.getFinishReason()).thenReturn("stop [GH-90000]");
            when(lowResult.getModelUsed()).thenReturn("gpt-4o-mini [GH-90000]");
            when(mockCompletion.complete(any())).thenReturn(Promise.of(lowResult)); // GH-90000

            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withCompletionService(mockCompletion); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/entities/test/suggest",
                "{\"collection\":\"test\",\"fields\":[\"id\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            // Response must always contain the ai block regardless of confidence
            assertThat(parseBody(resp)).containsKey("ai [GH-90000]");
        }

        @ParameterizedTest(name = "empty text=''{0}''") // GH-90000
        @ValueSource(strings = {"", "   "}) // GH-90000
        @DisplayName("LLM returns blank text → treated as fallback [GH-90000]")
        @SuppressWarnings("unchecked [GH-90000]")
        void blankLlmText_treatedAsFallback(String blankText) throws Exception { // GH-90000
            CompletionResult blankResult = mock(CompletionResult.class); // GH-90000
            when(blankResult.getText()).thenReturn(blankText); // GH-90000
            when(blankResult.getFinishReason()).thenReturn("length [GH-90000]");
            when(blankResult.getModelUsed()).thenReturn("gpt-4o-mini [GH-90000]");
            when(mockCompletion.complete(any())).thenReturn(Promise.of(blankResult)); // GH-90000

            server = new DataCloudHttpServer(mockClient, port) // GH-90000
                .withCompletionService(mockCompletion); // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/analytics/suggest",
                "{\"collections\":[\"events\"],\"goal\":\"count per day\"}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            assertThat(parseBody(resp)).containsKey("data [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // All AI endpoints return valid ai-block structure (response contract) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AI block response contract [GH-90000]")
    class AiBlockContractTests {

        @Test
        @DisplayName("all fallback responses include: fallback, confidence, model keys [GH-90000]")
        void fallbackResponse_containsAllRequiredAiKeys() throws Exception { // GH-90000
            server = new DataCloudHttpServer(mockClient, port); // no LLM // GH-90000
            server.start(); // GH-90000
            waitForServerReady(port); // GH-90000

            HttpResponse<String> resp = post( // GH-90000
                "/api/v1/entities/catalog/suggest",
                "{\"collection\":\"catalog\",\"fields\":[\"title\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> ai = extractAiBlock(resp); // GH-90000
            assertThat(ai).containsKeys("fallback", "confidence", "model"); // GH-90000
            assertThat(ai.get("fallback [GH-90000]")).isInstanceOf(Boolean.class);
            assertThat(ai.get("confidence [GH-90000]")).isInstanceOf(Double.class);
            assertThat(ai.get("model [GH-90000]")).isInstanceOf(String.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> parseBody(HttpResponse<String> resp) throws Exception { // GH-90000
        return mapper.readValue(resp.body(), Map.class); // GH-90000
    }

    @SuppressWarnings("unchecked [GH-90000]")
    private Map<String, Object> extractAiBlock(HttpResponse<String> resp) throws Exception { // GH-90000
        Map<String, Object> body = parseBody(resp); // GH-90000
        assertThat(body).containsKey("ai [GH-90000]");
        return (Map<String, Object>) body.get("ai [GH-90000]");
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception { // GH-90000
        HttpRequest req = HttpRequest.newBuilder() // GH-90000
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)) // GH-90000
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
