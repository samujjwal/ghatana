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
 * Integration tests for AI-assist endpoints (DC-E3). 
 *
 * <p>Verifies both LLM-backed and heuristic-fallback modes.  The
 * {@link CompletionService} is mocked; the server is started on a real port.
 *
 * <p><strong>Fixed (2026-04-03):</strong> The previous hang was caused by mocking 
 * {@code Promise} objects directly and calling {@code .getResult()} on them. The fix 
 * replaces mocked Promises with real {@link Promise#of(Object)} / {@link Promise#ofException} 
 * so the ActiveJ Eventloop can process them correctly within the HTTP handler lifecycle.
 * Per-test timeouts guard against any future regressions.
 *
 * @doc.type class
 * @doc.purpose Integration tests for AI assist HTTP endpoints (DC-E3) 
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@Timeout(value = 15, unit = TimeUnit.SECONDS) 
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
        void withLlm_returns200WithSuggestion() throws Exception { 
            CompletionResult result = mock(CompletionResult.class); 
            when(result.getFinishReason()).thenReturn("stop");
            when(result.getModelUsed()).thenReturn("gpt-4o");
            // Use real Promise.of() so the ActiveJ Eventloop can process it correctly 
            when(mockCompletion.complete(any())).thenReturn(Promise.of(result)); 

            server = new DataCloudHttpServer(mockClient, port) 
                .withCompletionService(mockCompletion); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/entities/users/suggest", 
                "{\"collection\":\"users\",\"fields\":[\"id\",\"email\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> body = readJsonObject(resp.body()); 
            assertThat(body).containsKey("data");
            Map<String, Object> data = readChildObject(body, "data"); 
            // LLM response is wrapped as {"suggestions": [...]} by the handler
            assertThat(data).containsKey("suggestions");
            assertThat(body).containsKey("ai");
            Map<String, Object> ai = readChildObject(body, "ai"); 
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
            Map<String, Object> body = readJsonObject(resp.body()); 
            assertThat(body).containsKey("data");
            assertThat(body).containsKey("ai");
            Map<String, Object> ai = readChildObject(body, "ai"); 
            assertThat(ai).containsEntry("fallback", true); 
            assertThat((Double) ai.get("confidence")).isLessThan(0.5);
        }

        @Test
        @DisplayName("returns 200 with heuristic fallback when LLM throws")
        void withLlmError_fallsBackGracefully() throws Exception { 
            // Use Promise.ofException() so the Eventloop propagates the failure to the handler 
            when(mockCompletion.complete(any())) 
                .thenReturn(Promise.ofException(new RuntimeException("LLM timeout")));

            server = new DataCloudHttpServer(mockClient, port) 
                .withCompletionService(mockCompletion); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/entities/products/suggest", 
                "{\"collection\":\"products\",\"fields\":[\"sku\"]}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> body = readJsonObject(resp.body()); 
            Map<String, Object> ai = readChildObject(body, "ai"); 
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
            Map<String, Object> body = readJsonObject(resp.body()); 
            assertThat(body).containsKey("data");
            Map<String, Object> ai = readChildObject(body, "ai"); 
            assertThat(ai).containsEntry("fallback", true); 
        }

        @Test
        @DisplayName("quality summary exposes fallback rates and separates workflow draft telemetry")
        @SuppressWarnings("unchecked")
        void qualitySummary_reportsTypeMetrics() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            post("/api/v1/analytics/suggest", "{\"collections\":[\"orders\"],\"goal\":\"revenue trend\"}"); 
            post("/api/v1/pipelines/draft", "{\"prompt\":\"Load customer data and validate records\"}"); 

            HttpResponse<String> resp = get("/api/v1/ai/quality-summary");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> body = readJsonObject(resp.body()); 
            Map<String, Object> data = readChildObject(body, "data"); 
            assertThat(data).containsEntry("scope", "launcher-process"); 
            Map<String, Object> summary = readChildObject(data, "summary"); 
            assertThat(summary).containsEntry("requestCount", 2); 
            assertThat(summary).containsEntry("fallbackCount", 2); 
            assertThat(summary).containsEntry("llmConfigured", false); 

            java.util.List<Map<String, Object>> types = (java.util.List<Map<String, Object>>) data.get("types");
            Map<String, Object> analyticsSuggest = types.stream() 
                .filter(type -> "analytics_suggest".equals(type.get("type")))
                .findFirst() 
                .orElseThrow(); 
            Map<String, Object> pipelineDraft = types.stream() 
                .filter(type -> "pipeline_draft".equals(type.get("type")))
                .findFirst() 
                .orElseThrow(); 

            assertThat(analyticsSuggest).containsEntry("fallbackCount", 1); 
            assertThat(analyticsSuggest).containsEntry("requestCount", 1); 
            assertThat(analyticsSuggest).containsEntry("route", "/api/v1/analytics/suggest"); 
            assertThat(pipelineDraft).containsEntry("fallbackCount", 1); 
            assertThat(pipelineDraft).containsEntry("route", "/api/v1/pipelines/draft"); 
            assertThat(pipelineDraft).containsEntry("provenanceMode", "ai-envelope-and-draft-provenance"); 
        }
    }

    // ──────────────────── POST /api/v1/pipelines/:id/optimise-hint ────────────────────

    @Nested
    @DisplayName("POST /api/v1/pipelines/draft")
    class PipelineDraftTests {

        @Test
        @DisplayName("returns 200 with heuristic draft when no LLM")
        void noLlm_returns200WithDraft() throws Exception { 
            server = new DataCloudHttpServer(mockClient, port); 
            server.start(); 
            waitForServerReady(port); 

            HttpResponse<String> resp = post("/api/v1/pipelines/draft", 
                "{\"prompt\":\"Load customer data, validate records, save to warehouse\"}");

            assertThat(resp.statusCode()).isEqualTo(200); 
            Map<String, Object> body = readJsonObject(resp.body()); 
            Map<String, Object> data = readChildObject(body, "data"); 
            assertThat(data).containsKeys("workflowId", "name", "description", "steps", "provenance"); 
            assertThat(data.get("steps")).isInstanceOfAny(java.util.List.class);
            Map<String, Object> ai = readChildObject(body, "ai"); 
            assertThat(ai).containsEntry("fallback", true); 
        }
    }

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
            Map<String, Object> body = readJsonObject(resp.body()); 
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
            Map<String, Object> body = readJsonObject(resp.body()); 
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
            .header("X-Tenant-ID", "tenant-a") 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    private HttpResponse<String> get(String path) throws Exception { 
        HttpRequest req = HttpRequest.newBuilder() 
            .GET() 
            .uri(URI.create("http://127.0.0.1:" + port + path)) 
            .header("X-Tenant-ID", "tenant-a") 
            .build(); 
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString()); 
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonObject(String payload) throws IOException { 
        return mapper.readValue(payload, Map.class); 
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readChildObject(Map<String, Object> parent, String key) { 
        return (Map<String, Object>) parent.get(key); 
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
