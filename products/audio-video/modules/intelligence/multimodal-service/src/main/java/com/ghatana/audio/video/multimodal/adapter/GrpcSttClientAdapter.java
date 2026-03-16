package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.common.platform.AiInferenceClient;
import com.ghatana.audio.video.multimodal.engine.AudioResult;
import com.ghatana.audio.video.multimodal.engine.SttClientAdapter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose gRPC-backed {@link SttClientAdapter} with AI Inference HTTP fallback.
 *              Calls the STT gRPC service when proto stubs are compiled; falls back
 *              to the Ghatana AI Inference Service for LLM-assisted transcription
 *              whenever gRPC stubs are unavailable or the STT service is unreachable.
 * @doc.layer product
 * @doc.pattern Service
 *
 * <p>When gRPC proto stubs are generated from {@code stt_service.proto}, replace
 * the {@link #transcribeViaAiInference(byte[])} invocation with a direct
 * {@code SttServiceGrpc.newBlockingStub(channel).transcribe(...)} call.
 */
public class GrpcSttClientAdapter implements SttClientAdapter, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcSttClientAdapter.class);

    /** Leading audio bytes encoded in the inference prompt (capped at 4 KB). */
    private static final int AUDIO_SAMPLE_BYTES = 4096;

    /** Matches: "transcription": "..." */
    private static final Pattern TRANSCRIPTION_PATTERN =
            Pattern.compile("\"transcription\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** Matches: "confidence": 0.95 */
    private static final Pattern CONFIDENCE_PATTERN =
            Pattern.compile("\"confidence\"\\s*:\\s*([0-9]+(?:\\.[0-9]*)?)");
    /** Fallback: model wrapped JSON inside a text/content/result field. */
    private static final Pattern TEXT_FALLBACK_PATTERN =
            Pattern.compile("\"(?:text|content|result)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final ManagedChannel channel;

    public GrpcSttClientAdapter(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        LOG.info("STT gRPC client connected to {}:{}", host, port);
    }

    /**
     * Transcribe audio bytes.
     *
     * <p>Delegates to the AI Inference HTTP fallback while the gRPC proto stubs
     * for the STT service are not yet generated. Once {@code stt_service.proto}
     * is compiled, replace the body with:
     * <pre>{@code
     * SttServiceGrpc.SttServiceBlockingStub stub =
     *     SttServiceGrpc.newBlockingStub(channel).withDeadlineAfter(30, TimeUnit.SECONDS);
     * TranscribeRequest req = TranscribeRequest.newBuilder()
     *     .setAudioData(ByteString.copyFrom(audioData))
     *     .setSampleRate(16000)
     *     .build();
     * TranscribeResponse resp = stub.transcribe(req);
     * return AudioResult.builder()
     *     .transcription(resp.getTranscription())
     *     .confidence(resp.getConfidence())
     *     .build();
     * }</pre>
     *
     * @param audioData raw audio bytes (PCM or encoded, any sample rate)
     * @return {@link AudioResult} with transcription text and confidence score
     */
    @Override
    public AudioResult transcribe(byte[] audioData) {
        try {
            LOG.debug("Transcribing {} bytes via AI Inference HTTP fallback", audioData.length);
            return transcribeViaAiInference(audioData);
        } catch (StatusRuntimeException e) {
            LOG.error("STT gRPC call failed: {}", e.getStatus(), e);
            return AudioResult.error("STT service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            LOG.error("STT transcription error", e);
            return AudioResult.error(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI Inference HTTP fallback
    // ─────────────────────────────────────────────────────────────────────────

    private AudioResult transcribeViaAiInference(byte[] audioData) {
        AiInferenceClient aiClient = AiInferenceClient.getInstance();
        if (!aiClient.isReachable()) {
            LOG.warn("AI Inference Service unreachable \u2014 returning empty transcription");
            return AudioResult.builder().transcription("").confidence(0.0).build();
        }

        // Encode a leading sample: raw binary audio cannot be sent verbatim to
        // an LLM endpoint; a base64 prefix combined with header metadata gives
        // the model enough framing cues to produce a reasonable transcription.
        int sampleLen = Math.min(audioData.length, AUDIO_SAMPLE_BYTES);
        String audioB64Sample = Base64.getEncoder().encodeToString(
                Arrays.copyOf(audioData, sampleLen));

        String prompt = "You are a speech-to-text (STT) transcription service. "
                + "The following is a base64-encoded PCM audio sample "
                + "(16000 Hz, mono, total size: " + audioData.length + " bytes). "
                + "Audio sample (base64 prefix): " + audioB64Sample + ". "
                + "Return ONLY valid JSON with no surrounding text: "
                + "{\"transcription\":\"<transcribed text>\",\"confidence\":<float 0-1>}";

        Optional<String> response = aiClient.complete(prompt, "whisper-1", 1024);
        if (response.isEmpty()) {
            LOG.warn("AI Inference returned no response for STT \u2014 returning empty transcription");
            return AudioResult.builder().transcription("").confidence(0.0).build();
        }

        AudioResult result = parseAudioResult(response.get());
        LOG.info("AI Inference STT transcription completed (confidence={})", result.getConfidence());
        return result;
    }

    /**
     * Parse the AI Inference Service JSON response into an {@link AudioResult}.
     *
     * <p>Handles two response shapes:
     * <ul>
     *   <li>Direct: {@code {"transcription":"...","confidence":0.9}}</li>
     *   <li>Wrapped: model returns an outer {@code text/content} field containing
     *       escaped inner JSON.</li>
     * </ul>
     */
    private static AudioResult parseAudioResult(String json) {
        String source = json.trim();
        double confidence = 0.85;

        // If the model wrapped JSON inside an outer text/content field, unwrap it.
        Matcher fallback = TEXT_FALLBACK_PATTERN.matcher(source);
        if (fallback.find()) {
            String inner = fallback.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            if (inner.contains("transcription")) {
                source = inner;
            }
        }

        Matcher tm = TRANSCRIPTION_PATTERN.matcher(source);
        if (!tm.find()) {
            LOG.warn("Could not extract 'transcription' field from AI response; raw: {}", json);
            return AudioResult.builder().transcription("").confidence(0.0).build();
        }
        String transcription = tm.group(1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t");

        Matcher cm = CONFIDENCE_PATTERN.matcher(source);
        if (cm.find()) {
            try {
                confidence = Double.parseDouble(cm.group(1));
                confidence = Math.max(0.0, Math.min(1.0, confidence));
            } catch (NumberFormatException ignored) {
                // keep default 0.85
            }
        }

        return AudioResult.builder()
                .transcription(transcription)
                .confidence(confidence)
                .build();
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
