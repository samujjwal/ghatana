/*
 * Copyright (c) 2026 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.scaling;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for plugin auto-scaling in Data-Cloud.
 * 
 * <p>Enables dynamic scaling of plugin instances based on load metrics,
 * with support for both horizontal (more instances) and vertical (more resources) scaling.
 *
 * @doc.type interface
 * @doc.purpose Plugin auto-scaling abstraction for Data-Cloud
 * @doc.layer core
 * @doc.pattern Strategy
 */
public interface AutoScaler {

    /**
     * Scaling direction.
     */
    enum ScaleDirection {
        UP, DOWN, NONE
    }

    /**
     * Scaling type.
     */
    enum ScaleType {
        HORIZONTAL,  // Add/remove instances
        VERTICAL     // Change resource allocation
    }

    /**
     * Scaling policy that determines when and how to scale.
     */
    record ScalingPolicy(
        String policyId,
        String pluginId,
        ScaleType scaleType,
        ScalingTrigger trigger,
        ScalingLimits limits,
        Duration cooldownPeriod,
        Map<String, String> metadata
    ) {}

    /**
     * Trigger conditions for scaling.
     */
    sealed interface ScalingTrigger {
        /**
         * Scale based on a metric threshold.
         */
        record MetricTrigger(
            String metricName,
            double scaleUpThreshold,
            double scaleDownThreshold,
            Duration evaluationPeriod,
            int dataPointsRequired
        ) implements ScalingTrigger {}

        /**
         * Scale based on a schedule.
         */
        record ScheduleTrigger(
            String cronExpression,
            int targetInstances
        ) implements ScalingTrigger {}

        /**
         * Scale based on queue depth.
         */
        record QueueTrigger(
            String queueName,
            int scaleUpThreshold,
            int scaleDownThreshold
        ) implements ScalingTrigger {}

        /**
         * Predictive scaling based on historical patterns.
         */
        record PredictiveTrigger(
            Duration lookAheadPeriod,
            double confidence
        ) implements ScalingTrigger {}
    }

    /**
     * Limits for scaling operations.
     */
    record ScalingLimits(
        int minInstances,
        int maxInstances,
        int scaleUpStep,
        int scaleDownStep,
        ResourceLimits resourceLimits
    ) {
        public static ScalingLimits defaults() {
            return new ScalingLimits(1, 10, 1, 1, ResourceLimits.defaults());
        }
    }

    /**
     * Resource limits for vertical scaling.
     */
    record ResourceLimits(
        long minMemoryMb,
        long maxMemoryMb,
        double minCpu,
        double maxCpu,
        int minThreads,
        int maxThreads
    ) {
        public static ResourceLimits defaults() {
            return new ResourceLimits(256, 4096, 0.25, 4.0, 1, 16);
        }
    }

    /**
     * Current scaling state for a plugin.
     */
    record ScalingState(
        String pluginId,
        int currentInstances,
        int desiredInstances,
        ResourceAllocation currentResources,
        Instant lastScaleTime,
        ScaleDirection lastDirection,
        boolean inCooldown
    ) {}

    /**
     * Resource allocation for a plugin instance.
     */
    record ResourceAllocation(
        long memoryMb,
        double cpuCores,
        int threads
    ) {}

    /**
     * Scaling event for history and audit.
     */
    record ScalingEvent(
        String eventId,
        String pluginId,
        Instant timestamp,
        ScaleDirection direction,
        ScaleType type,
        int previousInstances,
        int newInstances,
        ResourceAllocation previousResources,
        ResourceAllocation newResources,
        String reason,
        boolean success,
        String errorMessage
    ) {}

    /**
     * Scaling recommendation from the auto-scaler.
     */
    record ScalingRecommendation(
        String pluginId,
        ScaleDirection direction,
        ScaleType type,
        int targetInstances,
        ResourceAllocation targetResources,
        double confidence,
        String reason,
        List<MetricSnapshot> supportingMetrics
    ) {}

    /**
     * Snapshot of a metric value.
     */
    record MetricSnapshot(
        String metricName,
        double value,
        Instant timestamp
    ) {}

    // ==================== Policy Management ====================

    /**
     * Registers a scaling policy.
     *
     * @param policy the scaling policy
     * @return promise completed when registered
     */
    Promise<Void> registerPolicy(ScalingPolicy policy);

    /**
     * Unregisters a scaling policy.
     *
     * @param policyId the policy ID
     * @return promise completed when unregistered
     */
    Promise<Void> unregisterPolicy(String policyId);

    /**
     * Gets a scaling policy by ID.
     *
     * @param policyId the policy ID
     * @return promise of the policy, or empty if not found
     */
    Promise<ScalingPolicy> getPolicy(String policyId);

    /**
     * Gets all policies for a plugin.
     *
     * @param pluginId the plugin ID
     * @return promise of policies
     */
    Promise<List<ScalingPolicy>> getPoliciesForPlugin(String pluginId);

    /**
     * Gets all registered policies.
     *
     * @return promise of all policies
     */
    Promise<List<ScalingPolicy>> getAllPolicies();

    // ==================== Scaling Operations ====================

    /**
     * Evaluates whether scaling is needed for a plugin.
     *
     * @param pluginId the plugin to evaluate
     * @return promise of scaling recommendation
     */
    Promise<ScalingRecommendation> evaluate(String pluginId);

    /**
     * Executes a scaling operation.
     *
     * @param recommendation the scaling recommendation to execute
     * @return promise of scaling event result
     */
    Promise<ScalingEvent> scale(ScalingRecommendation recommendation);

    /**
     * Manually scales a plugin to specific instance count.
     *
     * @param pluginId the plugin to scale
     * @param targetInstances target instance count
     * @param reason reason for manual scaling
     * @return promise of scaling event
     */
    Promise<ScalingEvent> manualScale(String pluginId, int targetInstances, String reason);

    /**
     * Manually adjusts resources for a plugin.
     *
     * @param pluginId the plugin to adjust
     * @param targetResources target resource allocation
     * @param reason reason for adjustment
     * @return promise of scaling event
     */
    Promise<ScalingEvent> adjustResources(
        String pluginId, 
        ResourceAllocation targetResources, 
        String reason
    );

    // ==================== State & Monitoring ====================

    /**
     * Gets current scaling state for a plugin.
     *
     * @param pluginId the plugin ID
     * @return promise of scaling state
     */
    Promise<ScalingState> getState(String pluginId);

    /**
     * Gets scaling states for all plugins.
     *
     * @return promise of map of plugin ID to state
     */
    Promise<Map<String, ScalingState>> getAllStates();

    /**
     * Gets scaling history for a plugin.
     *
     * @param pluginId the plugin ID
     * @param since only include events after this time
     * @param limit maximum number of events
     * @return promise of scaling events
     */
    Promise<List<ScalingEvent>> getHistory(String pluginId, Instant since, int limit);

    // ==================== Hooks ====================

    /**
     * Hook interface for scaling lifecycle events.
     */
    interface ScalingHook {
        /**
         * Called before scaling begins.
         *
         * @param recommendation the proposed scaling
         * @return promise of whether to proceed
         */
        Promise<Boolean> beforeScale(ScalingRecommendation recommendation);

        /**
         * Called after scaling completes.
         *
         * @param event the scaling event
         */
        void afterScale(ScalingEvent event);

        /**
         * Called when scaling fails.
         *
         * @param recommendation the failed recommendation
         * @param error the error
         */
        void onScaleFailure(ScalingRecommendation recommendation, Throwable error);
    }

    /**
     * Registers a scaling hook.
     *
     * @param hookId unique ID for the hook
     * @param hook the hook implementation
     */
    void registerHook(String hookId, ScalingHook hook);

    /**
     * Unregisters a scaling hook.
     *
     * @param hookId the hook ID
     */
    void unregisterHook(String hookId);

    /**
     * Gets all registered hook IDs.
     *
     * @return set of hook IDs
     */
    Set<String> getRegisteredHooks();

    // ==================== Lifecycle ====================

    /**
     * Starts the auto-scaler.
     *
     * @return promise completed when started
     */
    Promise<Void> start();

    /**
     * Stops the auto-scaler.
     *
     * @return promise completed when stopped
     */
    Promise<Void> stop();

    /**
     * Checks if auto-scaler is running.
     *
     * @return true if running
     */
    boolean isRunning();

    /**
     * Pauses auto-scaling (manual scaling still allowed).
     */
    void pause();

    /**
     * Resumes auto-scaling.
     */
    void resume();

    /**
     * Checks if auto-scaling is paused.
     *
     * @return true if paused
     */
    boolean isPaused();
}
