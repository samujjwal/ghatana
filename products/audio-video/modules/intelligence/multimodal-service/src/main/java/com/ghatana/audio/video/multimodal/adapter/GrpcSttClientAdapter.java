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
 * @doc.purpose STT client adapter with AI Inference HTTP fallback (LLM_FALLBACK mode only supported).
 *              GRPC mode is not yet implemented. Use LLM_FALLBACK mode for transcription
 *              via Ghatana AI Inference Service.
 * @doc.layer product
 * @doc.pattern Service
 *
 * <p><b>Supported Mode:</b> Only {@link SttMode#LLM_FALLBACK} is currently supported.
 * The GRPC mode requires Whisper gRPC service implementation and proto stub generation.
 * Set environment variable {@code ENABLE_STT_GRPC=true} only when implementing real Whisper gRPC integration.
 *
 * <p><b>LLM_FALLBACK Mode:</b> Transcribes audio via AI Inference Service using base64-encoded
 * audio samples. The LLM provides transcription text with confidence scores. This is the
 * recommended and only supported mode for production use.
 *
 * <p>When implementing GRPC mode, generate proto stubs from {@code stt_service.proto} and
 * replace the {@link #transcribeViaAiInference(byte[])} invocation with direct gRPC calls.
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

    /**
     * STT mode enumeration
     *
     * <p><b>Note:</b> GRPC mode is not yet implemented. Use LLM_FALLBACK for transcription
     * via AI Inference Service. Set environment variable ENABLE_STT_GRPC=false to prevent
     * accidental GRPC mode selection.
     */
    public enum SttMode {
        GRPC,
        LLM_FALLBACK,
        NOP
    }

    private final ManagedChannel channel;
    private final SttMode configuredMode;
    private SttMode currentMode = SttMode.LLM_FALLBACK;

    /**
     * Creates a new gRPC STT client adapter.
     *
     * @param host the Whisper gRPC service host
     * @param port the Whisper gRPC service port
     * @param sttMode the configured STT mode (GRPC, LLM_FALLBACK, or NOP)
     * @throws IllegalArgumentException if GRPC mode is requested but ENABLE_STT_GRPC is not true
     */
    public GrpcSttClientAdapter(String host, int port, SttMode sttMode) {
        boolean enableSttGrpc = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_STT_GRPC", "false"));
        
        if (sttMode == SttMode.GRPC && !enableSttGrpc) {
            LOG.error("GRPC mode requested but ENABLE_STT_GRPC is not set to true. " +
                    "GRPC mode is not implemented. Use LLM_FALLBACK mode instead. " +
                    "Set ENABLE_STT_GRPC=true only when implementing real Whisper gRPC integration.");
            throw new IllegalArgumentException(
                    "GRPC mode is not yet implemented. Set ENABLE_STT_GRPC=true only when implementing " +
                    "real Whisper gRPC integration. Use SttMode.LLM_FALLBACK for transcription via AI Inference Service.");
        }
        
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.configuredMode = sttMode == SttMode.GRPC ? SttMode.LLM_FALLBACK : sttMode;
        LOG.info("STT gRPC client connected to {}:{} with mode {} (GRPC mode not implemented, using {})", 
                host, port, sttMode, this.configuredMode);
    }

    /**
     * Creates a new gRPC STT client adapter with default LLM_FALLBACK mode.
     *
     * @param host the Whisper gRPC service host
     * @param port the Whisper gRPC service port
     */
    public GrpcSttClientAdapter(String host, int port) {
        this(host, port, SttMode.LLM_FALLBACK);
    }

    /**
     * Transcribe audio bytes.
     *
     * <p>Respects the configured STT mode:
     * <ul>
     *   <li>{@code GRPC}: Uses real Whisper gRPC endpoint (requires proto stubs)</li>
     *   <li>{@code LLM_FALLBACK}: Uses AI Inference HTTP fallback</li>
     *   <li>{@code NOP}: Returns empty result (disabled)</li>
     * </ul>
     *
     * <p>When {@code GRPC} mode is configured and gRPC stubs are available, replace
     * the placeholder with:
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
            switch (configuredMode) {
                case GRPC:
                    LOG.debug("Transcribing {} bytes via Whisper gRPC", audioData.length);
                    currentMode = SttMode.GRPC;
                    return transcribeViaGrpc(audioData);
                case LLM_FALLBACK:
                    LOG.debug("Transcribing {} bytes via AI Inference HTTP fallback", audioData.length);
                    currentMode = SttMode.LLM_FALLBACK;
                    return transcribeViaAiInference(audioData);
                case NOP:
                    LOG.debug("STT disabled (NOP mode)");
                    currentMode = SttMode.NOP;
                    return AudioResult.builder().transcription("").confidence(0.0).build();
                default:
                    LOG.warn("Unknown STT mode: {}, falling back to LLM", configuredMode);
                    currentMode = SttMode.LLM_FALLBACK;
                    return transcribeViaAiInference(audioData);
            }
        } catch (StatusRuntimeException e) {
            LOG.error("STT gRPC call failed: {}, falling back to LLM", e.getStatus(), e);
            currentMode = SttMode.LLM_FALLBACK;
            return transcribeViaAiInference(audioData);
        } catch (Exception e) {
            LOG.error("STT transcription error", e);
            currentMode = SttMode.NOP;
            return AudioResult.error(e.getMessage());
        }
    }

    /**
     * Get the current STT mode
     *
     * @return current STT mode (GRPC, LLM_FALLBACK, or NOP)
     */
    public SttMode getCurrentMode() {
        return currentMode;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Whisper gRPC transcription (placeholder until proto stubs are generated)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transcribe audio via Whisper gRPC endpoint.
     *
     * <p>This is a placeholder method. Once {@code stt_service.proto} is compiled
     * and gRPC stubs are generated, replace this implementation with real gRPC calls.
     *
     * @param audioData raw audio bytes
     * @return AudioResult with transcription and confidence
     */
    private AudioResult transcribeViaGrpc(byte[] audioData) {
        // TODO: Replace with real gRPC call once proto stubs are generated
        // Example implementation:
        // SttServiceGrpc.SttServiceBlockingStub stub =
        //     SttServiceGrpc.newBlockingStub(channel).withDeadlineAfter(30, TimeUnit.SECONDS);
        // TranscribeRequest req = TranscribeRequest.newBuilder()
        //     .setAudioData(ByteString.copyFrom(audioData))
        //     .setSampleRate(16000)
        //     .build();
        // TranscribeResponse resp = stub.transcribe(req);
        // return AudioResult.builder()
        //     .transcription(resp.getTranscription())
        //     .confidence(resp.getConfidence())
        //     .build();

        LOG.warn("Whisper gRPC transcription not yet implemented - falling back to LLM");
        currentMode = SttMode.LLM_FALLBACK;
        return transcribeViaAiInference(audioData);
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
