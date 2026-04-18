package com.ghatana.datacloud.launcher.http.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * HTTP adapter for OpenAI-compatible text-to-speech providers.
 *
 * <p>Compatible with providers that expose a JSON POST endpoint at
 * {@code /v1/audio/speech} and return raw audio bytes in the response body.
 *
 * @doc.type class
 * @doc.purpose HTTP adapter for optional server-side TTS synthesis
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpSpeechTtsAdapter implements VoiceTtsPort {

    private static final Logger log = LoggerFactory.getLogger(HttpSpeechTtsAdapter.class);

    private final VoiceTtsConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Executor blockingExecutor;

    public HttpSpeechTtsAdapter(VoiceTtsConfig config, ObjectMapper objectMapper, Executor blockingExecutor) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.blockingExecutor = blockingExecutor;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(blockingExecutor)
            .build();
    }

    @Override
    public boolean isAvailable() {
        return config.enabled();
    }

    @Override
    public Promise<byte[]> synthesize(String text, String languageHint) {
        if (!config.enabled()) {
            return Promise.of(new byte[0]);
        }
        if (text == null || text.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("text must not be blank"));
        }

        return Promise.ofBlocking(blockingExecutor, () -> doSynthesize(text.strip(), languageHint));
    }

    private byte[] doSynthesize(String text, String languageHint) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model());
        payload.put("voice", config.voice());
        payload.put("input", text);
        payload.put("response_format", config.responseFormat());
        if (languageHint != null && !languageHint.isBlank()) {
            payload.put("language", sanitiseLanguage(languageHint));
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.endpointUrl().stripTrailing() + "/v1/audio/speech"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));

        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.apiKey());
        }

        HttpResponse<byte[]> response = httpClient.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[DC-E4] TTS endpoint returned status {}", response.statusCode());
            throw new RuntimeException("TTS endpoint error: HTTP " + response.statusCode());
        }

        return response.body() != null ? response.body() : new byte[0];
    }

    private static String sanitiseLanguage(String language) {
        return language.replaceAll("[^a-zA-Z0-9\\-]", "").substring(0, Math.min(language.length(), 10));
    }
}