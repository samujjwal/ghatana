package com.ghatana.stt.core.api;

/**
 * Options for loading an STT model.
 * 
 * @doc.type record
 * @doc.purpose Model loading configuration
 * @doc.layer api
 */
public record ModelOptions(
    /** Whether to use GPU acceleration if available */
    boolean useGpu,
    
    /** Number of threads for inference */
    int numThreads,
    
    /** Whether to enable model caching */
    boolean enableCache,
    
    /** Memory limit in bytes (0 = no limit) */
    long memoryLimitBytes,
    
    /** Optimization level (0-3) */
    int optimizationLevel
) {
    public ModelOptions {
        numThreads = numThreads > 0 ? numThreads : Runtime.getRuntime().availableProcessors();
        optimizationLevel = Math.max(0, Math.min(3, optimizationLevel));
    }

    /**
     * Create default options.
     */
    public static ModelOptions defaults() {
        return new ModelOptions(
            true,
            Runtime.getRuntime().availableProcessors(),
            true,
            0,
            3
        );
    }

    /**
     * Create options optimized for low memory usage.
     */
    public static ModelOptions lowMemory() {
        return new ModelOptions(
            false,
            2,
            false,
            512 * 1024 * 1024, // 512MB
            1
        );
    }

    /**
     * Create options optimized for speed.
     */
    public static ModelOptions highPerformance() {
        return new ModelOptions(
            true,
            Runtime.getRuntime().availableProcessors(),
            true,
            0,
            3
        );
    }
}
