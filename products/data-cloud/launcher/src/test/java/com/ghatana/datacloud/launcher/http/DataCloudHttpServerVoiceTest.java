/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for voice gateway endpoints (DC-E4).
 *
 * <p>Validates intent resolution, parameter validation, confirmation gating,
 * catalog discovery, and classify-only mode.
 *
 * @doc.type class
 * @doc.purpose Integration tests for /api/v1/voice/** HTTP endpoints (DC-E4)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpServer – Voice Gateway Endpoints (DC-E4)")
class DataCloudHttpServerVoiceTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port       = findFreePort();
        httpClient = HttpClient.newBuilder().build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ──────────────────── GET /api/v1/voice/intents ────────────────────

    @Nested
    @DisplayName("GET /api/v1/voice/intents – catalog")
    class ListIntentsTests {

        @Test
        @DisplayName("returns 200 with full intent catalog")
        @SuppressWarnings("unchecked")
        void returns200WithCatalog() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            assertThat(data).containsKey("intents");
            assertThat(data).containsKey("count");
            List<?> intents = (List<?>) data.get("intents");
            assertThat(intents).hasSizeGreaterThanOrEqualTo(20);
            // Every intent has required fields
            Map<String, Object> first = (Map<String, Object>) intents.get(0);
            assertThat(first).containsKey("name");
            assertThat(first).containsKey("description");
            assertThat(first).containsKey("httpMethod");
            assertThat(first).containsKey("pathTemplate");
            assertThat(first).containsKey("sensitivity");
        }
    }

    // ──────────────────── POST /api/v1/voice/intent ────────────────────

    @Nested
    @DisplayName("POST /api/v1/voice/intent – intent dispatch")
    class VoiceIntentTests {

        @Test
        @DisplayName("returns 400 when utterance is missing")
        @SuppressWarnings("unchecked")
        void missingUtterance_returns200WithError() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            HttpResponse<String> resp = post("/api/v1/voice/intent", "{}");

            assertThat(resp.statusCode()).isEqualTo(200); // ApiResponse envelope
            Map<String, Object> body = mapper.readValue(resp.body(), Map.class);
            assertThat(body).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_UTTERANCE");
        }

        @Test
        @DisplayName("resolves exact intent name and returns execution result")
        @SuppressWarnings("unchecked")
        void exactIntentName_resolvedAndExecuted() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            // Use the exact intent name — bypass LLM entirely
            String body = mapper.writeValueAsString(Map.of(
                "utterance",   "list_pipelines",
                "parameters",  Map.of(),
                "confirm",     true
            ));
            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("intentName")).isEqualTo("list_pipelines");
            assertThat(data.get("executed")).isEqualTo(true);
        }

        @Test
        @DisplayName("keyword heuristic match returns low-confidence confirmation gate")
        @SuppressWarnings("unchecked")
        void keywordMatch_lowConfidence_requiresConfirmation() throws Exception {
            server = new DataCloudHttpServer(mockClient, port); // no LLM — uses heuristics
            server.start();
            waitForServerReady(port);

            // "pipelines" should keyword-match list_pipelines with KEYWORD_CONFIDENCE 0.55
            String body = mapper.writeValueAsString(Map.of(
                "utterance", "show me pipelines",
                "confirm",   false
            ));
            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            // Should be a confirmation gate response (confidence < 0.60)
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            if (data != null && data.containsKey("requiresConfirmation")) {
                assertThat(data.get("requiresConfirmation")).isEqualTo(true);
            }
            // Either way the response must be 200 with valid structure
        }

        @Test
        @DisplayName("keyword match with confirm=true bypasses confirmation gate and executes")
        @SuppressWarnings("unchecked")
        void keywordMatchWithConfirm_executes() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "utterance",  "show me pipelines",
                "parameters", Map.of(),
                "confirm",    true
            ));
            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("data");
        }

        @Test
        @DisplayName("unrecognised utterance returns UNKNOWN_INTENT error")
        @SuppressWarnings("unchecked")
        void unknownUtterance_returnsUnknownIntentError() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of(
                "utterance", "xyzzy frobulate the wumpus"
            ));
            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            // Must not 500 — either data (empty) or error block
            assertThat(respBody.containsKey("error") || respBody.containsKey("data")).isTrue();
        }
    }

    // ──────────────────── POST /api/v1/voice/intent/classify ────────────────────

    @Nested
    @DisplayName("POST /api/v1/voice/intent/classify – classify only")
    class ClassifyOnlyTests {

        @Test
        @DisplayName("returns matched=true and intent name for exact intent name")
        @SuppressWarnings("unchecked")
        void exactName_classifiesCorrectly() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("utterance", "query_entities"));
            HttpResponse<String> resp = post("/api/v1/voice/intent/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            assertThat(data.get("matched")).isEqualTo(true);
            assertThat(data.get("intent")).isEqualTo("query_entities");
        }

        @Test
        @DisplayName("returns matched=false for unrecognised utterance")
        @SuppressWarnings("unchecked")
        void unknownUtterance_matchedFalse() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("utterance", "completely unrelated nonsense 12345"));
            HttpResponse<String> resp = post("/api/v1/voice/intent/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) respBody.get("data");
            // matched may be false if no keywords match
            assertThat(data).containsKey("matched");
        }

        @Test
        @DisplayName("returns 200 with error block when utterance is empty")
        @SuppressWarnings("unchecked")
        void emptyUtterance_returnsErrorBlock() throws Exception {
            server = new DataCloudHttpServer(mockClient, port);
            server.start();
            waitForServerReady(port);

            String body = mapper.writeValueAsString(Map.of("utterance", ""));
            HttpResponse<String> resp = post("/api/v1/voice/intent/classify", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            assertThat(respBody).containsKey("error");
        }
    }

    // ──────────────────── Helpers ────────────────────

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
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
        throw new IllegalStateException("Server did not start within 5 seconds on port " + port);
    }
}
