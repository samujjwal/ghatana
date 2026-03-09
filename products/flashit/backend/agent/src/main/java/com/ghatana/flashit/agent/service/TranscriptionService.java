package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.TranscriptionRequest;
import com.ghatana.flashit.agent.dto.TranscriptionResponse;
import com.ghatana.flashit.agent.dto.TranscriptionSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Audio transcription service using OpenAI Whisper API.
 *
 * <p>Handles downloading audio from S3 URLs, submitting to Whisper,
 * and returning timestamped transcript segments.
 *
 * @doc.type class
 * @doc.purpose Transcribes audio/video content using OpenAI Whisper
 * @doc.layer product
 * @doc.pattern Service
 */
public class TranscriptionService {
    private static final Logger log = LoggerFactory.getLogger(TranscriptionService.class);

    private final String apiKey;
    private final String whisperModel;
    private final HttpClient httpClient;
    private final Map<String, TranscriptionResponse> jobStore = new ConcurrentHashMap<>();

    public TranscriptionService(AgentConfig config) {
        this.apiKey = config.getOpenAiApiKey();
        this.whisperModel = config.getWhisperModel();
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Transcribe audio from a URL or base64 data.
     *
     * @param request transcription request with audio reference
     * @return transcription response with transcript and segments
     */
    public TranscriptionResponse transcribe(TranscriptionRequest request) {
        long start = System.currentTimeMillis();
        String jobId = UUID.randomUUID().toString();
        log.info("Starting transcription jobId={}, momentId={}, user={}",
                jobId, request.momentId(), request.userId());

        try {
            byte[] audioBytes;
            if (request.audioData() != null && !request.audioData().isBlank()) {
                audioBytes = java.util.Base64.getDecoder().decode(request.audioData());
            } else if (request.audioUrl() != null && !request.audioUrl().isBlank()) {
                audioBytes = downloadAudio(request.audioUrl());
            } else {
                long elapsed = System.currentTimeMillis() - start;
                var errorResponse = new TranscriptionResponse(
                        request.momentId(), jobId, "error", null, null, null,
                        List.of(), elapsed, whisperModel);
                jobStore.put(jobId, errorResponse);
                return errorResponse;
            }

            // Build multipart form data for OpenAI Whisper API
            String boundary = "---FlashItBoundary" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, audioBytes,
                    request.language() != null ? request.language() : "en");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            long elapsed = System.currentTimeMillis() - start;

            if (httpResponse.statusCode() != 200) {
                log.error("Whisper API error: {} - {}", httpResponse.statusCode(), httpResponse.body());
                var errorResponse = new TranscriptionResponse(
                        request.momentId(), jobId, "error", null, null, null,
                        List.of(), elapsed, whisperModel);
                jobStore.put(jobId, errorResponse);
                return errorResponse;
            }

            // Parse response
            var mapper = com.ghatana.flashit.agent.config.JsonConfig.objectMapper();
            var tree = mapper.readTree(httpResponse.body());
            String transcript = tree.path("text").asText("");

            // Parse segments if available (verbose_json format)
            List<TranscriptionSegment> segments = new java.util.ArrayList<>();
            if (tree.has("segments") && tree.get("segments").isArray()) {
                for (var seg : tree.get("segments")) {
                    segments.add(new TranscriptionSegment(
                            seg.path("start").asDouble(0),
                            seg.path("end").asDouble(0),
                            seg.path("text").asText(""),
                            seg.path("avg_logprob").asDouble(0) > -0.5 ? 0.9 : 0.7
                    ));
                }
            }

            String detectedLanguage = tree.has("language") ? tree.path("language").asText() : null;

            log.info("Transcription completed in {}ms, jobId={}, length={}",
                    elapsed, jobId, transcript.length());

            var result = new TranscriptionResponse(
                    request.momentId(), jobId, "completed", transcript,
                    detectedLanguage, 0.9, segments, elapsed, whisperModel);

            jobStore.put(jobId, result);
            return result;

        } catch (Exception e) {
            log.error("Transcription failed jobId={}", jobId, e);
            long elapsed = System.currentTimeMillis() - start;
            var errorResponse = new TranscriptionResponse(
                    request.momentId(), jobId, "error", null, null, null,
                    List.of(), elapsed, whisperModel);
            jobStore.put(jobId, errorResponse);
            return errorResponse;
        }
    }

    /**
     * Get transcription job status.
     *
     * @param jobId the job identifier
     * @return transcription response or null if not found
     */
    public TranscriptionResponse getStatus(String jobId) {
        return jobStore.get(jobId);
    }

    private byte[] downloadAudio(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download audio: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private byte[] buildMultipartBody(String boundary, byte[] audioBytes, String language)
            throws IOException {
        var baos = new ByteArrayOutputStream();
        var writer = new PrintWriter(new OutputStreamWriter(baos, java.nio.charset.StandardCharsets.UTF_8), true);

        // Model field
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        writer.append(whisperModel).append("\r\n");

        // Language field
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
        writer.append(language).append("\r\n");

        // Response format
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        writer.append("verbose_json").append("\r\n");

        // Audio file
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.mp3\"\r\n");
        writer.append("Content-Type: audio/mpeg\r\n\r\n");
        writer.flush();

        baos.write(audioBytes);

        writer.append("\r\n");
        writer.append("--").append(boundary).append("--\r\n");
        writer.flush();

        return baos.toByteArray();
    }
}
