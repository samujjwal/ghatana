package com.ghatana.datacloud.launcher.http.voice;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * STT adapter that calls any OpenAI Whisper-compatible transcription endpoint.
 *
 * <p>Compatible with:
 * <ul>
 *   <li>OpenAI Whisper API ({@code https://api.openai.com})</li>
 *   <li>Self-hosted whisper.cpp HTTP server</li>
 *   <li>Any endpoint that accepts multipart/form-data with field {@code file}</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * Reads from the {@link WhisperSttConfig} record:
 * <ul>
 *   <li>{@code endpointUrl} — base URL, e.g. {@code https://api.openai.com} (required)</li>
 *   <li>{@code apiKey} — Bearer token for authentication (required for cloud APIs)</li>
 *   <li>{@code model} — Whisper model name, default {@code whisper-1}</li>
 *   <li>{@code maxAudioBytes} — hard limit on audio payload, default 25 MB (Whisper limit)</li>
 * </ul>
 *
 * <h2>Privacy</h2>
 * Audio bytes are transmitted only to the configured endpoint.  No audio is logged
 * or persisted by this adapter.  Callers should ensure retention contracts with
 * the chosen STT vendor satisfy their tenant privacy requirements.
 *
 * @doc.type class
 * @doc.purpose HTTP adapter for Whisper-compatible STT endpoints
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class HttpWhisperSttAdapter implements VoiceSttPort {

    private static final Logger log = LoggerFactory.getLogger(HttpWhisperSttAdapter.class);

    /** Default maximum audio payload enforced before sending. Whisper API limit is 25 MB. */
    public static final int DEFAULT_MAX_AUDIO_BYTES = 25 * 1024 * 1024;

    private final WhisperSttConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Executor blockingExecutor;

    public HttpWhisperSttAdapter(WhisperSttConfig config, ObjectMapper objectMapper, Executor blockingExecutor) {
        this.config           = config;
        this.objectMapper     = objectMapper;
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

    /**
     * Transcribes audio by POSTing multipart/form-data to the Whisper endpoint.
     *
     * <p>If the audio exceeds {@link WhisperSttConfig#maxAudioBytes()}, the request
     * is rejected before making a network call to prevent accidental large uploads.
     */
    @Override
    public Promise<SttTranscription> transcribe(byte[] audioData, String audioFormat, String languageHint) {
        if (!config.enabled()) {
            return Promise.of(SttTranscription.unavailable());
        }

        int limit = config.maxAudioBytes() > 0 ? config.maxAudioBytes() : DEFAULT_MAX_AUDIO_BYTES;
        if (audioData == null || audioData.length == 0) {
            return Promise.ofException(new IllegalArgumentException("audioData must not be empty"));
        }
        if (audioData.length > limit) {
            return Promise.ofException(new IllegalArgumentException(
                "audioData exceeds limit " + limit + " bytes (got " + audioData.length + ")"));
        }

        return Promise.ofBlocking(blockingExecutor, () -> doTranscribe(audioData, audioFormat, languageHint));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Implementation
    // ─────────────────────────────────────────────────────────────────────────

    private SttTranscription doTranscribe(byte[] audioData, String audioFormat, String languageHint)
            throws Exception {
        String boundary  = "----WaveformBoundary" + UUID.randomUUID().toString().replace("-", "");
        String extension = guessExtension(audioFormat);

        byte[] multipart = buildMultipart(boundary, audioData, extension, languageHint);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.endpointUrl().stripTrailing() + "/v1/audio/transcriptions"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipart));

        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + config.apiKey());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[DC-E4] STT endpoint returned status {}: {}", response.statusCode(),
                truncate(response.body(), 200));
            throw new RuntimeException("STT endpoint error: HTTP " + response.statusCode());
        }

        return parseWhisperResponse(response.body());
    }

    private byte[] buildMultipart(String boundary, byte[] audioData, String extension, String languageHint)
            throws Exception {
        String model = (config.model() != null && !config.model().isBlank()) ? config.model() : "whisper-1";

        StringBuilder sb = new StringBuilder();
        // model field
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        sb.append(model).append("\r\n");
        // response_format
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        sb.append("verbose_json").append("\r\n");
        // language hint if provided
        if (languageHint != null && !languageHint.isBlank()) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
            sb.append(sanitiseLanguage(languageHint)).append("\r\n");
        }
        // audio file field header
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.").append(extension).append("\"\r\n");
        sb.append("Content-Type: audio/").append(extension).append("\r\n\r\n");

        byte[] header  = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] trailer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[header.length + audioData.length + trailer.length];
        System.arraycopy(header,    0, result, 0, header.length);
        System.arraycopy(audioData, 0, result, header.length, audioData.length);
        System.arraycopy(trailer,   0, result, header.length + audioData.length, trailer.length);
        return result;
    }

    private SttTranscription parseWhisperResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String text       = root.path("text").asText().strip();
            // verbose_json includes segment-level confidence; fall back to 0.9 (Whisper is ~95% WER in English)
            double confidence = root.has("segments")
                ? parseSegmentConfidence(root.path("segments"))
                : 0.90;
            return SttTranscription.of(text, confidence, "whisper");
        } catch (Exception e) {
            log.debug("[DC-E4] Failed to parse Whisper response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse STT response: " + e.getMessage(), e);
        }
    }

    private static double parseSegmentConfidence(JsonNode segments) {
        if (!segments.isArray() || segments.isEmpty()) return 0.9;
        double sum = 0.0;
        int count  = 0;
        for (JsonNode seg : segments) {
            if (seg.has("no_speech_prob")) {
                sum += (1.0 - seg.path("no_speech_prob").asDouble(0.1));
                count++;
            }
        }
        return count > 0 ? Math.min(1.0, sum / count) : 0.9;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Maps a MIME type to a file extension safe for the Content-Disposition filename. */
    private static String guessExtension(String audioFormat) {
        if (audioFormat == null) return "wav";
        return switch (audioFormat.toLowerCase()) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/mp4", "audio/m4a"  -> "m4a";
            case "audio/ogg"               -> "ogg";
            case "audio/opus"              -> "opus";
            case "audio/flac"              -> "flac";
            case "audio/webm"              -> "webm";
            default                        -> "wav";
        };
    }

    /** Strips all non-alphanumeric, non-hyphen chars from a language tag to prevent header injection. */
    private static String sanitiseLanguage(String lang) {
        return lang.replaceAll("[^a-zA-Z0-9\\-]", "").substring(0, Math.min(lang.length(), 10));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
