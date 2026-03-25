/**
 * @doc.type factory
 * @doc.purpose Factory for creating STT Engine instances
 * @doc.layer stt
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
import java.util.logging.Level;
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
     *
     * @param config engine configuration
     * @param libraryState shared library state
     * @return configured STT engine
     */
    public static SttEngine create(SttConfig config, AudioVideoLibrary.LibraryState libraryState) {
        LOG.info("Creating STT Engine with model: " + config.modelId());

        // Check if ONNX model path is available
        if (config.modelPath() != null && config.modelPath().toFile().exists()) {
            try {
                return new com.ghatana.media.stt.engine.onnx.WhisperOnnxEngine(config, libraryState);
            } catch (Exception e) {
                LOG.warning("Failed to load ONNX engine, falling back to stub: " + e.getMessage());
            }
        }

        // Fallback to stub implementation
        LOG.info("Using stub STT engine (no model found at " + config.modelPath() + ")");
        return new StubSttEngine(config, libraryState);
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
        private final AtomicReference<EngineStatus.State> state = new AtomicReference<>(EngineStatus.State.INITIALIZING);

        StubSttEngine(SttConfig config, AudioVideoLibrary.LibraryState libraryState) {
            this.config = config;
            this.libraryState = libraryState;
            this.concurrencyLimiter = new Semaphore(config.maxConcurrentRequests());
            this.executor = Executors.newVirtualThreadPerTaskExecutor();

            // Simulate initialization
            executor.submit(() -> {
                try {
                    Thread.sleep(100); // Simulate model loading
                    state.set(EngineStatus.State.READY);
                    LOG.info("STT Engine initialized successfully");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    state.set(EngineStatus.State.ERROR);
                    libraryState.markUnhealthy("STT initialization failed");
                }
            });
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
            return Promise.ofCallable(executor, () -> transcribe(audio, options));
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
