package com.ghatana.tts.core.inference;

import com.ghatana.audio.video.common.platform.AiInferenceClient;
import com.ghatana.tts.core.api.*;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Inference Service fallback implementation of {@link TtsEngine}.
 *
 * <p>This engine delegates text-to-speech synthesis to the Ghatana AI Inference
 * Service via {@link AiInferenceClient#tts(String, String, int)}, enabling
 * the TTS gRPC service to function on hosts where the native Coqui TTS or
 * ONNX Runtime libraries are unavailable.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>At startup {@link #initialize()} checks whether the AI Inference Service
 *       is reachable and sets engine state to {@link EngineState#READY} or
 *       {@link EngineState#ERROR} accordingly.</li>
 *   <li>Each {@link #synthesize(String, SynthesisOptions)} call POSTs to
 *       {@code /ai/infer/tts} and parses the base64-encoded WAV payload from
 *       the JSON response.</li>
 *   <li>{@link #synthesizeStreaming} slices the fully-synthesized audio into
 *       fixed-size chunks and delivers them sequentially to the consumer.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose AI Inference Service fallback for TTS when native libs are absent
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AiInferenceTtsEngine implements TtsEngine {

    private static final Logger LOG = Logger.getLogger(AiInferenceTtsEngine.class.getName());

    /** Default Piper voice forwarded to the inference service. */
    private static final String DEFAULT_VOICE = "piper-en-us-amy-low";

    /** Default PCM sample rate in Hz. */
    private static final int DEFAULT_SAMPLE_RATE = 22_050;

    /** Streaming chunk size in bytes (~100 ms of 16-bit PCM @ 22050 Hz). */
    private static final int STREAM_CHUNK_BYTES = 4_410;

    // JSON field patterns for response parsing
    private static final Pattern AUDIO_B64_PATTERN =
            Pattern.compile("\"audio_b64\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SAMPLE_RATE_PATTERN =
            Pattern.compile("\"sample_rate\"\\s*:\\s*(\\d+)");
    private static final Pattern DURATION_MS_PATTERN =
            Pattern.compile("\"duration_ms\"\\s*:\\s*(\\d+)");

    private final AiInferenceClient aiClient;

    private volatile EngineState state = EngineState.INITIALIZING;
    private volatile String activeVoice = DEFAULT_VOICE;
    private final AtomicLong totalSyntheses = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    /**
     * Creates the engine using the singleton {@link AiInferenceClient}.
     */
    public AiInferenceTtsEngine() {
        this(AiInferenceClient.getInstance());
    }

    /**
     * Creates the engine with an explicit client (useful for tests).
     *
     * @param aiClient the AI inference client to use
     */
    public AiInferenceTtsEngine(AiInferenceClient aiClient) {
        this.aiClient = aiClient;
        initialize();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private void initialize() {
        if (aiClient.isReachable()) {
            state = EngineState.READY;
            LOG.info("[AiInferenceTtsEngine] AI Inference Service reachable — TTS fallback READY");
        } else {
            state = EngineState.ERROR;
            LOG.warning("[AiInferenceTtsEngine] AI Inference Service not reachable — TTS synthesis will fail");
        }
    }

    // -------------------------------------------------------------------------
    // TtsEngine implementation
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public SynthesisResult synthesize(String text, SynthesisOptions options) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        String voice = resolveVoice(options);
        int sampleRate = resolveSampleRate(options);

        long startMs = System.currentTimeMillis();

        Optional<String> response = aiClient.tts(text, voice, sampleRate);

        if (response.isEmpty()) {
            LOG.warning("[AiInferenceTtsEngine] No response for synthesize — returning silence");
            byte[] silence = generateSilence(text, sampleRate);
            return new SynthesisResult(silence, sampleRate, estimateDurationMs(silence, sampleRate), voice);
        }

        SynthesisResult result = parseResponse(response.get(), voice, sampleRate);

        long latency = System.currentTimeMillis() - startMs;
        totalSyntheses.incrementAndGet();
        totalLatencyMs.addAndGet(latency);
        LOG.fine(() -> String.format("[AiInferenceTtsEngine] Synthesized %d chars in %d ms via AI inference",
                text.length(), latency));

        return result;
    }

    /** {@inheritDoc} */
    @Override
    public void synthesizeStreaming(String text, SynthesisOptions options, Consumer<AudioChunk> chunkConsumer) {
        // Fully synthesize first, then stream in fixed-size chunks.
        SynthesisResult full = synthesize(text, options);
        byte[] audio = full.getAudioData();
        int sampleRate = full.getSampleRate();
        int offset = 0;
        long timestampMs = 0L;

        while (offset < audio.length) {
            int len = Math.min(STREAM_CHUNK_BYTES, audio.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(audio, offset, chunk, 0, len);

            boolean isFinal = (offset + len) >= audio.length;
            chunkConsumer.accept(new AudioChunk(chunk, sampleRate, timestampMs, isFinal));

            // Advance timestamp: each byte is 2 bytes/sample at sampleRate Hz
            long samplesInChunk = len / 2L;
            timestampMs += (samplesInChunk * 1000L) / sampleRate;
            offset += len;
        }
    }

    /** {@inheritDoc} */
    @Override
    public EngineStatus getStatus() {
        return new EngineStatus(state, activeVoice, null, 0);
    }

    /** {@inheritDoc} */
    @Override
    public EngineMetrics getMetrics() {
        long syntheses = totalSyntheses.get();
        float avgLatency = syntheses > 0 ? (float) totalLatencyMs.get() / syntheses : 0f;
        return new EngineMetrics(1.0f, 0L, 0, syntheses, avgLatency);
    }

    /** {@inheritDoc} */
    @Override
    public List<VoiceInfo> getAvailableVoices(String languageFilter) {
        // Return the single built-in Piper voice this fallback supports.
        return List.of(new VoiceInfo(
                DEFAULT_VOICE,
                "Amy (AI Inference)",
                "English US voice synthesized via AI Inference Service (Piper backend)",
                List.of("en", "en-US"),
                "female",
                0L,
                true,
                false
        ));
    }

    /** {@inheritDoc} */
    @Override
    public VoiceInfo loadVoice(String voiceId) {
        this.activeVoice = (voiceId != null && !voiceId.isBlank()) ? voiceId : DEFAULT_VOICE;
        return new VoiceInfo(
                activeVoice, activeVoice,
                "Voice loaded via AI Inference Service",
                List.of("en"), "neutral", 0L, true, false
        );
    }

    /** {@inheritDoc} */
    @Override
    public UserProfile createProfile(String profileId, String displayName, ProfileSettings settings) {
        LOG.fine("[AiInferenceTtsEngine] createProfile called — returning stub profile");
        long now = System.currentTimeMillis();
        ProfileStats stats = new ProfileStats(0L, 0, now, now);
        return new UserProfile(profileId, displayName, settings, stats);
    }

    /** {@inheritDoc} */
    @Override
    public UserProfile getProfile(String profileId) {
        long now = System.currentTimeMillis();
        ProfileStats stats = new ProfileStats(0L, 0, now, now);
        return new UserProfile(profileId, profileId, null, stats);
    }

    /** {@inheritDoc} */
    @Override
    public UserProfile updateProfile(String profileId, ProfileSettings settings) {
        long now = System.currentTimeMillis();
        ProfileStats stats = new ProfileStats(0L, 0, now, now);
        return new UserProfile(profileId, profileId, settings, stats);
    }

    /** {@inheritDoc} */
    @Override
    public CloneResult cloneVoice(String voiceName, List<byte[]> audioSamples, int epochs, float learningRate) {
        LOG.warning("[AiInferenceTtsEngine] Voice cloning is not supported by the AI Inference fallback engine");
        return new CloneResult(false, "Voice cloning requires native TTS engine", null, 0f, null);
    }

    /** {@inheritDoc} */
    @Override
    public void submitFeedback(String profileId, String synthesisId, String feedbackType, String comment) {
        LOG.info(String.format("[AiInferenceTtsEngine] Feedback received (profileId=%s, type=%s) — logged only",
                profileId, feedbackType));
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        state = EngineState.SHUTDOWN;
        LOG.info("[AiInferenceTtsEngine] Engine shut down");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private SynthesisResult parseResponse(String json, String voice, int defaultSampleRate) {
        try {
            // Extract base64 audio payload
            Matcher audioMatcher = AUDIO_B64_PATTERN.matcher(json);
            if (!audioMatcher.find()) {
                LOG.warning("[AiInferenceTtsEngine] Response missing 'audio_b64' field — returning silence");
                byte[] silence = generateSilence("fallback", defaultSampleRate);
                return new SynthesisResult(silence, defaultSampleRate, estimateDurationMs(silence, defaultSampleRate), voice);
            }

            byte[] audioData = Base64.getDecoder().decode(audioMatcher.group(1));

            // Extract optional fields
            int sampleRate = defaultSampleRate;
            Matcher srMatcher = SAMPLE_RATE_PATTERN.matcher(json);
            if (srMatcher.find()) {
                sampleRate = Integer.parseInt(srMatcher.group(1));
            }

            long durationMs = estimateDurationMs(audioData, sampleRate);
            Matcher durMatcher = DURATION_MS_PATTERN.matcher(json);
            if (durMatcher.find()) {
                durationMs = Long.parseLong(durMatcher.group(1));
            }

            return new SynthesisResult(audioData, sampleRate, durationMs, voice);

        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "[AiInferenceTtsEngine] Failed to decode base64 audio", e);
            byte[] silence = generateSilence("fallback", defaultSampleRate);
            return new SynthesisResult(silence, defaultSampleRate, estimateDurationMs(silence, defaultSampleRate), voice);
        }
    }

    /**
     * Generate a silent 16-bit signed PCM byte array whose length approximates
     * a plausible speaking duration for the given text.
     *
     * <p>Estimated at ~150 words per minute, 5 chars/word → ~8 ms per char.
     */
    private static byte[] generateSilence(String text, int sampleRate) {
        int wordCount = Math.max(1, text.trim().split("\\s+").length);
        long durationMs = (wordCount * 1000L * 60L) / 150L;
        long samples = (sampleRate * durationMs) / 1000L;
        // 16-bit PCM → 2 bytes per sample; silence = all zeros
        return new byte[(int) (samples * 2)];
    }

    /** Estimate audio duration from raw 16-bit PCM byte array length. */
    private static long estimateDurationMs(byte[] pcm, int sampleRate) {
        if (sampleRate <= 0 || pcm.length == 0) return 0L;
        return ((long) pcm.length * 1000L) / (sampleRate * 2L);
    }

    private String resolveVoice(SynthesisOptions options) {
        if (options != null && options.voiceId() != null && !options.voiceId().isBlank()) {
            return options.voiceId();
        }
        return activeVoice;
    }

    private int resolveSampleRate(SynthesisOptions options) {
        // SynthesisOptions does not carry a sample rate; use the engine default.
        return DEFAULT_SAMPLE_RATE;
    }
}
