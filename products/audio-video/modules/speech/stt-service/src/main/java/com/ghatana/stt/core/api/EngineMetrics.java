package com.ghatana.stt.core.api;

/**
 * Runtime metrics for the STT engine.
 * 
 * @doc.type record
 * @doc.purpose Engine performance metrics
 * @doc.layer api
 */
public record EngineMetrics(
    /** Real-time factor (< 1.0 means faster than real-time) */
    float realTimeFactor,
    
    /** Current memory usage in bytes */
    long memoryUsageBytes,
    
    /** Number of active streaming sessions */
    int activeSessions,
    
    /** Total transcriptions since startup */
    long totalTranscriptions,
    
    /** Average confidence score */
    float averageConfidence,
    
    /** Average processing latency in milliseconds */
    long averageLatencyMs,
    
    /** Model cache hit rate (0.0 to 1.0) */
    float cacheHitRate
) {
    public float wakeWordAccuracy() {
        return cacheHitRate;
    }

    /**
     * Get memory usage as a human-readable string.
     */
    public String memoryUsageString() {
        if (memoryUsageBytes < 1024) return memoryUsageBytes + " B";
        if (memoryUsageBytes < 1024 * 1024) return String.format("%.1f KB", memoryUsageBytes / 1024.0);
        if (memoryUsageBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", memoryUsageBytes / (1024.0 * 1024));
        return String.format("%.2f GB", memoryUsageBytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Check if performance is within acceptable bounds.
     */
    public boolean isPerformanceAcceptable() {
        return realTimeFactor < 0.5 && averageLatencyMs < 500;
    }

    public static EngineMetrics empty() {
        return new EngineMetrics(0, 0, 0, 0, 0, 0, 0);
    }
}
