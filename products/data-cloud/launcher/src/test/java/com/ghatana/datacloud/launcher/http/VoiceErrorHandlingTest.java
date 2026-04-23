/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.utils.NetworkTestUtils;
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
 * Data Cloud voice HTTP tests: STT low-confidence flow, audio format
 * errors, permission denials, intent resolution failures, and ambiguous utterance
 * handling.
 *
 * <p>All tests use a {@link DataCloudHttpServer} started on a random port without
 * a real {@link com.ghatana.ai.llm.CompletionService} so that the handler falls
 * back to its keyword-heuristic path. This makes the assertions deterministic and
 * infrastructure-free.
 *
 * @doc.type class
 * @doc.purpose Voice gateway error-handling tests (DC-E4, Gap 003) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@Timeout(value = 15, unit = TimeUnit.SECONDS) // GH-90000
@DisplayName("Voice Gateway – Error Handling")
class VoiceErrorHandlingTest {

    private DataCloudClient mockClient;
    private DataCloudHttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newBuilder().build(); // GH-90000
    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        mockClient = mock(DataCloudClient.class); // GH-90000
        port       = NetworkTestUtils.findFreePort(); // GH-90000
        server     = new DataCloudHttpServer(mockClient, port); // GH-90000
        server.start(); // GH-90000
        waitForServerReady(port); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (server != null) server.stop(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utterance validation errors
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Utterance validation")
    class UtteranceValidationTests {

        @ParameterizedTest(name = "body=''{0}''") // GH-90000
        @ValueSource(strings = {"{}", "{\"utterance\":\"\"}", "{\"utterance\":\"   \"}"}) // GH-90000
        @DisplayName("missing or blank utterance → error code MISSING_UTTERANCE with HTTP 400")
        @SuppressWarnings("unchecked")
        void blankOrMissingUtterance_returnsMissingUtteranceError(String body) throws Exception { // GH-90000
            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // Client error - bad request // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("MISSING_UTTERANCE");
        }

        @Test
        @DisplayName("null body → 400 bad request")
        void nullBody_returns400() throws Exception { // GH-90000
            HttpResponse<String> resp = postRaw("/api/v1/voice/intent", ""); // GH-90000
            // Server should return 4xx for completely empty body
            assertThat(resp.statusCode()).isGreaterThanOrEqualTo(400); // GH-90000
        }

        @Test
        @DisplayName("extremely long utterance (>4096 chars) → rejected before LLM call")
        @SuppressWarnings("unchecked")
        void tooLongUtterance_returnsError() throws Exception { // GH-90000
            String longUtterance = "a".repeat(4097); // GH-90000
            String body = mapper.writeValueAsString(Map.of("utterance", longUtterance)); // GH-90000

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_UTTERANCE");
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
        void noMatchingIntent_returnsIntentNotFoundError() throws Exception { // GH-90000
            // This phrase has zero keyword overlap with any registered intent
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "utterance", "play some jazz music and dim the lights"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // Not found // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("UNKNOWN_INTENT");
        }

        @Test
        @DisplayName("unknown exact intent name → response indicates unresolved with HTTP 404")
        @SuppressWarnings("unchecked")
        void unknownExactIntentName_notExecuted() throws Exception { // GH-90000
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "utterance",  "completely_nonexistent_intent_xyz",
                "parameters", Map.of(), // GH-90000
                "confirm",    true
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(404); // Not found // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("UNKNOWN_INTENT");
        }

        @Test
        @DisplayName("ambiguous utterance matching multiple intents → confidence below 0.65 or confirmation required")
        @SuppressWarnings("unchecked")
        void ambiguousUtterance_lowConfidenceOrConfirmationGate() throws Exception { // GH-90000
            // "list" matches many intents (list_pipelines, list_entities, list_models, etc.) // GH-90000
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "utterance", "list"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            // Ambiguous match may return 200 with confirmationRequired or 404 if no match
            assertThat(resp.statusCode()).isIn(200, 404); // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            if (resp.statusCode() == 200 && resBody.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resBody.get("data");
                if (data != null && data.containsKey("confidence")) {
                    // Ambiguous match → should be reported as low confidence or require confirmation
                    double confidence = ((Number) data.get("confidence")).doubleValue();
                    boolean confirmRequired = Boolean.TRUE.equals(data.get("confirmationRequired"));
                    assertThat(confidence < 0.65 || confirmRequired).isTrue(); // GH-90000
                }
            } else if (resp.statusCode() == 404) { // GH-90000
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
        @DisplayName("audioData with invalid base64 → HTTP 400 with INVALID_AUDIO_DATA")
        @SuppressWarnings("unchecked")
        void invalidBase64AudioData_returnsError() throws Exception { // GH-90000
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "audioData",   "!!! not valid base64 !!!",
                "audioFormat", "audio/wav"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            assertThat(resBody).containsKey("error");
            Map<String, Object> error = (Map<String, Object>) resBody.get("error");
            assertThat(error.get("code")).isEqualTo("INVALID_AUDIO_DATA");
        }

        @Test
        @DisplayName("audioData present but no STT provider → graceful STT-unavailable response with HTTP 400")
        @SuppressWarnings("unchecked")
        void audioDataWithoutSttProvider_returnsGracefulError() throws Exception { // GH-90000
            // Encode minimal dummy PCM bytes as base64
            byte[] dummyAudio = new byte[]{0x52, 0x49, 0x46, 0x46};  // "RIFF" header
            String audioBase64 = Base64.getEncoder().encodeToString(dummyAudio); // GH-90000

            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "audioData",   audioBase64,
                "audioFormat", "audio/wav"
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            // Without STT provider, handler returns 400 with EMPTY_UTTERANCE error
            assertThat(resp.statusCode()).isEqualTo(400); // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
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
        void classifyOnly_intentNotExecuted() throws Exception { // GH-90000
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "utterance",    "list_pipelines",
                "classifyOnly", true
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
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
        void classifyOnly_unknownIntent_noExecutionError() throws Exception { // GH-90000
            String body = mapper.writeValueAsString(Map.of( // GH-90000
                "utterance",    "do something completely unknown",
                "classifyOnly", true
            ));

            HttpResponse<String> resp = post("/api/v1/voice/intent", body); // GH-90000

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> resBody = parseBody(resp); // GH-90000
            assertThat(resBody).containsKey("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resBody.get("data");
            assertThat(data.get("matched")).isEqualTo(false);
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
        void catalogIntents_haveSensitivityField() throws Exception { // GH-90000
            HttpResponse<String> resp = get("/api/v1/voice/intents");

            assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            Map<String, Object> body = parseBody(resp); // GH-90000
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            java.util.List<?> intents = (java.util.List<?>) data.get("intents");

            for (Object intentObj : intents) { // GH-90000
                Map<String, Object> intent = (Map<String, Object>) intentObj; // GH-90000
                String sensitivity = (String) intent.get("sensitivity");
                assertThat(sensitivity).isNotBlank(); // GH-90000
                // Sensitivity uses EndpointSensitivity enum values: PUBLIC, INTERNAL, SENSITIVE, CRITICAL
                assertThat(sensitivity).isIn("PUBLIC", "INTERNAL", "SENSITIVE", "CRITICAL"); // GH-90000
            }
        }

        @Test
        @DisplayName("GET /api/v1/voice/intents returns HTTP 200 for successful catalog retrieval")
        void intentsEndpoint_returns200ForSuccess() throws Exception { // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                HttpResponse<String> resp = get("/api/v1/voice/intents");
                assertThat(resp.statusCode()).isEqualTo(200); // GH-90000
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(HttpResponse<String> resp) throws Exception { // GH-90000
        return mapper.readValue(resp.body(), Map.class); // GH-90000
    }

    private HttpResponse<String> get(String path) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder().GET() // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception { // GH-90000
        return postRaw(path, jsonBody); // GH-90000
    }

    private HttpResponse<String> postRaw(String path, String body) throws Exception { // GH-90000
        return httpClient.send( // GH-90000
            HttpRequest.newBuilder() // GH-90000
                .POST(HttpRequest.BodyPublishers.ofString(body)) // GH-90000
                .uri(URI.create("http://127.0.0.1:" + port + path)) // GH-90000
                .header("Content-Type", "application/json") // GH-90000
                .build(), // GH-90000
            HttpResponse.BodyHandlers.ofString()); // GH-90000
    }

    private static void waitForServerReady(int port) throws Exception { // GH-90000
        NetworkTestUtils.waitForTcpPortOpen("127.0.0.1", port, 5_000);
    }
}
