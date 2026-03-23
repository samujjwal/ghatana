/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Auto-scaling engine that evaluates cluster metrics and makes scaling decisions.
 */
@Slf4j
public class AutoScalingEngine {

    private final Eventloop eventloop;
    private final MetricsCollector metricsCollector;
    private final ScalingPolicyManager policyManager;
    private final ScalingExecutor scalingExecutor;
    private final PredictiveScaler predictiveScaler;
    private final CostOptimizer costOptimizer;

    private final AtomicLong totalScalingEvents;
    private final AtomicLong successfulScalingEvents;
    private final Map<String, ScalingEvent> activeScalingStates;

    public AutoScalingEngine(
            MetricsCollector metricsCollector,
            ScalingPolicyManager policyManager,
            ScalingExecutor scalingExecutor,
            PredictiveScaler predictiveScaler,
            CostOptimizer costOptimizer,
            Eventloop eventloop) {
        this.metricsCollector = metricsCollector;
        this.policyManager = policyManager;
        this.scalingExecutor = scalingExecutor;
        this.predictiveScaler = predictiveScaler;
        this.costOptimizer = costOptimizer;
        this.eventloop = eventloop;
        this.totalScalingEvents = new AtomicLong(0);
        this.successfulScalingEvents = new AtomicLong(0);
        this.activeScalingStates = new ConcurrentHashMap<>();
    }

    /**
     * Evaluates scaling needs for a cluster and returns a decision.
     */
    public Promise<ScalingEvaluationResult> evaluateScaling(ScalingEvaluationRequest request) {
        long startTime = System.currentTimeMillis();
        String clusterId = request.getClusterId();

        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Collect metrics
                ClusterMetrics metrics = metricsCollector.collectClusterMetrics(clusterId);
                if (metrics == null) {
                    return ScalingEvaluationResult.builder()
                        .success(false)
                        .errorMessage("Failed to collect metrics for cluster: " + clusterId)
                        .evaluationTime(System.currentTimeMillis() - startTime)
                        .build();
                }

                // Get applicable policies
                List<ScalingPolicy> policies = policyManager.getApplicablePolicies(clusterId);

                // Get predictive recommendation
                PredictiveScalingRecommendation prediction = predictiveScaler.getRecommendation(clusterId, metrics);

                // Determine scaling action
                ScalingAction action = determineScalingAction(metrics, policies, prediction);
                ScalingDecision decision = new ScalingDecision(clusterId, action);

                // Optimize for cost
                CostOptimizationResult costResult = costOptimizer.optimizeScalingDecision(decision, metrics, policies);

                totalScalingEvents.incrementAndGet();

                return ScalingEvaluationResult.builder()
                    .success(true)
                    .decision(decision)
                    .evaluationTime(System.currentTimeMillis() - startTime)
                    .appliedPolicies(policies.stream().map(ScalingPolicy::getPolicyId).toList())
                    .prediction(prediction)
                    .costOptimization(costResult)
                    .build();

            } catch (Exception e) {
                log.error("Error evaluating scaling for cluster {}", clusterId, e);
                return ScalingEvaluationResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .evaluationTime(System.currentTimeMillis() - startTime)
                    .build();
            }
        });
    }

    /**
     * Returns current metrics for the auto-scaling engine.
     */
    public Map<String, Object> getMetrics() {
        long total = totalScalingEvents.get();
        long successful = successfulScalingEvents.get();
        double successRate = total > 0 ? (double) successful / total : 0.0;

        return Map.of(
            "totalScalingEvents", total,
            "successfulScalingEvents", successful,
            "successRate", successRate,
            "activeScalingStates", activeScalingStates.size()
        );
    }

    /**
     * Shuts down the auto-scaling engine.
     */
    public Promise<Void> shutdown() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Shutting down AutoScalingEngine");
            activeScalingStates.clear();
            return null;
        });
    }

    // ==================== Private Helpers ====================

    private ScalingAction determineScalingAction(ClusterMetrics metrics, List<ScalingPolicy> policies, PredictiveScalingRecommendation prediction) {
        // Check predictive recommendation first
        if (prediction != null && prediction.getConfidence() > 0.8) {
            return new ScalingAction(
                prediction.getRecommendedAction(),
                prediction.getRecommendedNodeCount(),
                "Predictive scaling: " + prediction.getReason()
            );
        }

        // Check policies
        for (ScalingPolicy policy : policies) {
            if (!policy.isEnabled()) continue;

            if (metrics.getCpuUtilization() > policy.getScaleUpThreshold()) {
                return new ScalingAction(
                    ScalingAction.Type.SCALE_UP,
                    policy.getScaleUpStep(),
                    "CPU utilization above threshold: " + metrics.getCpuUtilization()
                );
            }

            if (metrics.getCpuUtilization() < policy.getScaleDownThreshold() && metrics.getActiveNodes() > policy.getMinNodes()) {
                return new ScalingAction(
                    ScalingAction.Type.SCALE_DOWN,
                    policy.getScaleDownStep(),
                    "CPU utilization below threshold: " + metrics.getCpuUtilization()
                );
            }
        }

        return new ScalingAction(ScalingAction.Type.NO_ACTION, 0, "No scaling needed");
    }
}
