package com.ghatana.stt.core.observability;

import com.ghatana.platform.observability.MetricsCollector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * STT-specific metrics collection using the platform observability library.
 *
 * <p>Provides comprehensive metrics for speech-to-text operations including:
 * <ul>
 *   <li>Transcription latency and throughput</li>
 *   <li>Real-time factor (RTF) tracking</li>
 *   <li>Model inference performance</li>
 *   <li>Session and streaming metrics</li>
 *   <li>Error rates and types</li>
 * </ul>
 *
 * <p><b>Metric Naming Convention:</b> {@code stt.<category>.<metric>}
 *
 * @doc.type class
 * @doc.purpose STT metrics collection with Micrometer/Prometheus
 * @doc.layer observability
 * @doc.pattern Facade, Singleton
 */
public final class SttMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(SttMetrics.class);

    private static final String PREFIX = "stt";

    private final MeterRegistry registry;
    private final MetricsCollector collector;

    // Counters
    private final Counter transcriptionsTotal;
    private final Counter transcriptionErrors;
    private final Counter sessionsCreated;
    private final Counter sessionsClosed;
    private final Counter audioChunksProcessed;
    private final Counter adaptationsApplied;

    // Timers
    private final Timer transcriptionLatency;
    private final Timer modelLoadTime;
    private final Timer featureExtractionTime;
    private final Timer inferenceTime;

    // Distribution summaries
    private final DistributionSummary audioDuration;
    private final DistributionSummary confidenceScore;
    private final DistributionSummary realTimeFactor;
    private final DistributionSummary wordCount;

    // Gauges (atomic values for thread-safe updates)
    private final AtomicInteger activeSessions;
    private final AtomicInteger loadedModels;
    private final AtomicLong memoryUsage;
    private final AtomicLong modelMemoryUsage;

    // Per-model metrics cache
    private final Map<String, ModelMetrics> modelMetricsCache;

    /**
     * Creates STT metrics with the provided metrics collector.
     *
     * @param collector the platform metrics collector
     */
    public SttMetrics(MetricsCollector collector) {
        this.collector = collector;
        this.registry = collector.getMeterRegistry();
        this.modelMetricsCache = new ConcurrentHashMap<>();

        // Initialize atomic values for gauges
        this.activeSessions = new AtomicInteger(0);
        this.loadedModels = new AtomicInteger(0);
        this.memoryUsage = new AtomicLong(0);
        this.modelMemoryUsage = new AtomicLong(0);

        // Register counters
        this.transcriptionsTotal = Counter.builder(PREFIX + ".transcriptions.total")
            .description("Total number of transcription requests")
            .register(registry);

        this.transcriptionErrors = Counter.builder(PREFIX + ".transcriptions.errors")
            .description("Total number of transcription errors")
            .register(registry);

        this.sessionsCreated = Counter.builder(PREFIX + ".sessions.created")
            .description("Total streaming sessions created")
            .register(registry);

        this.sessionsClosed = Counter.builder(PREFIX + ".sessions.closed")
            .description("Total streaming sessions closed")
            .register(registry);

        this.audioChunksProcessed = Counter.builder(PREFIX + ".audio.chunks.processed")
            .description("Total audio chunks processed in streaming")
            .register(registry);

        this.adaptationsApplied = Counter.builder(PREFIX + ".adaptations.applied")
            .description("Total adaptation updates applied")
            .register(registry);

        // Register timers
        this.transcriptionLatency = Timer.builder(PREFIX + ".transcription.latency")
            .description("Transcription processing latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.modelLoadTime = Timer.builder(PREFIX + ".model.load.time")
            .description("Model loading time")
            .register(registry);

        this.featureExtractionTime = Timer.builder(PREFIX + ".feature.extraction.time")
            .description("Audio feature extraction time")
            .register(registry);

        this.inferenceTime = Timer.builder(PREFIX + ".inference.time")
            .description("Model inference time")
            .register(registry);

        // Register distribution summaries
        this.audioDuration = DistributionSummary.builder(PREFIX + ".audio.duration.ms")
            .description("Audio duration in milliseconds")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.confidenceScore = DistributionSummary.builder(PREFIX + ".confidence.score")
            .description("Transcription confidence score")
            .register(registry);

        this.realTimeFactor = DistributionSummary.builder(PREFIX + ".rtf")
            .description("Real-time factor (processing time / audio duration)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.wordCount = DistributionSummary.builder(PREFIX + ".words.count")
            .description("Words per transcription")
            .register(registry);

        // Register gauges
        Gauge.builder(PREFIX + ".sessions.active", activeSessions, AtomicInteger::get)
            .description("Currently active streaming sessions")
            .register(registry);

        Gauge.builder(PREFIX + ".models.loaded", loadedModels, AtomicInteger::get)
            .description("Number of loaded models")
            .register(registry);

        Gauge.builder(PREFIX + ".memory.usage.bytes", memoryUsage, AtomicLong::get)
            .description("Total memory usage in bytes")
            .register(registry);

        Gauge.builder(PREFIX + ".models.memory.bytes", modelMemoryUsage, AtomicLong::get)
            .description("Memory used by loaded models")
            .register(registry);

        LOG.info("STT metrics initialized");
    }

    // =========================================================================
    // Transcription Metrics
    // =========================================================================

    /**
     * Records a successful transcription.
     *
     * @param audioDurationMs audio duration in milliseconds
     * @param processingTimeMs processing time in milliseconds
     * @param confidence confidence score (0-1)
     * @param wordCount number of words transcribed
     * @param modelId model used for transcription
     */
    public void recordTranscription(
            long audioDurationMs,
            long processingTimeMs,
            float confidence,
            int wordCount,
            String modelId) {

        transcriptionsTotal.increment();
        transcriptionLatency.record(processingTimeMs, TimeUnit.MILLISECONDS);
        audioDuration.record(audioDurationMs);
        confidenceScore.record(confidence);
        this.wordCount.record(wordCount);

        // Calculate and record RTF
        if (audioDurationMs > 0) {
            double rtf = (double) processingTimeMs / audioDurationMs;
            realTimeFactor.record(rtf);
        }

        // Update per-model metrics
        getModelMetrics(modelId).recordTranscription(processingTimeMs, confidence);

        // Also record to platform collector for aggregation
        collector.incrementCounter(PREFIX + ".transcriptions.total", "model", modelId);
        collector.recordTimer(PREFIX + ".transcription.latency", processingTimeMs, "model", modelId);
    }

    /**
     * Records a transcription error.
     *
     * @param errorType type of error
     * @param modelId model that was being used
     */
    public void recordTranscriptionError(String errorType, String modelId) {
        transcriptionErrors.increment();
        collector.incrementCounter(PREFIX + ".transcriptions.errors",
            "type", errorType, "model", modelId);
    }

    // =========================================================================
    // Session Metrics
    // =========================================================================

    /**
     * Records session creation.
     */
    public void recordSessionCreated() {
        sessionsCreated.increment();
        activeSessions.incrementAndGet();
    }

    /**
     * Records session closure.
     *
     * @param durationMs session duration in milliseconds
     */
    public void recordSessionClosed(long durationMs) {
        sessionsClosed.increment();
        activeSessions.decrementAndGet();
        collector.recordTimer(PREFIX + ".session.duration", durationMs);
    }

    /**
     * Records audio chunk processing in streaming mode.
     *
     * @param chunkSizeBytes chunk size in bytes
     * @param processingTimeMs processing time
     */
    public void recordAudioChunk(int chunkSizeBytes, long processingTimeMs) {
        audioChunksProcessed.increment();
        collector.recordTimer(PREFIX + ".chunk.processing.time", processingTimeMs);
    }

    // =========================================================================
    // Model Metrics
    // =========================================================================

    /**
     * Records model loading.
     *
     * @param modelId model identifier
     * @param loadTimeMs load time in milliseconds
     * @param memorySizeBytes memory used by model
     */
    public void recordModelLoaded(String modelId, long loadTimeMs, long memorySizeBytes) {
        modelLoadTime.record(loadTimeMs, TimeUnit.MILLISECONDS);
        loadedModels.incrementAndGet();
        modelMemoryUsage.addAndGet(memorySizeBytes);

        collector.incrementCounter(PREFIX + ".models.loaded.total", "model", modelId);
        collector.recordTimer(PREFIX + ".model.load.time", loadTimeMs, "model", modelId);
    }

    /**
     * Records model unloading.
     *
     * @param modelId model identifier
     * @param memorySizeBytes memory freed
     */
    public void recordModelUnloaded(String modelId, long memorySizeBytes) {
        loadedModels.decrementAndGet();
        modelMemoryUsage.addAndGet(-memorySizeBytes);
        modelMetricsCache.remove(modelId);
    }

    /**
     * Records feature extraction time.
     *
     * @param timeMs extraction time in milliseconds
     */
    public void recordFeatureExtraction(long timeMs) {
        featureExtractionTime.record(timeMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Records model inference time.
     *
     * @param timeMs inference time in milliseconds
     * @param modelId model used
     */
    public void recordInference(long timeMs, String modelId) {
        inferenceTime.record(timeMs, TimeUnit.MILLISECONDS);
        getModelMetrics(modelId).recordInference(timeMs);
    }

    // =========================================================================
    // Adaptation Metrics
    // =========================================================================

    /**
     * Records adaptation applied.
     *
     * @param adaptationType type of adaptation
     * @param profileId user profile
     */
    public void recordAdaptation(String adaptationType, String profileId) {
        adaptationsApplied.increment();
        collector.incrementCounter(PREFIX + ".adaptations.applied",
            "type", adaptationType, "profile", profileId);
    }

    // =========================================================================
    // Resource Metrics
    // =========================================================================

    /**
     * Updates memory usage gauge.
     *
     * @param bytes current memory usage
     */
    public void updateMemoryUsage(long bytes) {
        memoryUsage.set(bytes);
    }

    /**
     * Gets current active session count.
     *
     * @return active sessions
     */
    public int getActiveSessions() {
        return activeSessions.get();
    }

    /**
     * Gets current loaded model count.
     *
     * @return loaded models
     */
    public int getLoadedModels() {
        return loadedModels.get();
    }

    // =========================================================================
    // Per-Model Metrics
    // =========================================================================

    private ModelMetrics getModelMetrics(String modelId) {
        return modelMetricsCache.computeIfAbsent(modelId,
            id -> new ModelMetrics(registry, id));
    }

    /**
     * Per-model metrics container.
     */
    private static class ModelMetrics {
        private final Counter transcriptions;
        private final Timer latency;
        private final Timer inference;
        private final DistributionSummary confidence;

        ModelMetrics(MeterRegistry registry, String modelId) {
            Tags tags = Tags.of("model", modelId);

            this.transcriptions = Counter.builder(PREFIX + ".model.transcriptions")
                .tags(tags)
                .register(registry);

            this.latency = Timer.builder(PREFIX + ".model.latency")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

            this.inference = Timer.builder(PREFIX + ".model.inference")
                .tags(tags)
                .register(registry);

            this.confidence = DistributionSummary.builder(PREFIX + ".model.confidence")
                .tags(tags)
                .register(registry);
        }

        void recordTranscription(long latencyMs, float confidenceScore) {
            transcriptions.increment();
            latency.record(latencyMs, TimeUnit.MILLISECONDS);
            confidence.record(confidenceScore);
        }

        void recordInference(long timeMs) {
            inference.record(timeMs, TimeUnit.MILLISECONDS);
        }
    }
}
