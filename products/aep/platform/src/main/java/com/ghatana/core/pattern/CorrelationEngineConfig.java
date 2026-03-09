package com.ghatana.core.pattern;

/**
 * Configuration for advanced correlation engine.
 *
 * @see AdvancedCorrelationEngine
 * @doc.type class
 * @doc.purpose Correlation engine configuration
 * @doc.layer core
 * @doc.pattern Configuration
 */
public class CorrelationEngineConfig {

    private final SimilarityCalculator.SimilarityConfig similarityConfig;
    private final CausalInferenceConfig causalConfig;
    private final CorrelationCacheConfig cacheConfig;
    private final PerformanceConfig performanceConfig;

    private CorrelationEngineConfig(Builder builder) {
        this.similarityConfig = builder.similarityConfig;
        this.causalConfig = builder.causalConfig;
        this.cacheConfig = builder.cacheConfig;
        this.performanceConfig = builder.performanceConfig;
    }

    public SimilarityCalculator.SimilarityConfig getSimilarityConfig() { return similarityConfig; }
    public CausalInferenceConfig getCausalConfig() { return causalConfig; }
    public CorrelationCacheConfig getCacheConfig() { return cacheConfig; }
    public PerformanceConfig getPerformanceConfig() { return performanceConfig; }

    public static class Builder {
        private SimilarityCalculator.SimilarityConfig similarityConfig = new SimilarityCalculator.SimilarityConfig();
        private CausalInferenceConfig causalConfig = new CausalInferenceConfig();
        private CorrelationCacheConfig cacheConfig = new CorrelationCacheConfig();
        private PerformanceConfig performanceConfig = new PerformanceConfig();

        public Builder similarityConfig(SimilarityCalculator.SimilarityConfig config) {
            this.similarityConfig = config;
            return this;
        }

        public Builder causalConfig(CausalInferenceConfig config) {
            this.causalConfig = config;
            return this;
        }

        public Builder cacheConfig(CorrelationCacheConfig config) {
            this.cacheConfig = config;
            return this;
        }

        public Builder performanceConfig(PerformanceConfig config) {
            this.performanceConfig = config;
            return this;
        }

        public CorrelationEngineConfig build() {
            return new CorrelationEngineConfig(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Default configuration
    public static class DefaultCorrelationEngineConfig extends CorrelationEngineConfig {
        public DefaultCorrelationEngineConfig() {
            super(new Builder());
        }
    }

    public static class CausalInferenceConfig {
        private final double confidenceThreshold;
        private final int maxDepth;
        private final boolean enableTemporalConstraints;

        public CausalInferenceConfig() {
            this(0.7, 3, true);
        }

        public CausalInferenceConfig(double confidenceThreshold, int maxDepth, boolean enableTemporalConstraints) {
            this.confidenceThreshold = confidenceThreshold;
            this.maxDepth = maxDepth;
            this.enableTemporalConstraints = enableTemporalConstraints;
        }

        public double getConfidenceThreshold() { return confidenceThreshold; }
        public int getMaxDepth() { return maxDepth; }
        public boolean isEnableTemporalConstraints() { return enableTemporalConstraints; }
    }

    public static class CorrelationCacheConfig {
        private final long maxSize;
        private final long ttlMillis;
        private final boolean enabled;

        public CorrelationCacheConfig() {
            this(10000, 300000, true); // 10k entries, 5 minutes TTL
        }

        public CorrelationCacheConfig(long maxSize, long ttlMillis, boolean enabled) {
            this.maxSize = maxSize;
            this.ttlMillis = ttlMillis;
            this.enabled = enabled;
        }

        public long getMaxSize() { return maxSize; }
        public long getTtlMillis() { return ttlMillis; }
        public boolean isEnabled() { return enabled; }
    }

    public static class PerformanceConfig {
        private final int maxThreads;
        private final long timeoutMillis;
        private final boolean enableMetrics;

        public PerformanceConfig() {
            this(8, 30000, true); // 8 threads, 30s timeout
        }

        public PerformanceConfig(int maxThreads, long timeoutMillis, boolean enableMetrics) {
            this.maxThreads = maxThreads;
            this.timeoutMillis = timeoutMillis;
            this.enableMetrics = enableMetrics;
        }

        public int getMaxThreads() { return maxThreads; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public boolean isEnableMetrics() { return enableMetrics; }
    }
}
