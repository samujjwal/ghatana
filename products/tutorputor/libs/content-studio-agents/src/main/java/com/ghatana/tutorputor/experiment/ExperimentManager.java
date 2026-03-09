package com.ghatana.tutorputor.experiment;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A/B testing experiment manager for content generation strategies.
 * 
 * <p>Features:
 * <ul>
 *   <li>Deterministic variant assignment based on user ID</li>
 *   <li>Multi-variant experiments (A/B/C/N)</li>
 *   <li>Traffic allocation and ramping</li>
 *   <li>Experiment lifecycle management</li>
 *   <li>Real-time metrics collection</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose A/B testing for content strategies
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class ExperimentManager {

    private static final Logger LOG = LoggerFactory.getLogger(ExperimentManager.class);

    private final ConcurrentMap<String, Experiment> experiments;
    private final ConcurrentMap<String, Map<String, String>> userAssignments;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter assignmentsCounter;
    private final Counter exposuresCounter;
    private final Counter conversionsCounter;

    /**
     * Creates a new experiment manager.
     *
     * @param meterRegistry the metrics registry
     */
    public ExperimentManager(@NotNull MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.experiments = new ConcurrentHashMap<>();
        this.userAssignments = new ConcurrentHashMap<>();
        
        this.assignmentsCounter = Counter.builder("tutorputor.experiments.assignments")
            .description("Number of experiment assignments")
            .register(meterRegistry);
        this.exposuresCounter = Counter.builder("tutorputor.experiments.exposures")
            .description("Number of experiment exposures")
            .register(meterRegistry);
        this.conversionsCounter = Counter.builder("tutorputor.experiments.conversions")
            .description("Number of experiment conversions")
            .register(meterRegistry);
        
        LOG.info("ExperimentManager initialized");
    }

    /**
     * Creates a new experiment.
     *
     * @param experiment the experiment configuration
     */
    public void createExperiment(@NotNull Experiment experiment) {
        if (experiments.containsKey(experiment.id())) {
            throw new IllegalArgumentException("Experiment already exists: " + experiment.id());
        }
        
        validateExperiment(experiment);
        experiments.put(experiment.id(), experiment);
        
        LOG.info("Created experiment: {} with {} variants", 
            experiment.id(), experiment.variants().size());
    }

    /**
     * Gets an experiment by ID.
     *
     * @param experimentId the experiment ID
     * @return the experiment, or null if not found
     */
    @Nullable
    public Experiment getExperiment(@NotNull String experimentId) {
        return experiments.get(experimentId);
    }

    /**
     * Gets the variant for a user in an experiment.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @return the assigned variant, or null if not in experiment
     */
    @Nullable
    public Variant getVariant(@NotNull String experimentId, @NotNull String userId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null || !experiment.isActive()) {
            return null;
        }
        
        // Check traffic allocation
        if (!isInTraffic(experimentId, userId, experiment.trafficAllocation())) {
            return null;
        }
        
        // Get or create assignment
        String variantId = getOrCreateAssignment(experimentId, userId, experiment);
        
        return experiment.variants().stream()
            .filter(v -> v.id().equals(variantId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Records an exposure event.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param variantId the variant ID
     */
    public void recordExposure(
            @NotNull String experimentId, 
            @NotNull String userId, 
            @NotNull String variantId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) return;
        
        experiment.recordExposure(variantId);
        exposuresCounter.increment();
        
        Counter.builder("tutorputor.experiments.exposures.variant")
            .tag("experiment", experimentId)
            .tag("variant", variantId)
            .register(meterRegistry)
            .increment();
        
        LOG.debug("Recorded exposure: experiment={}, user={}, variant={}", 
            experimentId, userId, variantId);
    }

    /**
     * Records a conversion event.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @param metric the conversion metric name
     * @param value the metric value
     */
    public void recordConversion(
            @NotNull String experimentId,
            @NotNull String userId,
            @NotNull String metric,
            double value) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) return;
        
        String variantId = userAssignments
            .getOrDefault(userId, Map.of())
            .get(experimentId);
        
        if (variantId == null) return;
        
        experiment.recordConversion(variantId, metric, value);
        conversionsCounter.increment();
        
        Counter.builder("tutorputor.experiments.conversions.variant")
            .tag("experiment", experimentId)
            .tag("variant", variantId)
            .tag("metric", metric)
            .register(meterRegistry)
            .increment();
        
        LOG.debug("Recorded conversion: experiment={}, user={}, variant={}, metric={}, value={}", 
            experimentId, userId, variantId, metric, value);
    }

    /**
     * Gets experiment results.
     *
     * @param experimentId the experiment ID
     * @return experiment results
     */
    public ExperimentResults getResults(@NotNull String experimentId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null) {
            return null;
        }
        return experiment.getResults();
    }

    /**
     * Stops an experiment.
     *
     * @param experimentId the experiment ID
     */
    public void stopExperiment(@NotNull String experimentId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment != null) {
            experiment.stop();
            LOG.info("Stopped experiment: {}", experimentId);
        }
    }

    /**
     * Lists all experiments.
     *
     * @return list of experiments
     */
    public List<Experiment> listExperiments() {
        return new ArrayList<>(experiments.values());
    }

    /**
     * Lists active experiments.
     *
     * @return list of active experiments
     */
    public List<Experiment> listActiveExperiments() {
        return experiments.values().stream()
            .filter(Experiment::isActive)
            .toList();
    }

    private void validateExperiment(Experiment experiment) {
        if (experiment.variants().isEmpty()) {
            throw new IllegalArgumentException("Experiment must have at least one variant");
        }
        
        double totalWeight = experiment.variants().stream()
            .mapToDouble(Variant::weight)
            .sum();
        
        if (Math.abs(totalWeight - 100.0) > 0.01) {
            throw new IllegalArgumentException(
                "Variant weights must sum to 100, got: " + totalWeight);
        }
        
        if (experiment.trafficAllocation() <= 0 || experiment.trafficAllocation() > 100) {
            throw new IllegalArgumentException(
                "Traffic allocation must be between 0 and 100");
        }
    }

    private boolean isInTraffic(String experimentId, String userId, double trafficPercent) {
        if (trafficPercent >= 100) return true;
        
        int hash = Math.abs(hashString(experimentId + ":traffic:" + userId));
        return (hash % 100) < trafficPercent;
    }

    private String getOrCreateAssignment(
            String experimentId, 
            String userId, 
            Experiment experiment) {
        
        Map<String, String> userExperiments = userAssignments
            .computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        
        return userExperiments.computeIfAbsent(experimentId, k -> {
            String variantId = assignVariant(experimentId, userId, experiment.variants());
            assignmentsCounter.increment();
            LOG.debug("Assigned user {} to variant {} in experiment {}", 
                userId, variantId, experimentId);
            return variantId;
        });
    }

    private String assignVariant(String experimentId, String userId, List<Variant> variants) {
        // Deterministic assignment based on user ID hash
        int hash = Math.abs(hashString(experimentId + ":" + userId));
        int bucket = hash % 100;
        
        double cumulative = 0;
        for (Variant variant : variants) {
            cumulative += variant.weight();
            if (bucket < cumulative) {
                return variant.id();
            }
        }
        
        // Fallback to last variant
        return variants.get(variants.size() - 1).id();
    }

    private int hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return ((digest[0] & 0xFF) << 24) | 
                   ((digest[1] & 0xFF) << 16) | 
                   ((digest[2] & 0xFF) << 8) | 
                   (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            return input.hashCode();
        }
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Experiment variant.
     */
    public record Variant(
        String id,
        String name,
        String description,
        double weight,
        Map<String, Object> config
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String description = "";
            private double weight = 50.0;
            private Map<String, Object> config = Map.of();

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder weight(double weight) { this.weight = weight; return this; }
            public Builder config(Map<String, Object> config) { this.config = config; return this; }

            public Variant build() {
                return new Variant(id, name, description, weight, config);
            }
        }
    }

    /**
     * Experiment configuration.
     */
    public static class Experiment {
        private final String id;
        private final String name;
        private final String description;
        private final List<Variant> variants;
        private final double trafficAllocation;
        private final Instant startTime;
        private final Instant endTime;
        private final Map<String, String> metadata;
        
        private volatile boolean active = true;
        private final ConcurrentMap<String, Long> exposureCounts;
        private final ConcurrentMap<String, Map<String, List<Double>>> conversionData;

        private Experiment(Builder builder) {
            this.id = builder.id;
            this.name = builder.name;
            this.description = builder.description;
            this.variants = List.copyOf(builder.variants);
            this.trafficAllocation = builder.trafficAllocation;
            this.startTime = builder.startTime;
            this.endTime = builder.endTime;
            this.metadata = Map.copyOf(builder.metadata);
            this.exposureCounts = new ConcurrentHashMap<>();
            this.conversionData = new ConcurrentHashMap<>();
        }

        public String id() { return id; }
        public String name() { return name; }
        public String description() { return description; }
        public List<Variant> variants() { return variants; }
        public double trafficAllocation() { return trafficAllocation; }
        public Instant startTime() { return startTime; }
        public Instant endTime() { return endTime; }
        public Map<String, String> metadata() { return metadata; }

        public boolean isActive() {
            if (!active) return false;
            Instant now = Instant.now();
            if (startTime != null && now.isBefore(startTime)) return false;
            if (endTime != null && now.isAfter(endTime)) return false;
            return true;
        }

        public void stop() {
            this.active = false;
        }

        void recordExposure(String variantId) {
            exposureCounts.merge(variantId, 1L, Long::sum);
        }

        void recordConversion(String variantId, String metric, double value) {
            conversionData
                .computeIfAbsent(variantId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(metric, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(value);
        }

        ExperimentResults getResults() {
            Map<String, VariantResults> variantResults = new HashMap<>();
            
            for (Variant variant : variants) {
                long exposures = exposureCounts.getOrDefault(variant.id(), 0L);
                Map<String, List<Double>> conversions = 
                    conversionData.getOrDefault(variant.id(), Map.of());
                
                Map<String, MetricStats> metricStats = new HashMap<>();
                for (Map.Entry<String, List<Double>> entry : conversions.entrySet()) {
                    List<Double> values = entry.getValue();
                    if (!values.isEmpty()) {
                        metricStats.put(entry.getKey(), calculateStats(values));
                    }
                }
                
                variantResults.put(variant.id(), 
                    new VariantResults(variant, exposures, metricStats));
            }
            
            return new ExperimentResults(this, variantResults);
        }

        private MetricStats calculateStats(List<Double> values) {
            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            
            for (double v : values) {
                sum += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            
            double mean = sum / values.size();
            
            double variance = 0;
            for (double v : values) {
                variance += (v - mean) * (v - mean);
            }
            variance /= values.size();
            
            return new MetricStats(
                values.size(),
                mean,
                Math.sqrt(variance),
                min,
                max,
                sum
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String description = "";
            private List<Variant> variants = new ArrayList<>();
            private double trafficAllocation = 100.0;
            private Instant startTime;
            private Instant endTime;
            private Map<String, String> metadata = new HashMap<>();

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder variants(List<Variant> variants) { this.variants = variants; return this; }
            public Builder addVariant(Variant variant) { this.variants.add(variant); return this; }
            public Builder trafficAllocation(double percent) { this.trafficAllocation = percent; return this; }
            public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
            public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
            public Builder duration(Duration duration) { 
                this.endTime = (startTime != null ? startTime : Instant.now()).plus(duration);
                return this; 
            }
            public Builder metadata(String key, String value) { 
                this.metadata.put(key, value); 
                return this; 
            }

            public Experiment build() {
                if (id == null) throw new IllegalStateException("ID is required");
                if (name == null) throw new IllegalStateException("Name is required");
                if (variants.isEmpty()) throw new IllegalStateException("At least one variant required");
                return new Experiment(this);
            }
        }
    }

    /**
     * Metric statistics.
     */
    public record MetricStats(
        int count,
        double mean,
        double stdDev,
        double min,
        double max,
        double sum
    ) {}

    /**
     * Results for a single variant.
     */
    public record VariantResults(
        Variant variant,
        long exposures,
        Map<String, MetricStats> metrics
    ) {}

    /**
     * Complete experiment results.
     */
    public record ExperimentResults(
        Experiment experiment,
        Map<String, VariantResults> variantResults
    ) {
        /**
         * Calculates statistical significance between control and treatment.
         *
         * @param metric the metric to compare
         * @param controlVariantId the control variant ID
         * @param treatmentVariantId the treatment variant ID
         * @return p-value (lower = more significant)
         */
        public double calculateSignificance(
                String metric, 
                String controlVariantId, 
                String treatmentVariantId) {
            VariantResults control = variantResults.get(controlVariantId);
            VariantResults treatment = variantResults.get(treatmentVariantId);
            
            if (control == null || treatment == null) return 1.0;
            
            MetricStats controlStats = control.metrics().get(metric);
            MetricStats treatmentStats = treatment.metrics().get(metric);
            
            if (controlStats == null || treatmentStats == null) return 1.0;
            
            // Two-sample t-test (approximation)
            double meanDiff = treatmentStats.mean() - controlStats.mean();
            double pooledVariance = 
                (controlStats.stdDev() * controlStats.stdDev() / controlStats.count()) +
                (treatmentStats.stdDev() * treatmentStats.stdDev() / treatmentStats.count());
            
            if (pooledVariance <= 0) return 1.0;
            
            double tStatistic = meanDiff / Math.sqrt(pooledVariance);
            
            // Approximate p-value using normal distribution
            return 2.0 * (1.0 - normalCdf(Math.abs(tStatistic)));
        }

        private double normalCdf(double x) {
            // Approximation of standard normal CDF
            double t = 1.0 / (1.0 + 0.2316419 * x);
            double d = 0.3989423 * Math.exp(-x * x / 2.0);
            double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
            return 1.0 - p;
        }
    }
}
