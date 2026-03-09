package com.ghatana.stt.core.metrics;

import com.ghatana.stt.core.api.EngineMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time metrics collector for STT engine.
 *
 * <p>Collects and aggregates performance metrics including
 * RTF, memory usage, session counts, and latency statistics.
 *
 * @doc.type class
 * @doc.purpose Real-time metrics collection
 * @doc.layer core
 * @doc.pattern Observer
 */
public class MetricsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);

    private final AtomicLong totalTranscriptions = new AtomicLong(0);
    private final AtomicLong totalAudioDurationMs = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final AtomicLong totalConfidenceSum = new AtomicLong(0); // Stored as int (confidence * 1000)
    private final AtomicLong wakeWordDetections = new AtomicLong(0);

    private final MemoryMXBean memoryBean;

    public MetricsCollector() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Record a transcription operation.
     *
     * @param audioDurationMs Duration of input audio in milliseconds
     * @param processingTimeMs Time taken to process in milliseconds
     * @param confidence Confidence score (0.0 to 1.0)
     */
    public void recordTranscription(long audioDurationMs, long processingTimeMs, float confidence) {
        totalTranscriptions.incrementAndGet();
        totalAudioDurationMs.addAndGet(audioDurationMs);
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        totalConfidenceSum.addAndGet((long) (confidence * 1000));

        if (totalTranscriptions.get() % 100 == 0) {
            LOG.debug("Metrics: {} transcriptions, RTF={}, avg confidence={}",
                totalTranscriptions.get(),
                String.format("%.3f", calculateRealTimeFactor()),
                String.format("%.3f", calculateAverageConfidence())
            );
        }
    }

    /**
     * Record a session start.
     */
    public void recordSessionStart() {
        int current = activeSessions.incrementAndGet();
        LOG.debug("Session started, active sessions: {}", current);
    }

    /**
     * Record a session end.
     */
    public void recordSessionEnd() {
        int current = activeSessions.decrementAndGet();
        LOG.debug("Session ended, active sessions: {}", current);
    }

    /**
     * Record a wake word detection.
     */
    public void recordWakeWordDetection() {
        wakeWordDetections.incrementAndGet();
    }

    /**
     * Get current engine metrics.
     */
    public EngineMetrics getMetrics() {
        long transcriptions = totalTranscriptions.get();
        float rtf = calculateRealTimeFactor();
        long memoryUsage = getMemoryUsage();
        int sessions = activeSessions.get();
        float avgConfidence = calculateAverageConfidence();
        float avgLatency = calculateAverageLatency();
        float wakeWordAccuracy = calculateWakeWordAccuracy();

        return new EngineMetrics(
            rtf,
            memoryUsage,
            sessions,
            transcriptions,
            avgConfidence,
            (long) avgLatency,
            wakeWordAccuracy
        );
    }

    /**
     * Calculate real-time factor.
     * RTF = processing_time / audio_duration
     * < 1.0 means faster than real-time
     */
    private float calculateRealTimeFactor() {
        long audioDuration = totalAudioDurationMs.get();
        if (audioDuration == 0) return 0.0f;

        long processingTime = totalProcessingTimeMs.get();
        return (float) processingTime / audioDuration;
    }

    /**
     * Get current memory usage in bytes.
     */
    private long getMemoryUsage() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    /**
     * Calculate average confidence across all transcriptions.
     */
    private float calculateAverageConfidence() {
        long transcriptions = totalTranscriptions.get();
        if (transcriptions == 0) return 0.0f;

        long confidenceSum = totalConfidenceSum.get();
        return (float) confidenceSum / (transcriptions * 1000);
    }

    /**
     * Calculate average latency in milliseconds.
     */
    private float calculateAverageLatency() {
        long transcriptions = totalTranscriptions.get();
        if (transcriptions == 0) return 0.0f;

        long totalLatency = totalProcessingTimeMs.get();
        return (float) totalLatency / transcriptions;
    }

    /**
     * Calculate wake word detection accuracy.
     * Placeholder - would need false positive tracking for real accuracy.
     */
    private float calculateWakeWordAccuracy() {
        long detections = wakeWordDetections.get();
        if (detections == 0) return 0.0f;

        // For now, return a baseline accuracy
        // Real implementation would track true positives vs false positives
        return 0.95f;
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        totalTranscriptions.set(0);
        totalAudioDurationMs.set(0);
        totalProcessingTimeMs.set(0);
        totalConfidenceSum.set(0);
        wakeWordDetections.set(0);
        // Don't reset activeSessions as they represent current state
        LOG.info("Metrics reset");
    }

    /**
     * Get detailed metrics report as string.
     */
    public String getDetailedReport() {
        EngineMetrics metrics = getMetrics();
        return String.format(
            "STT Engine Metrics:\n" +
            "  Total Transcriptions: %d\n" +
            "  Real-Time Factor: %.3f\n" +
            "  Memory Usage: %.2f MB\n" +
            "  Active Sessions: %d\n" +
            "  Average Confidence: %.3f\n" +
            "  Average Latency: %.1f ms\n" +
            "  Wake Word Accuracy: %.3f",
            metrics.totalTranscriptions(),
            metrics.realTimeFactor(),
            metrics.memoryUsageBytes() / (1024.0 * 1024.0),
            metrics.activeSessions(),
            metrics.averageConfidence(),
            (float) metrics.averageLatencyMs(),
            calculateWakeWordAccuracy()
        );
    }
}

