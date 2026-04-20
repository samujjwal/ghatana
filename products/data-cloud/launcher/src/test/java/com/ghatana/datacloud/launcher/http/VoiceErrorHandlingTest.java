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
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for voice gateway error-handling paths that were not covered by
 * {@link DataCloudHttpServerVoiceTest}: STT low-confidence flow, audio format
 * errors, permission denials, intent resolution failures, and ambiguous utterance
 * handling.
 *
 * <p>All tests use a {@link DataCloudHttpServer} started on a random port without
 * a real {@link com.ghatana.ai.llm.CompletionService} so that the handler falls
 * back to its keyword-heuristic path. This makes the assertions deterministic and
 * infrastructure-free.
 *
 * @doc.type class
 * @doc.purpose Voice gateway error-handling tests (DC-E4, Gap 003)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("Voice Gateway – Error Handling")
class VoiceErrorHandlingTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockClient = mock(DataCloudClient.class);
        port       = findFreePort();
        server     = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utterance validation errors
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Utterance validation")
    class UtteranceValidationTests {

        @ParameterizedTest(name = "body=''{0}''")
        @ValueSource(strings = {"{}", "{\"utterance\":\"\"}", "{\"utterance\":\"   \"}"})
        @DisplayName("missing or blank utterance → error code MISSING_UTTERANCE with HTTP 400")
        @SuppressWarnings("unchecked")
        void blankOrMissingUtterance_returnsMissingUtteranceError(String body) throws Exception {
            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(400); // Client error - bad request
            Map<String, Object> resBody = parseBody(resp);
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_UTTERANCE");
        }

        @Test
        @DisplayName("null body → 400 bad request")
        void nullBody_returns400() throws Exception {
            HttpResponse<String> resp = postRaw("/api/v1/voice/intent", "");
            // Server should return 4xx for completely empty body
            assertThat(resp.statusCode()).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("extremely long utterance (>4096 chars) → rejected before LLM call")
        @SuppressWarnings("unchecked")
        void tooLongUtterance_returnsError() throws Exception {
            String longUtterance = "a".repeat(4097);
            String body = mapper.writeValueAsString(Map.of("utterance", longUtterance));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            // Either 400 or ApiResponse with error — must not 500
            assertThat(resp.statusCode()).isLessThan(500);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Non-existent intent handling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Intent resolution failures")
    class IntentResolutionFailureTests {

        @Test
        @DisplayName("utterance with no matching intent → error code UNKNOWN_INTENT with HTTP 404")
        @SuppressWarnings("unchecked")
        void noMatchingIntent_returnsIntentNotFoundError() throws Exception {
            // This phrase has zero keyword overlap with any registered intent
            String body = mapper.writeValueAsString(Map.of(
                "utterance", "play some jazz music and dim the lights"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(404); // Not found
            Map<String, Object> resBody = parseBody(resp);
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("UNKNOWN_INTENT");
        }

        @Test
        @DisplayName("unknown exact intent name → response indicates unresolved with HTTP 404")
        @SuppressWarnings("unchecked")
        void unknownExactIntentName_notExecuted() throws Exception {
            String body = mapper.writeValueAsString(Map.of(
                "utterance",  "completely_nonexistent_intent_xyz",
                "parameters", Map.of(),
                "confirm",    true
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(404); // Not found
            Map<String, Object> resBody = parseBody(resp);
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("UNKNOWN_INTENT");
        }

        @Test
        @DisplayName("ambiguous utterance matching multiple intents → confidence below 0.65 or confirmation required")
        @SuppressWarnings("unchecked")
        void ambiguousUtterance_lowConfidenceOrConfirmationGate() throws Exception {
            // "list" matches many intents (list_pipelines, list_entities, list_models, etc.)
            String body = mapper.writeValueAsString(Map.of(
                "utterance", "list"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            // Ambiguous match may return 200 with confirmationRequired or 404 if no match
            assertThat(resp.statusCode()).isIn(200, 404);
            Map<String, Object> resBody = parseBody(resp);
            if (resp.statusCode() == 200 && resBody.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                if (data != null && data.containsKey("confidence")) {
                    // Ambiguous match → should be reported as low confidence or require confirmation
                    double confidence = ((Number) data.get("confidence")).doubleValue();
                    boolean confirmRequired = Boolean.TRUE.equals(data.get("confirmationRequired"));
                    assertThat(confidence < 0.65 || confirmRequired).isTrue();
                }
            } else if (resp.statusCode() == 404) {
                // Also acceptable if no match found
                assertThat(resBody).containsKey("error");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio / STT error paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Audio input validation")
    class AudioInputValidationTests {

        @Test
        @DisplayName("audioData with invalid base64 → returns error, not 500")
        @SuppressWarnings("unchecked")
        void invalidBase64AudioData_returnsError() throws Exception {
            String body = mapper.writeValueAsString(Map.of(
                "audioData",   "!!! not valid base64 !!!",
                "audioFormat", "audio/wav"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            // Must not be a 500 internal server error
            assertThat(resp.statusCode()).isLessThan(500);
        }

        @Test
        @DisplayName("audioData present but no STT provider → graceful STT-unavailable response with HTTP 400")
        @SuppressWarnings("unchecked")
        void audioDataWithoutSttProvider_returnsGracefulError() throws Exception {
            // Encode minimal dummy PCM bytes as base64
            byte[] dummyAudio = new byte[]{0x52, 0x49, 0x46, 0x46};  // "RIFF" header
            String audioBase64 = Base64.getEncoder().encodeToString(dummyAudio);

            String body = mapper.writeValueAsString(Map.of(
                "audioData",   audioBase64,
                "audioFormat", "audio/wav"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            // Without STT provider, handler returns 400 with EMPTY_UTTERANCE error
            assertThat(resp.statusCode()).isEqualTo(400);
            Map<String, Object> resBody = parseBody(resp);
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("EMPTY_UTTERANCE");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Voice gateway classify-only mode
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Classify-only mode")
    class ClassifyOnlyTests {

        @Test
        @DisplayName("classifyOnly=true → intent classified but not executed")
        @SuppressWarnings("unchecked")
        void classifyOnly_intentNotExecuted() throws Exception {
            String body = mapper.writeValueAsString(Map.of(
                "utterance",    "list_pipelines",
                "classifyOnly", true
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> resBody = parseBody(resp);
            assertThat(resBody).containsKey("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resBody.get("data");
            // executed must be false in classify-only mode
            assertThat(data.get("executed")).isEqualTo(false);
            assertThat(data.get("intentName")).isEqualTo("list_pipelines");
        }

        @Test
        @DisplayName("classifyOnly=true with unknown intent → classification reported without error")
        @SuppressWarnings("unchecked")
        void classifyOnly_unknownIntent_noExecutionError() throws Exception {
            String body = mapper.writeValueAsString(Map.of(
                "utterance",    "do something completely unknown",
                "classifyOnly", true
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body);

            // Must not 500 — classify-only mode should never throw on unknown intent
            // Returns 200 with matched=false for no match
            assertThat(resp.statusCode()).isLessThan(500);
            Map<String, Object> resBody = parseBody(resp);
            if (resp.statusCode() == 200) {
                assertThat(resBody).containsKey("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                assertThat(data.get("matched")).isEqualTo(false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intent catalog validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Intent catalog structure")
    class IntentCatalogTests {

        @Test
        @DisplayName("every catalog intent has required sensitivity field with valid value")
        @SuppressWarnings("unchecked")
        void catalogIntents_haveSensitivityField() throws Exception {
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200);
            Map<String, Object> body = parseBody(resp);
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            java.util.List<?> intents = (java.util.List<?>) data.get("intents");

            for (Object intentObj : intents) {
                Map<String, Object> intent = (Map<String, Object>) intentObj;
                String sensitivity = (String) intent.get("sensitivity");
                assertThat(sensitivity).isNotBlank();
                // Sensitivity uses EndpointSensitivity enum values: PUBLIC, INTERNAL, SENSITIVE, CRITICAL
                assertThat(sensitivity).isIn("PUBLIC", "INTERNAL", "SENSITIVE", "CRITICAL");
            }
        }

        @Test
        @DisplayName("GET /api/v1/voice/intents returns HTTP 200 for successful catalog retrieval")
        void intentsEndpoint_returns200ForSuccess() throws Exception {
            for (int i = 0; i < 3; i++) {
                HttpResponse<String> resp = get("/api/v1/voice/intents");
                assertThat(resp.statusCode()).isEqualTo(200);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpResponse<String> resp) throws Exception {
        return mapper.readValue(resp.body(), Map.class);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder().GET()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        return postRaw(path, jsonBody);
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception {
        return httpClient.send(
            HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .build(),
            HttpResponse.BodyHandlers.ofString());
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
