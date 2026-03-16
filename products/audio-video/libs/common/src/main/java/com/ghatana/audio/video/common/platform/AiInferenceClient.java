package com.ghatana.audio.video.common.platform;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @doc.type class
 * @doc.purpose Optional HTTP client for the Ghatana AI Inference Service.
 *              Provides a last-resort LLM completion and embedding fallback for
 *              audio-video services (STT, TTS, Vision) when their native
 *              adapter libraries (Whisper.cpp, Coqui TTS, YOLO v8) are not
 *              available on the host.
 * @doc.layer product
 * @doc.pattern Service
 *
 * <p>Endpoints consumed (set {@code AI_INFERENCE_URL} env var):
 * <ul>
 *   <li>{@code POST /ai/infer/completion}  — LLM text completion</li>
 *   <li>{@code POST /ai/infer/embedding}   — single text embedding</li>
 *   <li>{@code POST /ai/infer/embeddings}  — batch text embeddings</li>
 *   <li>{@code GET  /health}               — liveness check</li>
 * </ul>
 *
 * <p>When {@code AI_INFERENCE_URL} is absent or blank every method returns
 * {@link Optional#empty()} without throwing, preserving fail-soft behaviour.
 */
public final class AiInferenceClient {

    private static final Logger LOG = Logger.getLogger(AiInferenceClient.class.getName());
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static volatile AiInferenceClient instance;

    private final String baseUrl;
    private final HttpClient http;

    private AiInferenceClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Return the singleton instance, configured from the {@code AI_INFERENCE_URL}
     * environment variable.
     *
     * @return AiInferenceClient singleton
     */
    public static AiInferenceClient getInstance() {
        if (instance == null) {
            synchronized (AiInferenceClient.class) {
                if (instance == null) {
                    String url = System.getenv("AI_INFERENCE_URL");
                    if (url == null || url.isBlank()) {
                        LOG.warning("[AiInferenceClient] AI_INFERENCE_URL not set — LLM inference fallback disabled");
                        url = "";
                    }
                    instance = new AiInferenceClient(url);
                }
            }
        }
        return instance;
    }

    /**
     * Check whether the AI Inference Service is reachable.
     *
     * @return {@code true} if {@code GET /health} returns HTTP 200.
     */
    public boolean isReachable() {
        if (baseUrl.isBlank()) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.log(Level.FINE, "[AiInferenceClient] Health check failed", e);
            return false;
        }
    }

    /**
     * Request a text completion from the AI Inference Service.
     *
     * <p>Constructs a minimal JSON body compatible with the inference service's
     * {@code POST /ai/infer/completion} endpoint.
     *
     * @param prompt     The prompt / instruction string.
     * @param model      Model identifier (e.g. {@code "gpt-4o"}, {@code "llama3"}).
     *                   Pass {@code null} to use the server-side default.
     * @param maxTokens  Maximum number of tokens to generate (≤ 0 uses server default).
     * @return The raw JSON response body, or {@link Optional#empty()} on error or
     *         when the service is not configured.
     */
    public Optional<String> complete(String prompt, String model, int maxTokens) {
        if (baseUrl.isBlank() || prompt == null || prompt.isBlank()) return Optional.empty();

        String modelField = (model != null && !model.isBlank())
                ? "\"model\":\"" + escapeJson(model) + "\","
                : "";
        String maxTokensField = maxTokens > 0 ? "\"max_tokens\":" + maxTokens + "," : "";

        String body = "{"
                + modelField
                + maxTokensField
                + "\"prompt\":\"" + escapeJson(prompt) + "\""
                + "}";

        return postJson("/ai/infer/completion", body);
    }

    /**
     * Request an embedding vector for a single text input.
     *
     * @param text  Text to embed.
     * @param model Model identifier. Pass {@code null} for server default.
     * @return Raw JSON response body containing the vector, or
     *         {@link Optional#empty()} on error.
     */
    public Optional<String> embedding(String text, String model) {
        if (baseUrl.isBlank() || text == null || text.isBlank()) return Optional.empty();

        String modelField = (model != null && !model.isBlank())
                ? "\"model\":\"" + escapeJson(model) + "\","
                : "";

        String body = "{"
                + modelField
                + "\"input\":\"" + escapeJson(text) + "\""
                + "}";

        return postJson("/ai/infer/embedding", body);
    }

    /**
     * Request embedding vectors for multiple text inputs in a single batch call.
     *
     * @param texts      List of text inputs.
     * @param model      Model identifier. Pass {@code null} for server default.
     * @return Raw JSON response body containing the vectors, or
     *         {@link Optional#empty()} on error.
     */
    public Optional<String> embeddings(java.util.List<String> texts, String model) {
        if (baseUrl.isBlank() || texts == null || texts.isEmpty()) return Optional.empty();

        String modelField = (model != null && !model.isBlank())
                ? "\"model\":\"" + escapeJson(model) + "\","
                : "";

        StringBuilder inputArray = new StringBuilder("[");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) inputArray.append(',');
            inputArray.append('"').append(escapeJson(texts.get(i))).append('"');
        }
        inputArray.append(']');

        String body = "{"
                + modelField
                + "\"input\":" + inputArray
                + "}";

        return postJson("/ai/infer/embeddings", body);
    }

    /**
     * Request text-to-speech synthesis from the AI Inference Service.
     *
     * <p>Calls {@code POST /ai/infer/tts}. The response JSON contains a
     * Base64-encoded WAV audio payload, the sample rate used, and the
     * estimated duration in milliseconds:
     * <pre>{@code
     * { "audio_b64": "<base64>", "sample_rate": 22050, "duration_ms": 1234 }
     * }</pre>
     *
     * @param text       The plain-text input to synthesize (must not be blank).
     * @param voice      Voice identifier forwarded to the inference service
     *                   (e.g. {@code "piper-en-us-amy-low"}).
     *                   Pass {@code null} to use the server-side default.
     * @param sampleRate Target PCM sample rate in Hz (e.g. {@code 22050}).
     *                   Pass {@code 0} to use the server-side default.
     * @return Raw JSON response body, or {@link Optional#empty()} on error or
     *         when the service is not configured.
     */
    public Optional<String> tts(String text, String voice, int sampleRate) {
        if (baseUrl.isBlank() || text == null || text.isBlank()) return Optional.empty();

        String voiceField = (voice != null && !voice.isBlank())
                ? "\"voice\":\"" + escapeJson(voice) + "\","
                : "";
        String sampleRateField = sampleRate > 0 ? "\"sample_rate\":" + sampleRate + "," : "";

        String body = "{"
                + voiceField
                + sampleRateField
                + "\"text\":\"" + escapeJson(text) + "\""
                + "}";

        return postJson("/ai/infer/tts", body);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Optional<String> postJson(String path, String jsonBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return Optional.ofNullable(resp.body());
            }

            LOG.warning("[AiInferenceClient] POST " + path + " returned HTTP "
                    + resp.statusCode() + ": " + resp.body());
            return Optional.empty();

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "[AiInferenceClient] POST " + path + " failed", e);
            return Optional.empty();
        }
    }

    /**
     * Minimal JSON string escaping to prevent injection.
     * Handles the characters that must be escaped per RFC 8259.
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
