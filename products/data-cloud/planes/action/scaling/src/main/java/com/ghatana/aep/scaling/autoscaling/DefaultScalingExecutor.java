/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingAction;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingDecision;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingExecutor;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default scaling executor that logs scaling actions and tracks state.
 * In production, this would delegate to a container orchestrator (K8s, ECS, etc.).
 *
 * @doc.type class
 * @doc.purpose Executes and validates scaling actions
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultScalingExecutor implements ScalingExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultScalingExecutor.class);

    private final Set<String> activeClusters = ConcurrentHashMap.newKeySet();

    @Override
    public Promise<Void> executeScaling(ScalingDecision decision) {
        String clusterId = decision.getClusterId();
        ScalingAction action = decision.getAction();

        if (action == null || action.getType() == ScalingAction.Type.NO_ACTION) {
            log.debug("No scaling action required for cluster={}", clusterId);
            return Promise.complete();
        }

        if (!activeClusters.add(clusterId)) {
            log.warn("Scaling already in progress for cluster={}, skipping", clusterId);
            return Promise.complete();
        }

        log.info("Executing scaling: cluster={}, action={}, nodes={}",
            clusterId, action.getType(), action.getNodeCount());

        try {
            // In production, this would call K8s API, cloud provider APIs, etc.
            return Promise.complete();
        } finally {
            activeClusters.remove(clusterId);
        }
    }

    @Override
    public Promise<Boolean> validateScalingAction(ScalingAction action) {
        if (action == null) {
            return Promise.of(false);
        }
        if (action.getNodeCount() < 0) {
            log.warn("Invalid node count: {}", action.getNodeCount());
            return Promise.of(false);
        }
        if (action.getType() == null) {
            return Promise.of(false);
        }
        return Promise.of(Boolean.TRUE);
    }

    @Override
    public Promise<Void> rollbackScaling(String clusterId) {
        log.info("Rolling back scaling for cluster={}", clusterId);
        activeClusters.remove(clusterId);
        return Promise.complete();
    }
}
