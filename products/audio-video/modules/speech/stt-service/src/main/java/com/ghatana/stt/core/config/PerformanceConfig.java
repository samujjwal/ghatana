package com.ghatana.stt.core.config;

/**
 * Configuration for performance tuning.
 * 
 * @doc.type record
 * @doc.purpose Performance configuration
 * @doc.layer config
 */
public record PerformanceConfig(
    /** Target real-time factor (< 1.0 = faster than real-time) */
    float targetRtf,
    
    /** Maximum memory usage in bytes */
    long maxMemoryUsage,
    
    /** Thread pool size for background tasks */
    int threadPoolSize,
    
    /** Audio buffer size in milliseconds */
    int audioBufferMs,
    
    /** Streaming chunk size in milliseconds */
    int streamingChunkMs,
    
    /** Enable power-saving mode on battery */
    boolean powerSavingMode,
    
    /** Quality preset */
    QualityPreset qualityPreset
) {
    public PerformanceConfig {
        targetRtf = Math.max(0.1f, Math.min(1.0f, targetRtf));
        maxMemoryUsage = maxMemoryUsage > 0 ? maxMemoryUsage : 1024L * 1024 * 1024; // 1GB default
        threadPoolSize = threadPoolSize > 0 ? threadPoolSize : Runtime.getRuntime().availableProcessors();
        audioBufferMs = Math.max(100, Math.min(10000, audioBufferMs));
        streamingChunkMs = Math.max(50, Math.min(1000, streamingChunkMs));
    }

    public static PerformanceConfig defaults() {
        return new PerformanceConfig(
            0.3f,
            1024L * 1024 * 1024,
            Runtime.getRuntime().availableProcessors(),
            1000,
            100,
            false,
            QualityPreset.BALANCED
        );
    }

    public static PerformanceConfig fast() {
        return new PerformanceConfig(
            0.5f,
            512L * 1024 * 1024,
            Runtime.getRuntime().availableProcessors() / 2,
            500,
            50,
            true,
            QualityPreset.FAST
        );
    }

    public static PerformanceConfig accurate() {
        return new PerformanceConfig(
            0.2f,
            2048L * 1024 * 1024,
            Runtime.getRuntime().availableProcessors(),
            2000,
            200,
            false,
            QualityPreset.ACCURATE
        );
    }

    public enum QualityPreset {
        FAST,
        BALANCED,
        ACCURATE
    }
}
