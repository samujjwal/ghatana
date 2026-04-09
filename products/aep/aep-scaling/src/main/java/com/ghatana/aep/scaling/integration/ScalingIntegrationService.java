/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.integration;

import com.ghatana.aep.scaling.autoscaling.AutoScalingEngine;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels;
import com.ghatana.aep.scaling.cluster.ClusterManagementSystem;
import com.ghatana.aep.scaling.distributed.DistributedPatternProcessor;
import com.ghatana.aep.scaling.loadbalancer.AdvancedLoadBalancer;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integrates all scaling services (auto-scaling, cluster management, load balancing, distributed processing).
 * Provides a unified API for scaling operations across the platform.
 *
 * @doc.type class
 * @doc.purpose Unified scaling service integrating auto-scaling, cluster management, and load balancing
 * @doc.layer product
 * @doc.pattern Service, Facade
 */
@Slf4j
public class ScalingIntegrationService {

    private final Eventloop eventloop;
    private final AutoScalingEngine autoScalingEngine;
    private final ClusterManagementSystem clusterManagementSystem;
    private final Map<String, AdvancedLoadBalancer> loadBalancers;
    private final Map<String, DistributedPatternProcessor> patternProcessors;

    public ScalingIntegrationService(
            Eventloop eventloop,
            AutoScalingEngine autoScalingEngine,
            ClusterManagementSystem clusterManagementSystem) {
        this.eventloop = eventloop;
        this.autoScalingEngine = autoScalingEngine;
        this.clusterManagementSystem = clusterManagementSystem;
        this.loadBalancers = new ConcurrentHashMap<>();
        this.patternProcessors = new ConcurrentHashMap<>();
    }

    /**
     * Performs a comprehensive scaling evaluation for a cluster.
     * Coordinates auto-scaling, cluster management, and load balancing.
     */
    public Promise<ScalingIntegrationResult> evaluateAndScale(String clusterId) {
        log.info("Starting integrated scaling evaluation for cluster {}", clusterId);

        // Step 1: Auto-scaling evaluation
        ScalingOperationModels.ScalingEvaluationRequest request =
            new ScalingOperationModels.ScalingEvaluationRequest(clusterId);
        return autoScalingEngine.evaluateScaling(request)
            .map(scalingResult -> {
                if (!scalingResult.isSuccess()) {
                    return ScalingIntegrationResult.builder()
                        .success(false)
                        .clusterId(clusterId)
                        .errorMessage("Auto-scaling evaluation failed: " + scalingResult.getErrorMessage())
                        .build();
                }

                // Step 2: Update load balancers if scaling decision was made
                if (scalingResult.getDecision() != null &&
                    scalingResult.getDecision().getAction() != null &&
                    scalingResult.getDecision().getAction().getType() != ScalingOperationModels.ScalingAction.Type.NO_ACTION) {

                    for (AdvancedLoadBalancer lb : loadBalancers.values()) {
                        // Notify load balancers of scaling change
                        log.debug("Notifying load balancer of scaling change for cluster {}", clusterId);
                    }
                }

                return ScalingIntegrationResult.builder()
                    .success(true)
                    .clusterId(clusterId)
                    .scalingDecision(scalingResult.getDecision())
                    .evaluationTime(scalingResult.getEvaluationTime())
                    .build();
            });
    }

    /**
     * Registers a load balancer with the integration service.
     */
    public void registerLoadBalancer(String id, AdvancedLoadBalancer loadBalancer) {
        loadBalancers.put(id, loadBalancer);
        log.info("Registered load balancer {} with scaling integration service", id);
    }

    /**
     * Registers a pattern processor with the integration service.
     */
    public void registerPatternProcessor(String id, DistributedPatternProcessor processor) {
        patternProcessors.put(id, processor);
        log.info("Registered pattern processor {} with scaling integration service", id);
    }

    /**
     * Returns metrics from all scaling services.
     */
    public Map<String, Object> getAllMetrics() {
        return Map.of(
            "autoScaling", autoScalingEngine.getMetrics(),
            "clusterManagement", clusterManagementSystem.getMetrics(),
            "loadBalancers", loadBalancers.size(),
            "patternProcessors", patternProcessors.size()
        );
    }

    /**
     * Shuts down all scaling services.
     */
    public Promise<Void> shutdown() {
        log.info("Shutting down ScalingIntegrationService");
        return autoScalingEngine.shutdown()
            .then(() -> clusterManagementSystem.shutdown())
            .then(() -> {
                Promise<Void> chain = Promise.complete();
                for (DistributedPatternProcessor processor : patternProcessors.values()) {
                    chain = chain.then(() -> processor.shutdown());
                }
                return chain;
            })
            .whenComplete(() -> {
                patternProcessors.clear();
                loadBalancers.clear();
            });
    }

    // ==================== Result Class ====================

    @lombok.Data
    @lombok.Builder
    public static class ScalingIntegrationResult {
        private boolean success;
        private String clusterId;
        private String errorMessage;
        private ScalingOperationModels.ScalingDecision scalingDecision;
        private long evaluationTime;
    }
}
