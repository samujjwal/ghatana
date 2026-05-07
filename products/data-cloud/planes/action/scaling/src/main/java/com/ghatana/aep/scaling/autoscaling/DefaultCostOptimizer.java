/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.CostOptimizationResult;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.CostOptimizer;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingAction;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingDecision;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicy;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default cost optimizer that applies policy constraints to scaling decisions
 * and estimates costs using a simple per-node cost model.
 *
 * @doc.type class
 * @doc.purpose Optimizes scaling decisions for cost efficiency
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultCostOptimizer implements CostOptimizer {

    private static final Logger log = LoggerFactory.getLogger(DefaultCostOptimizer.class);

    /** Default hourly cost per node (USD). Override via region cost map. */
    private static final double DEFAULT_NODE_COST_PER_HOUR = 0.10;

    private final ConcurrentHashMap<String, Double> regionCostMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> costMetricsStore = new ConcurrentHashMap<>();

    @Override
    public CostOptimizationResult optimizeScalingDecision(ScalingDecision decision,
                                                           ClusterMetrics metrics,
                                                           List<ScalingPolicy> policies) {
        ScalingAction action = decision.getAction();
        if (action == null || action.getType() == ScalingAction.Type.NO_ACTION) {
            return new CostOptimizationResult(
                action, 0.0, List.of("No scaling action — zero cost"),
                (long) metrics.getActiveNodes(), false, Map.of());
        }

        List<String> notes = new ArrayList<>();
        ScalingAction optimized = action;
        boolean applied = false;

        // Enforce policy bounds
        for (ScalingPolicy policy : policies) {
            if (!policy.isEnabled()) continue;
            int targetCount = action.getNodeCount();
            if (targetCount > policy.getMaxNodes()) {
                optimized = new ScalingAction(action.getType(), policy.getMaxNodes(),
                    action.getReason() + " (capped by policy " + policy.getPolicyId() + ")");
                notes.add("Capped to maxNodes=" + policy.getMaxNodes()
                    + " by policy " + policy.getPolicyId());
                applied = true;
            }
            if (targetCount < policy.getMinNodes()) {
                optimized = new ScalingAction(action.getType(), policy.getMinNodes(),
                    action.getReason() + " (raised to min by policy " + policy.getPolicyId() + ")");
                notes.add("Raised to minNodes=" + policy.getMinNodes()
                    + " by policy " + policy.getPolicyId());
                applied = true;
            }
        }

        double cost = estimateScalingCost(optimized, metrics.getClusterId());
        notes.add(String.format("Estimated hourly cost: $%.4f", cost));

        return new CostOptimizationResult(
            optimized, cost, notes, (long) metrics.getActiveNodes(), applied, Map.of(
                "originalNodeCount", action.getNodeCount(),
                "optimizedNodeCount", optimized.getNodeCount(),
                "costPerHour", cost
            ));
    }

    @Override
    public double estimateScalingCost(ScalingAction action, String region) {
        double costPerNode = regionCostMap.getOrDefault(region, DEFAULT_NODE_COST_PER_HOUR);
        return action.getNodeCount() * costPerNode;
    }

    @Override
    public Map<String, Object> getCostMetrics(String clusterId) {
        return costMetricsStore.getOrDefault(clusterId, Map.of(
            "clusterId", clusterId,
            "currentCostPerHour", 0.0,
            "estimatedMonthlyCost", 0.0
        ));
    }

    /**
     * Sets the hourly cost per node for a specific region.
     */
    public void setRegionCost(String region, double costPerHour) {
        regionCostMap.put(region, costPerHour);
    }
}
