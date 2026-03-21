package com.ghatana.core.pattern.learning;

/**
 * Configuration for the real-time pattern learning engine.
 *
 * @doc.type class
 * @doc.purpose Learning engine configuration
 * @doc.layer core
 * @doc.pattern Configuration
 */
public class LearningEngineConfig {

    // Learning intervals and periods
    private final long learningInterval; // milliseconds
    private final long optimizationInterval; // milliseconds
    private final long evolutionInterval; // milliseconds
    private final long stateRetentionPeriod; // milliseconds
    private final long timeWindow; // milliseconds

    // Thresholds
    private final double minPatternConfidence;
    private final long minPatternSupport;
    private final double minSequenceConfidence;
    private final int minSequenceFrequency;
    private final double minCorrelationThreshold;
    private final long minFrequencyThreshold;
    private final double minAnomalyThreshold;
    private final double minOptimizationThreshold;
    private final double minEvolutionThreshold;

    // Resource limits
    private final int maxLearningStates;
    private final int maxDiscoveredPatterns;
    private final int threadPoolSize;

    // Component configurations
    private final SequenceAnalyzerConfig sequenceConfig;
    private final CorrelationDetectorConfig correlationConfig;
    private final AnomalyDetectorConfig anomalyConfig;
    private final OptimizationConfig optimizationConfig;
    private final EvolutionConfig evolutionConfig;

    private LearningEngineConfig(Builder builder) {
        this.learningInterval = builder.learningInterval;
        this.optimizationInterval = builder.optimizationInterval;
        this.evolutionInterval = builder.evolutionInterval;
        this.stateRetentionPeriod = builder.stateRetentionPeriod;
        this.timeWindow = builder.timeWindow;
        
        this.minPatternConfidence = builder.minPatternConfidence;
        this.minPatternSupport = builder.minPatternSupport;
        this.minSequenceConfidence = builder.minSequenceConfidence;
        this.minSequenceFrequency = builder.minSequenceFrequency;
        this.minCorrelationThreshold = builder.minCorrelationThreshold;
        this.minFrequencyThreshold = builder.minFrequencyThreshold;
        this.minAnomalyThreshold = builder.minAnomalyThreshold;
        this.minOptimizationThreshold = builder.minOptimizationThreshold;
        this.minEvolutionThreshold = builder.minEvolutionThreshold;
        
        this.maxLearningStates = builder.maxLearningStates;
        this.maxDiscoveredPatterns = builder.maxDiscoveredPatterns;
        this.threadPoolSize = builder.threadPoolSize;
        
        this.sequenceConfig = builder.sequenceConfig;
        this.correlationConfig = builder.correlationConfig;
        this.anomalyConfig = builder.anomalyConfig;
        this.optimizationConfig = builder.optimizationConfig;
        this.evolutionConfig = builder.evolutionConfig;
    }

    // Getters
    public long getLearningInterval() { return learningInterval; }
    public long getOptimizationInterval() { return optimizationInterval; }
    public long getEvolutionInterval() { return evolutionInterval; }
    public long getStateRetentionPeriod() { return stateRetentionPeriod; }
    public long getTimeWindow() { return timeWindow; }
    
    public double getMinPatternConfidence() { return minPatternConfidence; }
    public long getMinPatternSupport() { return minPatternSupport; }
    public double getMinSequenceConfidence() { return minSequenceConfidence; }
    public int getMinSequenceFrequency() { return minSequenceFrequency; }
    public double getMinCorrelationThreshold() { return minCorrelationThreshold; }
    public long getMinFrequencyThreshold() { return minFrequencyThreshold; }
    public double getMinAnomalyThreshold() { return minAnomalyThreshold; }
    public double getMinOptimizationThreshold() { return minOptimizationThreshold; }
    public double getMinEvolutionThreshold() { return minEvolutionThreshold; }
    
    public int getMaxLearningStates() { return maxLearningStates; }
    public int getMaxDiscoveredPatterns() { return maxDiscoveredPatterns; }
    public int getThreadPoolSize() { return threadPoolSize; }
    
    public SequenceAnalyzerConfig getSequenceConfig() { return sequenceConfig; }
    public CorrelationDetectorConfig getCorrelationConfig() { return correlationConfig; }
    public AnomalyDetectorConfig getAnomalyConfig() { return anomalyConfig; }
    public OptimizationConfig getOptimizationConfig() { return optimizationConfig; }
    public EvolutionConfig getEvolutionConfig() { return evolutionConfig; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        // Learning intervals and periods
        private long learningInterval = 60000; // 1 minute
        private long optimizationInterval = 300000; // 5 minutes
        private long evolutionInterval = 600000; // 10 minutes
        private long stateRetentionPeriod = 3600000; // 1 hour
        private long timeWindow = 300000; // 5 minutes

        // Thresholds
        private double minPatternConfidence = 0.7;
        private long minPatternSupport = 10;
        private double minSequenceConfidence = 0.6;
        private int minSequenceFrequency = 5;
        private double minCorrelationThreshold = 0.5;
        private long minFrequencyThreshold = 20;
        private double minAnomalyThreshold = 0.8;
        private double minOptimizationThreshold = 0.1;
        private double minEvolutionThreshold = 0.7;

        // Resource limits
        private int maxLearningStates = 10000;
        private int maxDiscoveredPatterns = 1000;
        private int threadPoolSize = 4;

        // Component configurations
        private SequenceAnalyzerConfig sequenceConfig = SequenceAnalyzerConfig.defaultConfig();
        private CorrelationDetectorConfig correlationConfig = CorrelationDetectorConfig.defaultConfig();
        private AnomalyDetectorConfig anomalyConfig = AnomalyDetectorConfig.defaultConfig();
        private OptimizationConfig optimizationConfig = OptimizationConfig.defaultConfig();
        private EvolutionConfig evolutionConfig = EvolutionConfig.defaultConfig();

        public Builder learningInterval(long learningInterval) {
            this.learningInterval = learningInterval;
            return this;
        }

        public Builder optimizationInterval(long optimizationInterval) {
            this.optimizationInterval = optimizationInterval;
            return this;
        }

        public Builder evolutionInterval(long evolutionInterval) {
            this.evolutionInterval = evolutionInterval;
            return this;
        }

        public Builder stateRetentionPeriod(long stateRetentionPeriod) {
            this.stateRetentionPeriod = stateRetentionPeriod;
            return this;
        }

        public Builder timeWindow(long timeWindow) {
            this.timeWindow = timeWindow;
            return this;
        }

        public Builder minPatternConfidence(double minPatternConfidence) {
            this.minPatternConfidence = minPatternConfidence;
            return this;
        }

        public Builder minPatternSupport(long minPatternSupport) {
            this.minPatternSupport = minPatternSupport;
            return this;
        }

        public Builder minSequenceConfidence(double minSequenceConfidence) {
            this.minSequenceConfidence = minSequenceConfidence;
            return this;
        }

        public Builder minSequenceFrequency(int minSequenceFrequency) {
            this.minSequenceFrequency = minSequenceFrequency;
            return this;
        }

        public Builder minCorrelationThreshold(double minCorrelationThreshold) {
            this.minCorrelationThreshold = minCorrelationThreshold;
            return this;
        }

        public Builder minFrequencyThreshold(long minFrequencyThreshold) {
            this.minFrequencyThreshold = minFrequencyThreshold;
            return this;
        }

        public Builder minAnomalyThreshold(double minAnomalyThreshold) {
            this.minAnomalyThreshold = minAnomalyThreshold;
            return this;
        }

        public Builder minOptimizationThreshold(double minOptimizationThreshold) {
            this.minOptimizationThreshold = minOptimizationThreshold;
            return this;
        }

        public Builder minEvolutionThreshold(double minEvolutionThreshold) {
            this.minEvolutionThreshold = minEvolutionThreshold;
            return this;
        }

        public Builder maxLearningStates(int maxLearningStates) {
            this.maxLearningStates = maxLearningStates;
            return this;
        }

        public Builder maxDiscoveredPatterns(int maxDiscoveredPatterns) {
            this.maxDiscoveredPatterns = maxDiscoveredPatterns;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public Builder sequenceConfig(SequenceAnalyzerConfig sequenceConfig) {
            this.sequenceConfig = sequenceConfig;
            return this;
        }

        public Builder correlationConfig(CorrelationDetectorConfig correlationConfig) {
            this.correlationConfig = correlationConfig;
            return this;
        }

        public Builder anomalyConfig(AnomalyDetectorConfig anomalyConfig) {
            this.anomalyConfig = anomalyConfig;
            return this;
        }

        public Builder optimizationConfig(OptimizationConfig optimizationConfig) {
            this.optimizationConfig = optimizationConfig;
            return this;
        }

        public Builder evolutionConfig(EvolutionConfig evolutionConfig) {
            this.evolutionConfig = evolutionConfig;
            return this;
        }

        public LearningEngineConfig build() {
            return new LearningEngineConfig(this);
        }
    }

    // Component configuration classes
    public static class SequenceAnalyzerConfig {
        private final int maxSequenceLength;
        private final long maxTimeGap;
        private final double minSequenceSupport;

        private SequenceAnalyzerConfig(Builder builder) {
            this.maxSequenceLength = builder.maxSequenceLength;
            this.maxTimeGap = builder.maxTimeGap;
            this.minSequenceSupport = builder.minSequenceSupport;
        }

        public int getMaxSequenceLength() { return maxSequenceLength; }
        public long getMaxTimeGap() { return maxTimeGap; }
        public double getMinSequenceSupport() { return minSequenceSupport; }

        public static Builder builder() {
            return new Builder();
        }

        public static SequenceAnalyzerConfig defaultConfig() {
            return builder().build();
        }

        public static class Builder {
            private int maxSequenceLength = 10;
            private long maxTimeGap = 300000; // 5 minutes
            private double minSequenceSupport = 0.1;

            public Builder maxSequenceLength(int maxSequenceLength) {
                this.maxSequenceLength = maxSequenceLength;
                return this;
            }

            public Builder maxTimeGap(long maxTimeGap) {
                this.maxTimeGap = maxTimeGap;
                return this;
            }

            public Builder minSequenceSupport(double minSequenceSupport) {
                this.minSequenceSupport = minSequenceSupport;
                return this;
            }

            public SequenceAnalyzerConfig build() {
                return new SequenceAnalyzerConfig(this);
            }
        }
    }

    public static class CorrelationDetectorConfig {
        private final double minCorrelationStrength;
        private final int minCooccurrenceCount;
        private final long correlationTimeWindow;

        private CorrelationDetectorConfig(Builder builder) {
            this.minCorrelationStrength = builder.minCorrelationStrength;
            this.minCooccurrenceCount = builder.minCooccurrenceCount;
            this.correlationTimeWindow = builder.correlationTimeWindow;
        }

        public double getMinCorrelationStrength() { return minCorrelationStrength; }
        public int getMinCooccurrenceCount() { return minCooccurrenceCount; }
        public long getCorrelationTimeWindow() { return correlationTimeWindow; }

        public static Builder builder() {
            return new Builder();
        }

        public static CorrelationDetectorConfig defaultConfig() {
            return builder().build();
        }

        public static class Builder {
            private double minCorrelationStrength = 0.3;
            private int minCooccurrenceCount = 3;
            private long correlationTimeWindow = 600000; // 10 minutes

            public Builder minCorrelationStrength(double minCorrelationStrength) {
                this.minCorrelationStrength = minCorrelationStrength;
                return this;
            }

            public Builder minCooccurrenceCount(int minCooccurrenceCount) {
                this.minCooccurrenceCount = minCooccurrenceCount;
                return this;
            }

            public Builder correlationTimeWindow(long correlationTimeWindow) {
                this.correlationTimeWindow = correlationTimeWindow;
                return this;
            }

            public CorrelationDetectorConfig build() {
                return new CorrelationDetectorConfig(this);
            }
        }
    }

    public static class AnomalyDetectorConfig {
        private final double anomalyThreshold;
        private final int minAnomalySamples;
        private final double statisticalSignificance;

        private AnomalyDetectorConfig(Builder builder) {
            this.anomalyThreshold = builder.anomalyThreshold;
            this.minAnomalySamples = builder.minAnomalySamples;
            this.statisticalSignificance = builder.statisticalSignificance;
        }

        public double getAnomalyThreshold() { return anomalyThreshold; }
        public int getMinAnomalySamples() { return minAnomalySamples; }
        public double getStatisticalSignificance() { return statisticalSignificance; }

        public static Builder builder() {
            return new Builder();
        }

        public static AnomalyDetectorConfig defaultConfig() {
            return builder().build();
        }

        public static class Builder {
            private double anomalyThreshold = 0.95;
            private int minAnomalySamples = 10;
            private double statisticalSignificance = 0.05;

            public Builder anomalyThreshold(double anomalyThreshold) {
                this.anomalyThreshold = anomalyThreshold;
                return this;
            }

            public Builder minAnomalySamples(int minAnomalySamples) {
                this.minAnomalySamples = minAnomalySamples;
                return this;
            }

            public Builder statisticalSignificance(double statisticalSignificance) {
                this.statisticalSignificance = statisticalSignificance;
                return this;
            }

            public AnomalyDetectorConfig build() {
                return new AnomalyDetectorConfig(this);
            }
        }
    }

    public static class OptimizationConfig {
        private final double optimizationRate;
        private final int maxOptimizationAttempts;
        private final double performanceImprovementThreshold;

        private OptimizationConfig(Builder builder) {
            this.optimizationRate = builder.optimizationRate;
            this.maxOptimizationAttempts = builder.maxOptimizationAttempts;
            this.performanceImprovementThreshold = builder.performanceImprovementThreshold;
        }

        public double getOptimizationRate() { return optimizationRate; }
        public int getMaxOptimizationAttempts() { return maxOptimizationAttempts; }
        public double getPerformanceImprovementThreshold() { return performanceImprovementThreshold; }

        public static Builder builder() {
            return new Builder();
        }

        public static OptimizationConfig defaultConfig() {
            return builder().build();
        }

        public static class Builder {
            private double optimizationRate = 0.1;
            private int maxOptimizationAttempts = 3;
            private double performanceImprovementThreshold = 0.05;

            public Builder optimizationRate(double optimizationRate) {
                this.optimizationRate = optimizationRate;
                return this;
            }

            public Builder maxOptimizationAttempts(int maxOptimizationAttempts) {
                this.maxOptimizationAttempts = maxOptimizationAttempts;
                return this;
            }

            public Builder performanceImprovementThreshold(double performanceImprovementThreshold) {
                this.performanceImprovementThreshold = performanceImprovementThreshold;
                return this;
            }

            public OptimizationConfig build() {
                return new OptimizationConfig(this);
            }
        }
    }

    public static class EvolutionConfig {
        private final double mutationRate;
        private final double crossoverRate;
        private final int populationSize;
        private final int maxGenerations;

        private EvolutionConfig(Builder builder) {
            this.mutationRate = builder.mutationRate;
            this.crossoverRate = builder.crossoverRate;
            this.populationSize = builder.populationSize;
            this.maxGenerations = builder.maxGenerations;
        }

        public double getMutationRate() { return mutationRate; }
        public double getCrossoverRate() { return crossoverRate; }
        public int getPopulationSize() { return populationSize; }
        public int getMaxGenerations() { return maxGenerations; }

        public static Builder builder() {
            return new Builder();
        }

        public static EvolutionConfig defaultConfig() {
            return builder().build();
        }

        public static class Builder {
            private double mutationRate = 0.1;
            private double crossoverRate = 0.7;
            private int populationSize = 50;
            private int maxGenerations = 100;

            public Builder mutationRate(double mutationRate) {
                this.mutationRate = mutationRate;
                return this;
            }

            public Builder crossoverRate(double crossoverRate) {
                this.crossoverRate = crossoverRate;
                return this;
            }

            public Builder populationSize(int populationSize) {
                this.populationSize = populationSize;
                return this;
            }

            public Builder maxGenerations(int maxGenerations) {
                this.maxGenerations = maxGenerations;
                return this;
            }

            public EvolutionConfig build() {
                return new EvolutionConfig(this);
            }
        }
    }
}
