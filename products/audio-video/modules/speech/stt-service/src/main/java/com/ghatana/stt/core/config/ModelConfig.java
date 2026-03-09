package com.ghatana.stt.core.config;

/**
 * Configuration for model loading and inference.
 * 
 * @doc.type record
 * @doc.purpose Model configuration
 * @doc.layer config
 */
public record ModelConfig(
    /** Use GPU acceleration if available */
    boolean useGpu,
    
    /** Number of inference threads */
    int numThreads,
    
    /** Enable model caching */
    boolean enableCache,
    
    /** Maximum cache size in bytes */
    long maxCacheSize,
    
    /** ONNX optimization level (0-3) */
    int optimizationLevel,
    
    /** Preload models on startup */
    boolean preloadModels
) {
    public ModelConfig {
        numThreads = numThreads > 0 ? numThreads : Runtime.getRuntime().availableProcessors();
        optimizationLevel = Math.max(0, Math.min(3, optimizationLevel));
        maxCacheSize = maxCacheSize > 0 ? maxCacheSize : 2L * 1024 * 1024 * 1024; // 2GB default
    }

    public static ModelConfig defaults() {
        return new ModelConfig(
            true,
            Runtime.getRuntime().availableProcessors(),
            true,
            2L * 1024 * 1024 * 1024,
            3,
            true
        );
    }

    public static ModelConfig lowMemory() {
        return new ModelConfig(
            false,
            2,
            false,
            512L * 1024 * 1024,
            1,
            false
        );
    }
}
