/**
 * @doc.type factory
 * @doc.purpose Factory for creating TTS Engine instances
 * @doc.layer platform
 * @doc.pattern Factory
 */
package com.ghatana.media.tts.api;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.TtsConfig;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import io.activej.promise.Promise;

/**
 * Factory for creating TTS Engine instances.
 */
public final class TtsEngineFactory {

    private static final Logger LOG = Logger.getLogger(TtsEngineFactory.class.getName());

    private TtsEngineFactory() {}

    /**
     * Create a new TTS Engine with the given configuration.
     * Prefers real ONNX engine, falls back to stub only when necessary.
     */
    public static TtsEngine create(TtsConfig config, AudioVideoLibrary.LibraryState libraryState) {
        LOG.info("Creating TTS Engine with voice: " + config.defaultVoiceId());

        // Try multiple model path resolution strategies
        java.nio.file.Path modelPath = resolveVoiceModelPath(config);
        
        if (modelPath != null) {
            try {
                // Create config with resolved path
                TtsConfig resolvedConfig = TtsConfig.builder()
                    .voiceModelPath(modelPath)
                    .defaultVoiceId(config.defaultVoiceId())
                    .useGpu(config.useGpu())
                    .maxConcurrentRequests(config.maxConcurrentRequests())
                    .sampleRate(config.sampleRate())
                    .build();
                    
                TtsEngine engine = new com.ghatana.media.tts.engine.onnx.PiperOnnxEngine(resolvedConfig, libraryState);
                LOG.info("Successfully loaded ONNX TTS engine from: " + modelPath);
                return engine;
            } catch (Exception e) {
                LOG.warning("Failed to load ONNX TTS engine from " + modelPath + ": " + e.getMessage());
            }
        }

        // Fallback to stub implementation with warning
        LOG.warning("Using stub TTS engine - no valid ONNX voice model found. " +
            "Set TTS_MODEL_PATH environment variable to a valid Piper ONNX model.");
        return new StubTtsEngine(config, libraryState);
    }
    
    /**
     * Resolve voice model path using multiple strategies.
     */
    private static java.nio.file.Path resolveVoiceModelPath(TtsConfig config) {
        // Strategy 1: Check explicit config path
        if (config.voiceModelPath() != null && config.voiceModelPath().toFile().exists()) {
            return config.voiceModelPath();
        }
        
        // Strategy 2: Environment variable
        String envPath = System.getenv("TTS_MODEL_PATH");
        if (envPath != null && !envPath.isEmpty()) {
            java.nio.file.Path path = java.nio.file.Paths.get(envPath);
            if (path.toFile().exists()) {
                return path;
            }
        }
        
        // Strategy 3: Common model directories
        String voiceId = config.defaultVoiceId();
        String[] commonPaths = {
            "/models/piper-" + voiceId + ".onnx",
            "/models/piper-en_US-lessac-medium.onnx",
            "/usr/local/share/piper/voices/" + voiceId + ".onnx",
            "models/piper-" + voiceId + ".onnx",
        };
        
        for (String pathStr : commonPaths) {
            java.nio.file.Path path = java.nio.file.Paths.get(pathStr);
            if (path.toFile().exists()) {
                LOG.info("Found TTS model at common path: " + path);
                return path;
            }
        }
        
        // Strategy 4: User home download directory
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            java.nio.file.Path userPath = java.nio.file.Paths.get(userHome, ".cache", "ghatana", "voices", 
                voiceId + ".onnx");
            if (userPath.toFile().exists()) {
                LOG.info("Found TTS model in user cache: " + userPath);
                return userPath;
            }
        }
        
        return null;
    }

    /**
     * Stub TTS Engine implementation.
     */
    private static class StubTtsEngine implements TtsEngine {
        private final TtsConfig config;
        private final AtomicLong requestCount = new AtomicLong(0);
        private final Semaphore concurrencyLimiter;
        private final ExecutorService executor;
        private final AtomicReference<EngineStatus.State> state = new AtomicReference<>(EngineStatus.State.READY);
        private final AtomicReference<String> activeVoiceId;

        StubTtsEngine(TtsConfig config, AudioVideoLibrary.LibraryState libraryState) {
            this.config = config;
            this.concurrencyLimiter = new Semaphore(config.maxConcurrentRequests());
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            this.activeVoiceId = new AtomicReference<>(config.defaultVoiceId());
            LOG.info("TTS Engine initialized");
        }

        @Override
        public AudioData synthesize(String text, SynthesisOptions options) {
            ensureReady();
            validateText(text);

            requestCount.incrementAndGet();

            try {
                concurrencyLimiter.acquire();
                try {
                    // Simulate synthesis
                    int samples = (int) (text.length() * config.sampleRate() * 0.1);
                    byte[] data = new byte[samples * 2]; // 16-bit samples

                    return AudioData.builder()
                        .data(data)
                        .sampleRate(config.sampleRate())
                        .channels(1)
                        .bitsPerSample(16)
                        .build();
                } finally {
                    concurrencyLimiter.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InferenceError("Synthesis interrupted", e, true);
            }
        }

        @Override
        public Promise<AudioData> synthesizeAsync(String text, SynthesisOptions options) {
            return Promise.ofBlocking(executor, () -> synthesize(text, options));
        }

        @Override
        public void synthesizeStreaming(String text, SynthesisOptions options, java.util.function.Consumer<com.ghatana.media.common.AudioChunk> chunkConsumer) {
            AudioData audio = synthesize(text, options);
            // Simulate streaming by sending in chunks
            int chunkSize = config.sampleRate() / 10; // 100ms chunks
            byte[] data = audio.data();

            for (int i = 0; i < data.length; i += chunkSize * 2) {
                int end = Math.min(i + chunkSize * 2, data.length);
                byte[] chunk = java.util.Arrays.copyOfRange(data, i, end);
                chunkConsumer.accept(new AudioChunk(chunk, i / (chunkSize * 2), end == data.length, System.currentTimeMillis()));
            }
        }

        @Override
        public List<VoiceInfo> getAvailableVoices() {
            return config.availableVoices().isEmpty()
                ? List.of(new VoiceInfo(
                    config.defaultVoiceId(),
                    "Default Voice",
                    "Default TTS voice",
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
            return new VoiceInfo(voiceId, voiceId, voiceId, Locale.ENGLISH, VoiceInfo.Gender.NEUTRAL, config.sampleRate(), false, 0L);
        }

        @Override
        public VoiceInfo getActiveVoice() {
            return loadVoice(activeVoiceId.get());
        }

        @Override
        public void setActiveVoice(String voiceId) {
            activeVoiceId.set(voiceId);
            LOG.info("Setting active voice: " + voiceId);
        }

        @Override
        public VoiceInfo cloneVoice(String voiceName, java.util.List<com.ghatana.media.common.AudioData> audioSamples, CloneOptions options) {
            if (!config.enableVoiceCloning()) {
                throw new UnsupportedOperationException("Voice cloning not enabled");
            }
            return new VoiceInfo(
                "cloned-" + voiceName,
                voiceName,
                "Cloned voice",
                Locale.ENGLISH,
                VoiceInfo.Gender.NEUTRAL,
                config.sampleRate(),
                true,
                0L
            );
        }

        @Override
        public TtsProfile createProfile(String profileId, String displayName, ProfileSettings settings) {
            return new TtsProfile(profileId, displayName, config.defaultVoiceId(), settings, List.of());
        }

        @Override
        public Optional<TtsProfile> loadProfile(String profileId) {
            return Optional.of(createProfile(profileId, "User", new ProfileSettings(1.0, 1.0, 1.0, Emotion.NEUTRAL, List.of())));
        }

        @Override
        public void saveProfile(TtsProfile profile) {
            // Stub
        }

        @Override
        public boolean deleteProfile(String profileId) {
            return true;
        }

        @Override
        public void warmup() {
            LOG.info("Warming up TTS Engine...");
        }

        @Override
        public void close() {
            LOG.info("Closing TTS Engine...");
            state.set(EngineStatus.State.CLOSED);
            executor.shutdown();
        }

        @Override
        public EngineStatus getStatus() {
            return new EngineStatus(
                state.get(),
                config.defaultVoiceId(),
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

        private void ensureReady() {
            if (state.get() != EngineStatus.State.READY) {
                throw new IllegalStateException("TTS Engine not ready");
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
    }
}
