/*
 * Copyright (c) 2026 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.scaling;

import com.ghatana.datacloud.observability.DataCloudMetrics;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plugin auto-scaler implementation for Data-Cloud.
 * 
 * <p>Provides automatic scaling of plugin instances based on load metrics,
 * queue depths, and predictive algorithms. Supports both horizontal scaling
 * (adding/removing instances) and vertical scaling (adjusting resources).
 *
 * <p>Key features:
 * <ul>
 *   <li>Metric-based scaling triggers (CPU, memory, custom metrics)</li>
 *   <li>Queue-based scaling for streaming plugins</li>
 *   <li>Predictive scaling based on historical patterns</li>
 *   <li>Cooldown periods to prevent oscillation</li>
 *   <li>Scaling hooks for custom pre/post actions</li>
 *   <li>Manual override capabilities</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Plugin auto-scaling for Data-Cloud
 * @doc.layer core
 * @doc.pattern Observer, Strategy
 */
public class PluginAutoScaler implements AutoScaler {

    private static final Logger logger = LoggerFactory.getLogger(PluginAutoScaler.class);

    // Dependencies
    private final DataCloudMetrics metrics;
    private final PluginScalingExecutor executor;
    private final MeterRegistry meterRegistry;
    private final ExecutorService evaluationExecutor;
    private final ScheduledExecutorService scheduler;

    // Configuration
    private final AutoScalerConfig config;

    // State
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    // Policies
    private final ConcurrentMap<String, ScalingPolicy> policies = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<String>> pluginPolicies = new ConcurrentHashMap<>();

    // Current state per plugin
    private final ConcurrentMap<String, ScalingState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> cooldowns = new ConcurrentHashMap<>();

    // History
    private final ConcurrentMap<String, ConcurrentLinkedQueue<ScalingEvent>> history = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PER_PLUGIN = 100;

    // Hooks
    private final ConcurrentMap<String, ScalingHook> hooks = new ConcurrentHashMap<>();

    // Metrics tracking for evaluation
    private final ConcurrentMap<String, ConcurrentLinkedQueue<MetricSnapshot>> metricHistory = new ConcurrentHashMap<>();
    private static final int MAX_METRIC_SAMPLES = 60; // 60 samples for evaluation

    // Metrics
    private final Counter scaleUpCounter;
    private final Counter scaleDownCounter;
    private final Counter scaleFailureCounter;

    /**
     * Configuration for the auto-scaler.
     */
    public record AutoScalerConfig(
        Duration evaluationInterval,
        Duration defaultCooldown,
        int maxConcurrentScaling,
        boolean enablePredictiveScaling,
        double scalingAggressiveness
    ) {
        public static AutoScalerConfig defaults() {
            return new AutoScalerConfig(
                Duration.ofSeconds(30),  // evaluationInterval
                Duration.ofMinutes(5),   // defaultCooldown
                3,                       // maxConcurrentScaling
                false,                   // enablePredictiveScaling
                1.0                      // scalingAggressiveness
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Duration evaluationInterval = Duration.ofSeconds(30);
            private Duration defaultCooldown = Duration.ofMinutes(5);
            private int maxConcurrentScaling = 3;
            private boolean enablePredictiveScaling = false;
            private double scalingAggressiveness = 1.0;

            public Builder evaluationInterval(Duration interval) {
                this.evaluationInterval = interval;
                return this;
            }

            public Builder defaultCooldown(Duration cooldown) {
                this.defaultCooldown = cooldown;
                return this;
            }

            public Builder maxConcurrentScaling(int max) {
                this.maxConcurrentScaling = max;
                return this;
            }

            public Builder enablePredictiveScaling(boolean enable) {
                this.enablePredictiveScaling = enable;
                return this;
            }

            public Builder scalingAggressiveness(double aggressiveness) {
                this.scalingAggressiveness = aggressiveness;
                return this;
            }

            public AutoScalerConfig build() {
                return new AutoScalerConfig(
                    evaluationInterval, defaultCooldown, maxConcurrentScaling,
                    enablePredictiveScaling, scalingAggressiveness
                );
            }
        }
    }

    /**
     * Interface for executing actual scaling operations.
     */
    public interface PluginScalingExecutor {
        /**
         * Scales plugin to target instance count.
         *
         * @param pluginId the plugin to scale
         * @param targetInstances target instance count
         * @return promise of actual instance count after scaling
         */
        Promise<Integer> scaleInstances(String pluginId, int targetInstances);

        /**
         * Adjusts resources for a plugin.
         *
         * @param pluginId the plugin
         * @param allocation target resource allocation
         * @return promise completed when adjusted
         */
        Promise<Void> adjustResources(String pluginId, ResourceAllocation allocation);

        /**
         * Gets current instance count for a plugin.
         *
         * @param pluginId the plugin
         * @return current instance count
         */
        int getCurrentInstances(String pluginId);

        /**
         * Gets current resource allocation for a plugin.
         *
         * @param pluginId the plugin
         * @return current resource allocation
         */
        ResourceAllocation getCurrentResources(String pluginId);

        /**
         * Gets all managed plugins.
         *
         * @return set of plugin IDs
         */
        Set<String> getManagedPlugins();
    }

    /**
     * Creates a new PluginAutoScaler.
     *
     * @param metrics Data-Cloud metrics collector
     * @param executor scaling executor
     * @param meterRegistry Micrometer registry
     * @param config auto-scaler configuration
     */
    public PluginAutoScaler(
            DataCloudMetrics metrics,
            PluginScalingExecutor executor,
            MeterRegistry meterRegistry,
            AutoScalerConfig config) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.config = Objects.requireNonNull(config, "config");

        this.evaluationExecutor = Executors.newFixedThreadPool(
            config.maxConcurrentScaling(),
            r -> {
                Thread t = new Thread(r, "auto-scaler-eval");
                t.setDaemon(true);
                return t;
            }
        );
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auto-scaler-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Initialize metrics
        this.scaleUpCounter = Counter.builder("datacloud.autoscaler.scaleup")
            .description("Scale up operations")
            .register(meterRegistry);
        this.scaleDownCounter = Counter.builder("datacloud.autoscaler.scaledown")
            .description("Scale down operations")
            .register(meterRegistry);
        this.scaleFailureCounter = Counter.builder("datacloud.autoscaler.failures")
            .description("Failed scaling operations")
            .register(meterRegistry);

        // Register gauges
        Gauge.builder("datacloud.autoscaler.policies", policies, ConcurrentMap::size)
            .description("Number of registered scaling policies")
            .register(meterRegistry);

        logger.info("PluginAutoScaler initialized with config: evaluationInterval={}, defaultCooldown={}",
            config.evaluationInterval(), config.defaultCooldown());
    }

    // ==================== Policy Management ====================

    @Override
    public Promise<Void> registerPolicy(ScalingPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        
        policies.put(policy.policyId(), policy);
        pluginPolicies.computeIfAbsent(policy.pluginId(), k -> new CopyOnWriteArrayList<>())
            .add(policy.policyId());

        logger.info("Registered scaling policy: {} for plugin {}", policy.policyId(), policy.pluginId());
        return Promise.complete();
    }

    @Override
    public Promise<Void> unregisterPolicy(String policyId) {
        ScalingPolicy removed = policies.remove(policyId);
        if (removed != null) {
            List<String> pluginPolicyList = pluginPolicies.get(removed.pluginId());
            if (pluginPolicyList != null) {
                pluginPolicyList.remove(policyId);
            }
            logger.info("Unregistered scaling policy: {}", policyId);
        }
        return Promise.complete();
    }

    @Override
    public Promise<ScalingPolicy> getPolicy(String policyId) {
        return Promise.of(policies.get(policyId));
    }

    @Override
    public Promise<List<ScalingPolicy>> getPoliciesForPlugin(String pluginId) {
        List<String> policyIds = pluginPolicies.getOrDefault(pluginId, List.of());
        List<ScalingPolicy> result = policyIds.stream()
            .map(policies::get)
            .filter(Objects::nonNull)
            .toList();
        return Promise.of(result);
    }

    @Override
    public Promise<List<ScalingPolicy>> getAllPolicies() {
        return Promise.of(new ArrayList<>(policies.values()));
    }

    // ==================== Scaling Operations ====================

    @Override
    public Promise<ScalingRecommendation> evaluate(String pluginId) {
        return Promise.ofBlocking(evaluationExecutor, () -> {
            List<String> policyIds = pluginPolicies.getOrDefault(pluginId, List.of());
            if (policyIds.isEmpty()) {
                return new ScalingRecommendation(
                    pluginId, ScaleDirection.NONE, ScaleType.HORIZONTAL,
                    executor.getCurrentInstances(pluginId),
                    executor.getCurrentResources(pluginId),
                    0.0, "No policies configured", List.of()
                );
            }

            // Check cooldown
            Instant cooldownEnd = cooldowns.get(pluginId);
            if (cooldownEnd != null && Instant.now().isBefore(cooldownEnd)) {
                return new ScalingRecommendation(
                    pluginId, ScaleDirection.NONE, ScaleType.HORIZONTAL,
                    executor.getCurrentInstances(pluginId),
                    executor.getCurrentResources(pluginId),
                    0.0, "In cooldown until " + cooldownEnd, List.of()
                );
            }

            // Evaluate each policy
            List<ScalingRecommendation> recommendations = new ArrayList<>();
            for (String policyId : policyIds) {
                ScalingPolicy policy = policies.get(policyId);
                if (policy != null) {
                    ScalingRecommendation rec = evaluatePolicy(pluginId, policy);
                    if (rec.direction() != ScaleDirection.NONE) {
                        recommendations.add(rec);
                    }
                }
            }

            // Aggregate recommendations (prioritize scale-up)
            if (recommendations.isEmpty()) {
                return new ScalingRecommendation(
                    pluginId, ScaleDirection.NONE, ScaleType.HORIZONTAL,
                    executor.getCurrentInstances(pluginId),
                    executor.getCurrentResources(pluginId),
                    1.0, "No scaling needed", List.of()
                );
            }

            // Prefer scale-up recommendations
            Optional<ScalingRecommendation> scaleUp = recommendations.stream()
                .filter(r -> r.direction() == ScaleDirection.UP)
                .max(Comparator.comparingDouble(ScalingRecommendation::confidence));

            if (scaleUp.isPresent()) {
                return scaleUp.get();
            }

            // Otherwise return highest confidence scale-down
            return recommendations.stream()
                .max(Comparator.comparingDouble(ScalingRecommendation::confidence))
                .orElse(recommendations.get(0));
        });
    }

    private ScalingRecommendation evaluatePolicy(String pluginId, ScalingPolicy policy) {
        int currentInstances = executor.getCurrentInstances(pluginId);
        ResourceAllocation currentResources = executor.getCurrentResources(pluginId);

        return switch (policy.trigger()) {
            case ScalingTrigger.MetricTrigger mt -> evaluateMetricTrigger(
                pluginId, mt, policy, currentInstances, currentResources
            );
            case ScalingTrigger.QueueTrigger qt -> evaluateQueueTrigger(
                pluginId, qt, policy, currentInstances, currentResources
            );
            case ScalingTrigger.ScheduleTrigger st -> evaluateScheduleTrigger(
                pluginId, st, policy, currentInstances, currentResources
            );
            case ScalingTrigger.PredictiveTrigger pt -> evaluatePredictiveTrigger(
                pluginId, pt, policy, currentInstances, currentResources
            );
        };
    }

    private ScalingRecommendation evaluateMetricTrigger(
            String pluginId,
            ScalingTrigger.MetricTrigger trigger,
            ScalingPolicy policy,
            int currentInstances,
            ResourceAllocation currentResources) {
        
        // Get metric samples
        ConcurrentLinkedQueue<MetricSnapshot> samples = metricHistory.computeIfAbsent(
            pluginId + ":" + trigger.metricName(),
            k -> new ConcurrentLinkedQueue<>()
        );

        // Check if we have enough data points
        List<MetricSnapshot> recentSamples = samples.stream()
            .filter(s -> s.timestamp().isAfter(Instant.now().minus(trigger.evaluationPeriod())))
            .toList();

        if (recentSamples.size() < trigger.dataPointsRequired()) {
            return new ScalingRecommendation(
                pluginId, ScaleDirection.NONE, policy.scaleType(),
                currentInstances, currentResources,
                0.0, "Insufficient data points", List.of()
            );
        }

        // Calculate average metric value
        double avgValue = recentSamples.stream()
            .mapToDouble(MetricSnapshot::value)
            .average()
            .orElse(0.0);

        // Determine scaling direction
        ScaleDirection direction;
        int targetInstances;
        String reason;

        if (avgValue > trigger.scaleUpThreshold()) {
            direction = ScaleDirection.UP;
            targetInstances = Math.min(
                currentInstances + policy.limits().scaleUpStep(),
                policy.limits().maxInstances()
            );
            reason = String.format("Metric %s (%.2f) > threshold (%.2f)",
                trigger.metricName(), avgValue, trigger.scaleUpThreshold());
        } else if (avgValue < trigger.scaleDownThreshold()) {
            direction = ScaleDirection.DOWN;
            targetInstances = Math.max(
                currentInstances - policy.limits().scaleDownStep(),
                policy.limits().minInstances()
            );
            reason = String.format("Metric %s (%.2f) < threshold (%.2f)",
                trigger.metricName(), avgValue, trigger.scaleDownThreshold());
        } else {
            return new ScalingRecommendation(
                pluginId, ScaleDirection.NONE, policy.scaleType(),
                currentInstances, currentResources,
                1.0, "Metric within thresholds", recentSamples
            );
        }

        // Calculate confidence based on how far we are from threshold
        double confidence = direction == ScaleDirection.UP
            ? Math.min(1.0, (avgValue - trigger.scaleUpThreshold()) / trigger.scaleUpThreshold() + 0.5)
            : Math.min(1.0, (trigger.scaleDownThreshold() - avgValue) / trigger.scaleDownThreshold() + 0.5);

        return new ScalingRecommendation(
            pluginId, direction, policy.scaleType(),
            targetInstances, currentResources,
            confidence, reason, recentSamples
        );
    }

    private ScalingRecommendation evaluateQueueTrigger(
            String pluginId,
            ScalingTrigger.QueueTrigger trigger,
            ScalingPolicy policy,
            int currentInstances,
            ResourceAllocation currentResources) {
        
        // Get queue depth from metrics
        double queueDepth = getQueueDepth(pluginId, trigger.queueName());

        ScaleDirection direction;
        int targetInstances;
        String reason;

        if (queueDepth > trigger.scaleUpThreshold()) {
            direction = ScaleDirection.UP;
            // Scale proportionally to queue depth
            int additionalInstances = (int) Math.ceil(
                (queueDepth - trigger.scaleUpThreshold()) / (double) trigger.scaleUpThreshold()
                * config.scalingAggressiveness()
            );
            targetInstances = Math.min(
                currentInstances + Math.max(policy.limits().scaleUpStep(), additionalInstances),
                policy.limits().maxInstances()
            );
            reason = String.format("Queue depth (%.0f) > threshold (%d)",
                queueDepth, trigger.scaleUpThreshold());
        } else if (queueDepth < trigger.scaleDownThreshold() && currentInstances > policy.limits().minInstances()) {
            direction = ScaleDirection.DOWN;
            targetInstances = Math.max(
                currentInstances - policy.limits().scaleDownStep(),
                policy.limits().minInstances()
            );
            reason = String.format("Queue depth (%.0f) < threshold (%d)",
                queueDepth, trigger.scaleDownThreshold());
        } else {
            return new ScalingRecommendation(
                pluginId, ScaleDirection.NONE, policy.scaleType(),
                currentInstances, currentResources,
                1.0, "Queue within thresholds", List.of()
            );
        }

        double confidence = direction == ScaleDirection.UP
            ? Math.min(1.0, queueDepth / (trigger.scaleUpThreshold() * 2))
            : Math.min(1.0, 1 - queueDepth / trigger.scaleDownThreshold());

        return new ScalingRecommendation(
            pluginId, direction, policy.scaleType(),
            targetInstances, currentResources,
            confidence, reason,
            List.of(new MetricSnapshot("queue.depth", queueDepth, Instant.now()))
        );
    }

    private ScalingRecommendation evaluateScheduleTrigger(
            String pluginId,
            ScalingTrigger.ScheduleTrigger trigger,
            ScalingPolicy policy,
            int currentInstances,
            ResourceAllocation currentResources) {
        
        // Simple schedule evaluation - in production, use cron parser
        // For now, just check if current instances match target
        if (currentInstances == trigger.targetInstances()) {
            return new ScalingRecommendation(
                pluginId, ScaleDirection.NONE, policy.scaleType(),
                currentInstances, currentResources,
                1.0, "Already at scheduled target", List.of()
            );
        }

        ScaleDirection direction = trigger.targetInstances() > currentInstances
            ? ScaleDirection.UP : ScaleDirection.DOWN;

        return new ScalingRecommendation(
            pluginId, direction, policy.scaleType(),
            trigger.targetInstances(), currentResources,
            1.0, "Scheduled scaling to " + trigger.targetInstances(), List.of()
        );
    }

    private ScalingRecommendation evaluatePredictiveTrigger(
            String pluginId,
            ScalingTrigger.PredictiveTrigger trigger,
            ScalingPolicy policy,
            int currentInstances,
            ResourceAllocation currentResources) {
        
        if (!config.enablePredictiveScaling()) {
            return new ScalingRecommendation(
                pluginId, ScaleDirection.NONE, policy.scaleType(),
                currentInstances, currentResources,
                0.0, "Predictive scaling disabled", List.of()
            );
        }

        // Simple predictive model based on trend analysis
        // In production, use ML models or time-series forecasting
        List<MetricSnapshot> loadHistory = metricHistory.getOrDefault(
            pluginId + ":load",
            new ConcurrentLinkedQueue<>()
        ).stream().toList();

        if (loadHistory.size() < 10) {
            return new ScalingRecommendation(
                pluginId, ScaleDirection.NONE, policy.scaleType(),
                currentInstances, currentResources,
                0.0, "Insufficient history for prediction", List.of()
            );
        }

        // Calculate trend
        double[] values = loadHistory.stream()
            .mapToDouble(MetricSnapshot::value)
            .toArray();
        double trend = calculateTrend(values);

        // Predict future load
        double currentLoad = values[values.length - 1];
        double predictedLoad = currentLoad + trend * trigger.lookAheadPeriod().toMinutes();

        if (predictedLoad > currentInstances * 0.8 && trend > 0) {
            int targetInstances = Math.min(
                (int) Math.ceil(predictedLoad / 0.7),
                policy.limits().maxInstances()
            );
            return new ScalingRecommendation(
                pluginId, ScaleDirection.UP, policy.scaleType(),
                targetInstances, currentResources,
                trigger.confidence(),
                String.format("Predicted load %.1f in %s", predictedLoad, trigger.lookAheadPeriod()),
                loadHistory
            );
        }

        return new ScalingRecommendation(
            pluginId, ScaleDirection.NONE, policy.scaleType(),
            currentInstances, currentResources,
            1.0, "No scaling predicted", loadHistory
        );
    }

    private double calculateTrend(double[] values) {
        // Simple linear regression slope
        int n = values.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumX2 += i * i;
        }
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    private double getQueueDepth(String pluginId, String queueName) {
        // Get from metrics - in production, query actual queue
        ConcurrentLinkedQueue<MetricSnapshot> samples = metricHistory.get(pluginId + ":" + queueName);
        if (samples == null || samples.isEmpty()) {
            return 0.0;
        }
        return samples.stream()
            .max(Comparator.comparing(MetricSnapshot::timestamp))
            .map(MetricSnapshot::value)
            .orElse(0.0);
    }

    @Override
    public Promise<ScalingEvent> scale(ScalingRecommendation recommendation) {
        if (recommendation.direction() == ScaleDirection.NONE) {
            return Promise.of(new ScalingEvent(
                UUID.randomUUID().toString(),
                recommendation.pluginId(),
                Instant.now(),
                ScaleDirection.NONE,
                recommendation.type(),
                recommendation.targetInstances(),
                recommendation.targetInstances(),
                recommendation.targetResources(),
                recommendation.targetResources(),
                recommendation.reason(),
                true,
                null
            ));
        }

        return Promise.ofBlocking(evaluationExecutor, () -> {
            String pluginId = recommendation.pluginId();
            int previousInstances = executor.getCurrentInstances(pluginId);
            ResourceAllocation previousResources = executor.getCurrentResources(pluginId);

            // Call pre-scaling hooks
            for (ScalingHook hook : hooks.values()) {
                try {
                    Boolean proceed = hook.beforeScale(recommendation).getResult();
                    if (!Boolean.TRUE.equals(proceed)) {
                        return createEvent(
                            pluginId, ScaleDirection.NONE, recommendation.type(),
                            previousInstances, previousInstances,
                            previousResources, previousResources,
                            "Blocked by hook", false, "Hook vetoed scaling"
                        );
                    }
                } catch (Exception e) {
                    logger.warn("Hook failed for plugin {}", pluginId, e);
                }
            }

            try {
                int newInstances;
                ResourceAllocation newResources;

                if (recommendation.type() == ScaleType.HORIZONTAL) {
                    newInstances = executor.scaleInstances(pluginId, recommendation.targetInstances()).getResult();
                    newResources = previousResources;
                } else {
                    executor.adjustResources(pluginId, recommendation.targetResources()).getResult();
                    newInstances = previousInstances;
                    newResources = recommendation.targetResources();
                }

                // Update metrics
                if (recommendation.direction() == ScaleDirection.UP) {
                    scaleUpCounter.increment();
                } else {
                    scaleDownCounter.increment();
                }

                // Set cooldown
                ScalingPolicy policy = policies.values().stream()
                    .filter(p -> p.pluginId().equals(pluginId))
                    .findFirst()
                    .orElse(null);
                Duration cooldown = policy != null ? policy.cooldownPeriod() : config.defaultCooldown();
                cooldowns.put(pluginId, Instant.now().plus(cooldown));

                // Update state
                states.put(pluginId, new ScalingState(
                    pluginId,
                    newInstances,
                    newInstances,
                    newResources,
                    Instant.now(),
                    recommendation.direction(),
                    true
                ));

                ScalingEvent event = createEvent(
                    pluginId, recommendation.direction(), recommendation.type(),
                    previousInstances, newInstances,
                    previousResources, newResources,
                    recommendation.reason(), true, null
                );

                // Record history
                recordHistory(pluginId, event);

                // Call post-scaling hooks
                for (ScalingHook hook : hooks.values()) {
                    try {
                        hook.afterScale(event);
                    } catch (Exception e) {
                        logger.warn("Post-scale hook failed for plugin {}", pluginId, e);
                    }
                }

                logger.info("Scaled plugin {} from {} to {} instances ({})",
                    pluginId, previousInstances, newInstances, recommendation.reason());

                return event;

            } catch (Exception e) {
                scaleFailureCounter.increment();
                
                ScalingEvent failedEvent = createEvent(
                    pluginId, recommendation.direction(), recommendation.type(),
                    previousInstances, previousInstances,
                    previousResources, previousResources,
                    recommendation.reason(), false, e.getMessage()
                );

                recordHistory(pluginId, failedEvent);

                // Call failure hooks
                for (ScalingHook hook : hooks.values()) {
                    try {
                        hook.onScaleFailure(recommendation, e);
                    } catch (Exception he) {
                        logger.warn("Failure hook failed for plugin {}", pluginId, he);
                    }
                }

                logger.error("Failed to scale plugin {}", pluginId, e);
                return failedEvent;
            }
        });
    }

    @Override
    public Promise<ScalingEvent> manualScale(String pluginId, int targetInstances, String reason) {
        ScalingPolicy anyPolicy = policies.values().stream()
            .filter(p -> p.pluginId().equals(pluginId))
            .findFirst()
            .orElse(null);

        ScalingLimits limits = anyPolicy != null ? anyPolicy.limits() : ScalingLimits.defaults();
        
        int clampedTarget = Math.max(limits.minInstances(), Math.min(targetInstances, limits.maxInstances()));

        int currentInstances = executor.getCurrentInstances(pluginId);
        ScaleDirection direction = clampedTarget > currentInstances ? ScaleDirection.UP :
            clampedTarget < currentInstances ? ScaleDirection.DOWN : ScaleDirection.NONE;

        ScalingRecommendation recommendation = new ScalingRecommendation(
            pluginId, direction, ScaleType.HORIZONTAL,
            clampedTarget, executor.getCurrentResources(pluginId),
            1.0, "Manual: " + reason, List.of()
        );

        return scale(recommendation);
    }

    @Override
    public Promise<ScalingEvent> adjustResources(
            String pluginId, 
            ResourceAllocation targetResources, 
            String reason) {
        
        ResourceAllocation currentResources = executor.getCurrentResources(pluginId);
        
        ScaleDirection direction;
        if (targetResources.memoryMb() > currentResources.memoryMb() ||
            targetResources.cpuCores() > currentResources.cpuCores()) {
            direction = ScaleDirection.UP;
        } else if (targetResources.memoryMb() < currentResources.memoryMb() ||
                   targetResources.cpuCores() < currentResources.cpuCores()) {
            direction = ScaleDirection.DOWN;
        } else {
            direction = ScaleDirection.NONE;
        }

        ScalingRecommendation recommendation = new ScalingRecommendation(
            pluginId, direction, ScaleType.VERTICAL,
            executor.getCurrentInstances(pluginId), targetResources,
            1.0, "Manual resource adjustment: " + reason, List.of()
        );

        return scale(recommendation);
    }

    private ScalingEvent createEvent(
            String pluginId,
            ScaleDirection direction,
            ScaleType type,
            int previousInstances,
            int newInstances,
            ResourceAllocation previousResources,
            ResourceAllocation newResources,
            String reason,
            boolean success,
            String errorMessage) {
        return new ScalingEvent(
            UUID.randomUUID().toString(),
            pluginId,
            Instant.now(),
            direction,
            type,
            previousInstances,
            newInstances,
            previousResources,
            newResources,
            reason,
            success,
            errorMessage
        );
    }

    private void recordHistory(String pluginId, ScalingEvent event) {
        ConcurrentLinkedQueue<ScalingEvent> pluginHistory = 
            history.computeIfAbsent(pluginId, k -> new ConcurrentLinkedQueue<>());
        pluginHistory.offer(event);
        while (pluginHistory.size() > MAX_HISTORY_PER_PLUGIN) {
            pluginHistory.poll();
        }
    }

    // ==================== State & Monitoring ====================

    @Override
    public Promise<ScalingState> getState(String pluginId) {
        ScalingState state = states.get(pluginId);
        if (state == null) {
            int currentInstances = executor.getCurrentInstances(pluginId);
            ResourceAllocation currentResources = executor.getCurrentResources(pluginId);
            Instant cooldownEnd = cooldowns.get(pluginId);
            boolean inCooldown = cooldownEnd != null && Instant.now().isBefore(cooldownEnd);
            
            state = new ScalingState(
                pluginId,
                currentInstances,
                currentInstances,
                currentResources,
                null,
                ScaleDirection.NONE,
                inCooldown
            );
        }
        return Promise.of(state);
    }

    @Override
    public Promise<Map<String, ScalingState>> getAllStates() {
        return Promise.ofBlocking(evaluationExecutor, () -> {
            Map<String, ScalingState> allStates = new HashMap<>(states);
            
            // Add states for plugins without recorded state
            for (String pluginId : executor.getManagedPlugins()) {
                if (!allStates.containsKey(pluginId)) {
                    allStates.put(pluginId, getState(pluginId).getResult());
                }
            }
            
            return allStates;
        });
    }

    @Override
    public Promise<List<ScalingEvent>> getHistory(String pluginId, Instant since, int limit) {
        ConcurrentLinkedQueue<ScalingEvent> pluginHistory = history.get(pluginId);
        if (pluginHistory == null) {
            return Promise.of(List.of());
        }

        List<ScalingEvent> filtered = pluginHistory.stream()
            .filter(e -> since == null || e.timestamp().isAfter(since))
            .sorted(Comparator.comparing(ScalingEvent::timestamp).reversed())
            .limit(limit)
            .toList();

        return Promise.of(filtered);
    }

    // ==================== Hooks ====================

    @Override
    public void registerHook(String hookId, ScalingHook hook) {
        hooks.put(hookId, hook);
        logger.info("Registered scaling hook: {}", hookId);
    }

    @Override
    public void unregisterHook(String hookId) {
        hooks.remove(hookId);
        logger.info("Unregistered scaling hook: {}", hookId);
    }

    @Override
    public Set<String> getRegisteredHooks() {
        return new HashSet<>(hooks.keySet());
    }

    // ==================== Lifecycle ====================

    @Override
    public Promise<Void> start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting PluginAutoScaler...");

            // Schedule periodic evaluation
            scheduler.scheduleAtFixedRate(
                this::runEvaluation,
                config.evaluationInterval().toMillis(),
                config.evaluationInterval().toMillis(),
                TimeUnit.MILLISECONDS
            );

            // Schedule metric collection
            scheduler.scheduleAtFixedRate(
                this::collectMetrics,
                1000,
                1000,
                TimeUnit.MILLISECONDS
            );

            logger.info("PluginAutoScaler started");
        }
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping PluginAutoScaler...");

            scheduler.shutdown();
            evaluationExecutor.shutdown();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                if (!evaluationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    evaluationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                evaluationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            logger.info("PluginAutoScaler stopped");
        }
        return Promise.complete();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void pause() {
        if (paused.compareAndSet(false, true)) {
            logger.info("PluginAutoScaler paused");
        }
    }

    @Override
    public void resume() {
        if (paused.compareAndSet(true, false)) {
            logger.info("PluginAutoScaler resumed");
        }
    }

    @Override
    public boolean isPaused() {
        return paused.get();
    }

    private void runEvaluation() {
        if (!running.get() || paused.get()) {
            return;
        }

        logger.debug("Running auto-scaling evaluation...");
        
        for (String pluginId : executor.getManagedPlugins()) {
            try {
                ScalingRecommendation recommendation = evaluate(pluginId).getResult();
                if (recommendation.direction() != ScaleDirection.NONE) {
                    logger.info("Scaling recommendation for {}: {} to {} instances",
                        pluginId, recommendation.direction(), recommendation.targetInstances());
                    scale(recommendation);
                }
            } catch (Exception e) {
                logger.error("Error evaluating plugin {}", pluginId, e);
            }
        }
    }

    private void collectMetrics() {
        if (!running.get()) {
            return;
        }

        // Collect metrics from DataCloudMetrics and store for evaluation
        for (String pluginId : executor.getManagedPlugins()) {
            try {
                // This would integrate with DataCloudMetrics to get actual values
                // For now, we'll simulate metric collection
                collectPluginMetrics(pluginId);
            } catch (Exception e) {
                logger.debug("Error collecting metrics for plugin {}", pluginId, e);
            }
        }
    }

    private void collectPluginMetrics(String pluginId) {
        // Store CPU metric
        recordMetric(pluginId, "cpu", Math.random() * 100);
        // Store memory metric
        recordMetric(pluginId, "memory", Math.random() * 100);
        // Store load metric
        recordMetric(pluginId, "load", Math.random() * 10);
    }

    /**
     * Records a metric value for evaluation.
     *
     * @param pluginId the plugin ID
     * @param metricName the metric name
     * @param value the metric value
     */
    public void recordMetric(String pluginId, String metricName, double value) {
        String key = pluginId + ":" + metricName;
        ConcurrentLinkedQueue<MetricSnapshot> samples = metricHistory.computeIfAbsent(
            key, k -> new ConcurrentLinkedQueue<>()
        );
        samples.offer(new MetricSnapshot(metricName, value, Instant.now()));
        while (samples.size() > MAX_METRIC_SAMPLES) {
            samples.poll();
        }
    }
}
