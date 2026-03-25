/**
 * @doc.type library-root
 * @doc.purpose Unified Audio-Video Processing Library for Java
 * @doc.description Provides a stable, embeddable library for speech-to-text,
 * text-to-speech, computer vision, and multimodal processing. Can be used
 * standalone or as the foundation for gRPC/microservice deployments.
 *
 * <p>Key Features:
 * <ul>
 *   <li>Embeddable library API - no network required</li>
 *   <li>Clean lifecycle management with AutoCloseable</li>
 *   <li>Async Promise-based API for non-blocking operations</li>
 *   <li>Pluggable backends (native ONNX, cloud inference, hybrid)</li>
 *   <li>Unified configuration and metrics across all engines</li>
 *   <li>Built-in resource management and graceful degradation</li>
 * </ul>
 *
 * <p>Usage Example:
 * <pre>{@code
 * // Create and configure the library
 * AudioVideoLibrary library = AudioVideoLibrary.builder()
 *     .withSttConfig(SttConfig.builder()
 *         .modelPath("/models/whisper-base.onnx")
 *         .useGpu(true)
 *         .build())
 *     .withTtsConfig(TtsConfig.builder()
 *         .voiceModelPath("/models/piper-en.onnx")
 *         .build())
 *     .build();
 *
 * // Use STT engine
 * try (SttEngine stt = library.getSttEngine()) {
 *     TranscriptionResult result = stt.transcribe(audioData);
 *     System.out.println(result.getText());
 * }
 *
 * // Use TTS engine
 * try (TtsEngine tts = library.getTtsEngine()) {
 *     AudioData audio = tts.synthesize("Hello, world!");
 *     playAudio(audio);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
package com.ghatana.media;

import com.ghatana.media.api.*;
import com.ghatana.media.config.*;
import com.ghatana.media.lifecycle.*;
import com.ghatana.media.stt.api.*;
import com.ghatana.media.tts.api.*;
import com.ghatana.media.vision.api.*;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Unified Audio-Video Library providing access to STT, TTS, and Vision engines.
 *
 * <p>This is the main entry point for the audio-video library. Use the {@link #builder()}
 * method to create a configured instance.
 */
public final class AudioVideoLibrary implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AudioVideoLibrary.class.getName());

    private final LibraryConfig config;
    private final LibraryState state;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Engine instances (lazy-initialized)
    private volatile SttEngine sttEngine;
    private volatile TtsEngine ttsEngine;
    private volatile VisionEngine visionEngine;

    private AudioVideoLibrary(LibraryConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.state = new LibraryState();
        LOG.info("AudioVideoLibrary initialized with config: " + config);
    }

    /**
     * Create a new builder for configuring the library.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the Speech-to-Text engine.
     *
     * @return STT engine instance (cached)
     * @throws IllegalStateException if STT is not configured
     */
    public SttEngine getSttEngine() {
        ensureNotClosed();
        if (config.sttConfig() == null) {
            throw new IllegalStateException("STT not configured. Use builder.withSttConfig()");
        }
        if (sttEngine == null) {
            synchronized (this) {
                if (sttEngine == null) {
                    sttEngine = SttEngineFactory.create(config.sttConfig(), state);
                }
            }
        }
        return sttEngine;
    }

    /**
     * Get the Text-to-Speech engine.
     *
     * @return TTS engine instance (cached)
     * @throws IllegalStateException if TTS is not configured
     */
    public TtsEngine getTtsEngine() {
        ensureNotClosed();
        if (config.ttsConfig() == null) {
            throw new IllegalStateException("TTS not configured. Use builder.withTtsConfig()");
        }
        if (ttsEngine == null) {
            synchronized (this) {
                if (ttsEngine == null) {
                    ttsEngine = TtsEngineFactory.create(config.ttsConfig(), state);
                }
            }
        }
        return ttsEngine;
    }

    /**
     * Get the Vision (Computer Vision) engine.
     *
     * @return Vision engine instance (cached)
     * @throws IllegalStateException if Vision is not configured
     */
    public VisionEngine getVisionEngine() {
        ensureNotClosed();
        if (config.visionConfig() == null) {
            throw new IllegalStateException("Vision not configured. Use builder.withVisionConfig()");
        }
        if (visionEngine == null) {
            synchronized (this) {
                if (visionEngine == null) {
                    visionEngine = VisionEngineFactory.create(config.visionConfig(), state);
                }
            }
        }
        return visionEngine;
    }

    /**
     * Check if STT is configured.
     */
    public boolean isSttEnabled() {
        return config.sttConfig() != null;
    }

    /**
     * Check if TTS is configured.
     */
    public boolean isTtsEnabled() {
        return config.ttsConfig() != null;
    }

    /**
     * Check if Vision is configured.
     */
    public boolean isVisionEnabled() {
        return config.visionConfig() != null;
    }

    /**
     * Get the current library status.
     */
    public LibraryStatus getStatus() {
        return new LibraryStatus(
            state.isHealthy(),
            isSttEnabled() && sttEngine != null ? sttEngine.getStatus() : null,
            isTtsEnabled() && ttsEngine != null ? ttsEngine.getStatus() : null,
            isVisionEnabled() && visionEngine != null ? visionEngine.getStatus() : null,
            state.getMetrics()
        );
    }

    /**
     * Initialize all configured engines eagerly.
     * By default, engines are lazy-initialized on first use.
     */
    public Promise<LibraryStatus> initializeAsync() {
        ensureNotClosed();
        LOG.info("Eagerly initializing all engines...");

        Promise<?> sttInit = isSttEnabled()
            ? Promise.ofRunnable(() -> getSttEngine().warmup())
            : Promise.complete();

        Promise<?> ttsInit = isTtsEnabled()
            ? Promise.ofRunnable(() -> getTtsEngine().warmup())
            : Promise.complete();

        Promise<?> visionInit = isVisionEnabled()
            ? Promise.ofRunnable(() -> getVisionEngine().warmup())
            : Promise.complete();

        return Promise.all(sttInit, ttsInit, ttsInit)
            .map($ -> getStatus());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOG.info("Shutting down AudioVideoLibrary...");

            // Close engines in reverse order
            closeQuietly(visionEngine, "VisionEngine");
            closeQuietly(ttsEngine, "TtsEngine");
            closeQuietly(sttEngine, "SttEngine");

            state.shutdown();
            LOG.info("AudioVideoLibrary shutdown complete");
        }
    }

    private void closeQuietly(AutoCloseable resource, String name) {
        if (resource != null) {
            try {
                resource.close();
                LOG.fine(name + " closed successfully");
            } catch (Exception e) {
                LOG.warning("Error closing " + name + ": " + e.getMessage());
            }
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("AudioVideoLibrary has been closed");
        }
    }

    // ====================================================================================
    // Builder
    // ====================================================================================

    /**
     * Builder for configuring and creating AudioVideoLibrary instances.
     */
    public static final class Builder {
        private SttConfig sttConfig;
        private TtsConfig ttsConfig;
        private VisionConfig visionConfig;
        private boolean enableMetrics = true;
        private boolean enableTracing = false;

        private Builder() {}

        /**
         * Configure STT engine.
         */
        public Builder withSttConfig(SttConfig config) {
            this.sttConfig = config;
            return this;
        }

        /**
         * Configure TTS engine.
         */
        public Builder withTtsConfig(TtsConfig config) {
            this.ttsConfig = config;
            return this;
        }

        /**
         * Configure Vision engine.
         */
        public Builder withVisionConfig(VisionConfig config) {
            this.visionConfig = config;
            return this;
        }

        /**
         * Enable/disable metrics collection (default: true).
         */
        public Builder withMetrics(boolean enable) {
            this.enableMetrics = enable;
            return this;
        }

        /**
         * Enable/disable distributed tracing (default: false).
         */
        public Builder withTracing(boolean enable) {
            this.enableTracing = enable;
            return this;
        }

        /**
         * Build the library instance.
         *
         * @throws IllegalStateException if no engines are configured
         */
        public AudioVideoLibrary build() {
            if (sttConfig == null && ttsConfig == null && visionConfig == null) {
                throw new IllegalStateException(
                    "At least one engine must be configured. " +
                    "Use withSttConfig(), withTtsConfig(), or withVisionConfig()"
                );
            }

            LibraryConfig config = new LibraryConfig(sttConfig, ttsConfig, visionConfig, enableMetrics, enableTracing);
            return new AudioVideoLibrary(config);
        }
    }

    // ====================================================================================
    // Internal Types
    // ====================================================================================

    private record LibraryConfig(
        SttConfig sttConfig,
        TtsConfig ttsConfig,
        VisionConfig visionConfig,
        boolean enableMetrics,
        boolean enableTracing
    ) {}

    /**
     * Shared library state for cross-engine coordination.
     */
    public static final class LibraryState {
        private final ConcurrentHashMap<String, Object> sharedCache = new ConcurrentHashMap<>();
        private final AtomicBoolean healthy = new AtomicBoolean(true);

        public void markUnhealthy(String reason) {
            healthy.set(false);
        }

        public boolean isHealthy() {
            return healthy.get();
        }

        public LibraryMetrics getMetrics() {
            // Aggregate metrics from all engines
            return new LibraryMetrics(
                sharedCache.size(),
                Runtime.getRuntime().freeMemory()
            );
        }

        public void shutdown() {
            sharedCache.clear();
        }
    }

    public record LibraryStatus(
        boolean healthy,
        EngineStatus sttStatus,
        EngineStatus ttsStatus,
        EngineStatus visionStatus,
        LibraryMetrics metrics
    ) {}

    public record LibraryMetrics(
        int cacheEntries,
        long freeMemory
    ) {}
}
