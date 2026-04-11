/**
 * @doc.type factory
 * @doc.purpose Factory for creating STT Engine instances
 * @doc.layer platform
 * @doc.pattern Factory
 */
package com.ghatana.media.stt.api;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.*;
import com.ghatana.media.config.SttConfig;

import java.time.Duration;
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
 * Factory for creating STT Engine instances.
 */
public final class SttEngineFactory {

    private static final Logger LOG = Logger.getLogger(SttEngineFactory.class.getName());

    private SttEngineFactory() {}

    /**
     * Create a new STT Engine with the given configuration.
     * Prefers real ONNX engine, falls back to stub only when necessary.
     */
    public static SttEngine create(SttConfig config, AudioVideoLibrary.LibraryState libraryState) {
        LOG.info("Creating STT Engine with model: " + config.modelId());

        // Try multiple model path resolution strategies
        java.nio.file.Path modelPath = resolveModelPath(config);

        if (modelPath != null) {
            try {
                // Create config with resolved path
                SttConfig resolvedConfig = SttConfig.builder()
                    .modelPath(modelPath)
                    .modelId(config.modelId())
                    .useGpu(config.useGpu())
                    .maxConcurrentRequests(config.maxConcurrentRequests())
                    .maxAudioLengthSeconds(config.maxAudioLengthSeconds())
                    .build();

                SttEngine engine = new com.ghatana.media.stt.engine.onnx.WhisperOnnxEngine(resolvedConfig, libraryState);
                LOG.info("Successfully loaded ONNX STT engine from: " + modelPath);
                return engine;
            } catch (Exception e) {
                LOG.warning("Failed to load ONNX engine from " + modelPath + ": " + e.getMessage());
            }
        }

        // Fallback to stub implementation with warning
        LOG.warning("Using stub STT engine - no valid ONNX model found. " +
            "Set STT_MODEL_PATH environment variable to a valid Whisper ONNX model.");
        return new StubSttEngine(config, libraryState);
    }

    /**
     * Resolve model path using multiple strategies:
     * 1. Explicit config path if it exists
     * 2. Environment variable STT_MODEL_PATH
     * 3. Common model directories
     * 4. Download path in user home
     */
    private static java.nio.file.Path resolveModelPath(SttConfig config) {
        // Strategy 1: Check explicit config path
        if (config.modelPath() != null && config.modelPath().toFile().exists()) {
            return config.modelPath();
        }

        // Strategy 2: Environment variable
        String envPath = System.getenv("STT_MODEL_PATH");
        if (envPath != null && !envPath.isEmpty()) {
            java.nio.file.Path path = java.nio.file.Paths.get(envPath);
            if (path.toFile().exists()) {
                return path;
            }
        }

        // Strategy 3: Common model directories
        String[] commonPaths = {
            "/models/whisper-" + config.modelId() + ".onnx",
            "/models/whisper-base.onnx",
            "/usr/local/share/whisper/models/" + config.modelId() + ".onnx",
            "models/whisper-" + config.modelId() + ".onnx", // Relative to working dir
        };

        for (String pathStr : commonPaths) {
            java.nio.file.Path path = java.nio.file.Paths.get(pathStr);
            if (path.toFile().exists()) {
                LOG.info("Found model at common path: " + path);
                return path;
            }
        }

        // Strategy 4: User home download directory
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            java.nio.file.Path userPath = java.nio.file.Paths.get(userHome, ".cache", "ghatana", "models",
                "whisper-" + config.modelId() + ".onnx");
            if (userPath.toFile().exists()) {
                LOG.info("Found model in user cache: " + userPath);
                return userPath;
            }
        }

        return null;
    }

    /**
     * Stub implementation of STT Engine for library structure demonstration.
     * In production, this would be replaced with the actual implementation
     * using WhisperCppAdapter or OnnxWhisperEngine.
     */
    private static class StubSttEngine implements SttEngine {
        private final SttConfig config;
        private final AudioVideoLibrary.LibraryState libraryState;
        private final AtomicLong requestCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        private final AtomicLong totalLatency = new AtomicLong(0);
        private final Semaphore concurrencyLimiter;
        private final ExecutorService executor;
        private final AtomicReference<EngineStatus.State> state = new AtomicReference<>(EngineStatus.State.READY);

        StubSttEngine(SttConfig config, AudioVideoLibrary.LibraryState libraryState) {
            this.config = config;
            this.libraryState = libraryState;
            this.concurrencyLimiter = new Semaphore(config.maxConcurrentRequests());
            this.executor = Executors.newVirtualThreadPerTaskExecutor();

            LOG.info("STT Engine initialized successfully");
        }

        @Override
        public TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options) {
            ensureReady();
            validateAudio(audio);

            long startTime = System.currentTimeMillis();
            requestCount.incrementAndGet();

            try {
                concurrencyLimiter.acquire();
                try {
                    // Simulate transcription
                    Thread.sleep(100); // Simulate processing

                    String text = "Simulated transcription of " + audio.getSampleCount() + " samples";
                    long latency = System.currentTimeMillis() - startTime;
                    totalLatency.addAndGet(latency);

                    return new TranscriptionResult(
                        text,
                        0.95,
                        List.of(), // words
                        List.of(), // alternatives
                        Duration.ofMillis(latency),
                        options.language().toLanguageTag(),
                        config.modelId()
                    );
                } finally {
                    concurrencyLimiter.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errorCount.incrementAndGet();
                throw new InferenceError("Transcription interrupted", e, true);
            } catch (Exception e) {
                errorCount.incrementAndGet();
                throw new InferenceError("Transcription failed", e, false);
            }
        }

        @Override
        public Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) {
            return Promise.ofBlocking(executor, () -> transcribe(audio, options));
        }

        @Override
        public StreamingSession createStreamingSession() {
            return createStreamingSession(null);
        }

        @Override
        public StreamingSession createStreamingSession(UserProfile profile) {
            ensureReady();
            return new StubStreamingSession(profile, config);
        }

        @Override
        public UserProfile createProfile(String profileId, java.util.List<com.ghatana.media.common.AudioData> enrollmentAudio) {
            ensureReady();
            return new UserProfile(
                profileId,
                "User " + profileId,
                Locale.getDefault(),
                List.of(),
                new byte[0]
            );
        }

        @Override
        public Optional<UserProfile> loadProfile(String profileId) {
            return Optional.of(new UserProfile(
                profileId,
                "User " + profileId,
                Locale.getDefault(),
                List.of(),
                new byte[0]
            ));
        }

        @Override
        public void saveProfile(UserProfile profile) {
            // Stub - would persist to storage
        }

        @Override
        public boolean deleteProfile(String profileId) {
            return true;
        }

        @Override
        public List<String> listProfiles() {
            return List.of();
        }

        @Override
        public List<ModelInfo> getAvailableModels() {
            return List.of(
                new ModelInfo("whisper-tiny", "Whisper Tiny", "1.0", new Locale[]{Locale.ENGLISH}, 39000000L, false),
                new ModelInfo("whisper-base", "Whisper Base", "1.0", new Locale[]{Locale.ENGLISH}, 74000000L, true),
                new ModelInfo("whisper-small", "Whisper Small", "1.0", new Locale[]{Locale.ENGLISH}, 244000000L, true)
            );
        }

        @Override
        public void loadModel(String modelId) {
            LOG.info("Loading model: " + modelId);
        }

        @Override
        public ModelInfo getActiveModel() {
            return new ModelInfo(
                config.modelId(),
                config.modelId(),
                "1.0",
                new Locale[]{Locale.ENGLISH},
                0L,
                config.useGpu()
            );
        }

        @Override
        public void warmup() {
            LOG.info("Warming up STT Engine...");
        }

        @Override
        public void close() {
            LOG.info("Closing STT Engine...");
            state.set(EngineStatus.State.CLOSED);
            executor.shutdown();
        }

        @Override
        public EngineStatus getStatus() {
            return new EngineStatus(
                state.get(),
                config.modelId(),
                "1.0.0",
                0L,
                null
            );
        }

        @Override
        public EngineMetrics getMetrics() {
            long requests = requestCount.get();
            long errors = errorCount.get();
            long avgLatency = requests > 0 ? totalLatency.get() / requests : 0;

            return new EngineMetrics(
                requests,
                errors,
                avgLatency,
                config.maxConcurrentRequests() - concurrencyLimiter.availablePermits(),
                0L
            );
        }

        private void ensureReady() {
            if (state.get() != EngineStatus.State.READY) {
                throw new IllegalStateException("STT Engine not ready. Current state: " + state.get());
            }
        }

        private void validateAudio(AudioData audio) {
            if (audio == null) {
                throw new ValidationError("Audio data cannot be null");
            }
            if (audio.duration() != null && audio.duration().getSeconds() > config.maxAudioLengthSeconds()) {
                throw new ValidationError("Audio too long: " + audio.duration().getSeconds() + "s > " + config.maxAudioLengthSeconds() + "s");
            }
        }
    }

    /**
     * Stub streaming session implementation.
     */
    private static class StubStreamingSession implements StreamingSession {
        private final UserProfile profile;
        private final SttConfig config;
        private final AtomicReference<Consumer<StreamingTranscription>> transcriptionCallback = new AtomicReference<>();
        private final AtomicReference<Consumer<ProcessingError>> errorCallback = new AtomicReference<>();
        private final AtomicReference<Boolean> active = new AtomicReference<>(true);
        private final StringBuilder accumulatedText = new StringBuilder();

        StubStreamingSession(UserProfile profile, SttConfig config) {
            this.profile = profile;
            this.config = config;
        }

        @Override
        public void feedAudio(AudioChunk chunk) {
            if (!active.get()) {
                throw new IllegalStateException("Session not active");
            }

            // Simulate transcription
            accumulatedText.append("[").append(chunk.sequenceNumber()).append("]");

            var callback = transcriptionCallback.get();
            if (callback != null) {
                callback.accept(new StreamingTranscription(
                    accumulatedText.toString(),
                    chunk.isLast(),
                    0.9,
                    List.of()
                ));
            }
        }

        @Override
        public void onTranscription(Consumer<StreamingTranscription> callback) {
            transcriptionCallback.set(callback);
        }

        @Override
        public void onError(Consumer<ProcessingError> callback) {
            errorCallback.set(callback);
        }

        @Override
        public void endStream() {
            active.set(false);
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void close() {
            active.set(false);
        }
    }
}
