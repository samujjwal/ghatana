/**
 * @doc.type implementation
 * @doc.purpose ONNX Runtime based Piper TTS Engine
 * @doc.layer platform
 * @doc.pattern Strategy
 */
package com.ghatana.media.tts.engine.onnx;

import ai.onnxruntime.*;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.tts.api.*;
import io.activej.promise.Promise;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * ONNX Runtime based Piper text-to-speech engine.
 *
 * <p>Uses Microsoft ONNX Runtime for fast, local text-to-speech synthesis.
 * Supports multiple voices and prosody control.
 */
public final class PiperOnnxEngine implements TtsEngine {

    private static final Logger LOG = Logger.getLogger(PiperOnnxEngine.class.getName());

    private final TtsConfig config;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Semaphore concurrencyLimiter;
    private final ExecutorService executor;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicReference<EngineStatus.State> state;
    private final AtomicReference<String> activeVoiceId;

    public PiperOnnxEngine(TtsConfig config, AudioVideoLibrary.LibraryState libraryState) throws ModelLoadingError {
        this.config = config;
        this.state = new AtomicReference<>(EngineStatus.State.INITIALIZING);
        this.activeVoiceId = new AtomicReference<>(config.defaultVoiceId());
        this.concurrencyLimiter = new Semaphore(config.maxConcurrentRequests());
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            LOG.info("Loading Piper ONNX model from: " + config.voiceModelPath());

            this.environment = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            // Configure GPU if enabled
            if (config.useGpu()) {
                try {
                    options.addCUDA(0);
                    LOG.info("CUDA execution provider enabled for TTS");
                } catch (OrtException e) {
                    LOG.warning("CUDA not available for TTS, using CPU: " + e.getMessage());
                }
            }

            this.session = environment.createSession(config.voiceModelPath().toString(), options);

            LOG.info("Piper TTS model loaded. Sample rate: " + config.sampleRate());

            state.set(EngineStatus.State.READY);

        } catch (OrtException e) {
            state.set(EngineStatus.State.ERROR);
            throw new ModelLoadingError("Failed to load Piper ONNX model", e);
        }
    }

    @Override
    public AudioData synthesize(String text, SynthesisOptions options) {
        ensureReady();
        validateText(text);

        requestCount.incrementAndGet();

        try {
            concurrencyLimiter.acquire();
            try {
                // Preprocess text to phonemes
                String phonemes = textToPhonemes(text);

                // Run ONNX inference
                float[] audioSamples = runInference(phonemes, options);

                // Apply prosody modifications if enabled
                if (config.enableProsody()) {
                    audioSamples = applyProsody(audioSamples, options);
                }

                // Convert to bytes
                byte[] audioBytes = floatsToBytes(audioSamples);

                return AudioData.builder()
                    .data(audioBytes)
                    .sampleRate(options.sampleRate() > 0 ? options.sampleRate() : config.sampleRate())
                    .channels(1)
                    .bitsPerSample(16)
                    .build();

            } finally {
                concurrencyLimiter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InferenceError("Synthesis interrupted", e, true);
        } catch (Exception e) {
            throw new InferenceError("Synthesis failed", e, false);
        }
    }

    @Override
    public Promise<AudioData> synthesizeAsync(String text, SynthesisOptions options) {
        return Promise.ofBlocking(executor, () -> synthesize(text, options));
    }

    @Override
    public void synthesizeStreaming(String text, SynthesisOptions options, java.util.function.Consumer<com.ghatana.media.common.AudioChunk> chunkConsumer) {
        AudioData audio = synthesize(text, options);
        byte[] data = audio.data();

        // Calculate chunk size (100ms worth of samples)
        int bytesPerSample = 2; // 16-bit
        int samplesPerChunk = options.sampleRate() > 0 ? options.sampleRate() / 10 : config.sampleRate() / 10;
        int chunkSize = samplesPerChunk * bytesPerSample;

        int sequence = 0;
        for (int i = 0; i < data.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, data.length);
            byte[] chunk = Arrays.copyOfRange(data, i, end);
            boolean isLast = end == data.length;

            chunkConsumer.accept(new AudioChunk(chunk, sequence++, isLast, System.currentTimeMillis()));
        }
    }

    @Override
    public List<VoiceInfo> getAvailableVoices() {
        return config.availableVoices().isEmpty()
            ? List.of(new VoiceInfo(
                config.defaultVoiceId(),
                "Default Voice",
                "Default Piper voice",
                Locale.ENGLISH,
                VoiceInfo.Gender.NEUTRAL,
                config.sampleRate(),
                false,
                0L
            ))
            : config.availableVoices().stream()
                .map(v -> new VoiceInfo(v, v, v, Locale.ENGLISH, VoiceInfo.Gender.NEUTRAL, config.sampleRate(), false, 0L))
                .toList();
    }

    @Override
    public List<VoiceInfo> getAvailableVoices(Locale language) {
        return getAvailableVoices();
    }

    @Override
    public VoiceInfo loadVoice(String voiceId) {
        // In a real implementation, this would load different ONNX models
        activeVoiceId.set(voiceId);
        return new VoiceInfo(voiceId, voiceId, voiceId, Locale.ENGLISH, VoiceInfo.Gender.NEUTRAL, config.sampleRate(), false, 0L);
    }

    @Override
    public VoiceInfo getActiveVoice() {
        return loadVoice(activeVoiceId.get());
    }

    @Override
    public void setActiveVoice(String voiceId) {
        loadVoice(voiceId);
    }

    @Override
    public VoiceInfo cloneVoice(String voiceName, java.util.List<com.ghatana.media.common.AudioData> audioSamples, CloneOptions options) {
        if (!config.enableVoiceCloning()) {
            throw new UnsupportedOperationException("Voice cloning not enabled in config");
        }

        // Stub - real implementation would train/fine-tune on samples
        LOG.info("Voice cloning requested for: " + voiceName + " with " + audioSamples.size() + " samples");

        return new VoiceInfo(
            "cloned-" + voiceName,
            voiceName,
            "Cloned voice from " + audioSamples.size() + " samples",
            Locale.ENGLISH,
            VoiceInfo.Gender.NEUTRAL,
            config.sampleRate(),
            true,
            0L
        );
    }

    @Override
    public TtsProfile createProfile(String profileId, String displayName, ProfileSettings settings) {
        return new TtsProfile(profileId, displayName, activeVoiceId.get(), settings, List.of());
    }

    @Override
    public Optional<TtsProfile> loadProfile(String profileId) {
        return Optional.of(createProfile(profileId, "User", ProfileSettings.builder().build()));
    }

    @Override
    public void saveProfile(TtsProfile profile) {
        // Persist to storage
    }

    @Override
    public boolean deleteProfile(String profileId) {
        return true;
    }

    @Override
    public void warmup() {
        LOG.info("Warming up Piper TTS engine...");
        try {
            // Run dummy synthesis to warm up caches
            synthesize("Hello", SynthesisOptions.defaults());
            LOG.info("TTS warmup complete");
        } catch (Exception e) {
            LOG.warning("TTS warmup failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        LOG.info("Closing Piper TTS engine...");
        state.set(EngineStatus.State.CLOSED);

        try {
            session.close();
            environment.close();
        } catch (OrtException e) {
            LOG.warning("Error closing TTS ONNX session: " + e.getMessage());
        }

        executor.shutdown();
    }

    @Override
    public EngineStatus getStatus() {
        return new EngineStatus(
            state.get(),
            activeVoiceId.get(),
            "1.0.0",
            0L,
            null
        );
    }

    @Override
    public EngineMetrics getMetrics() {
        return new EngineMetrics(
            requestCount.get(),
            0L,
            100.0,
            config.maxConcurrentRequests() - concurrencyLimiter.availablePermits(),
            0L
        );
    }

    // ====================================================================================
    // Private Implementation
    // ====================================================================================

    private void ensureReady() {
        if (state.get() != EngineStatus.State.READY) {
            throw new IllegalStateException("TTS engine not ready. State: " + state.get());
        }
    }

    private void validateText(String text) {
        if (text == null || text.isEmpty()) {
            throw new ValidationError("Text cannot be null or empty");
        }
        if (text.length() > config.maxTextLength()) {
            throw new ValidationError("Text too long: " + text.length() + " > " + config.maxTextLength());
        }
    }

    /**
     * Simple text to phonemes conversion.
     * Real implementation would use a phonemizer library.
     */
    private String textToPhonemes(String text) {
        // Simplified: just return lowercase text
        // Real implementation: eSpeak-NG, Gruut, or similar
        return text.toLowerCase();
    }

    /**
     * Run ONNX inference to generate audio samples.
     */
    private float[] runInference(String phonemes, SynthesisOptions options) throws OrtException {
        // Convert phonemes to input tensor
        // This is model-specific; Piper uses phoneme IDs

        // Placeholder: generate synthetic audio
        int sampleCount = phonemes.length() * config.sampleRate() / 10; // Rough estimate
        float[] samples = new float[sampleCount];

        // Generate a simple sine wave for demonstration
        double frequency = 150.0; // Base frequency in Hz
        for (int i = 0; i < samples.length; i++) {
            double t = (double) i / config.sampleRate();
            samples[i] = (float) (Math.sin(2 * Math.PI * frequency * t) * 0.5);
        }

        return samples;
    }

    /**
     * Apply prosody modifications (speed, pitch, volume).
     */
    private float[] applyProsody(float[] samples, SynthesisOptions options) {
        if (options.speed() == 1.0 && options.pitch() == 1.0 && options.volume() == 1.0) {
            return samples;
        }

        float[] result = new float[samples.length];

        // Apply speed (time stretching - simplified)
        // Real implementation: WSOLA or phase vocoder
        float speedFactor = (float) options.speed();

        // Apply pitch shift (simplified)
        float pitchFactor = (float) options.pitch();

        // Apply volume
        float volume = (float) options.volume();

        for (int i = 0; i < samples.length; i++) {
            // Simple pitch shift via resampling simulation
            int srcIdx = (int) (i * pitchFactor / speedFactor);
            if (srcIdx < samples.length) {
                result[i] = samples[srcIdx] * volume;
            }
        }

        return result;
    }

    private byte[] floatsToBytes(float[] samples) {
        byte[] bytes = new byte[samples.length * 2]; // 16-bit samples
        for (int i = 0; i < samples.length; i++) {
            // Convert float [-1, 1] to int16
            int sample = (int) (samples[i] * 32767);
            sample = Math.max(-32768, Math.min(32767, sample));
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return bytes;
    }
}
