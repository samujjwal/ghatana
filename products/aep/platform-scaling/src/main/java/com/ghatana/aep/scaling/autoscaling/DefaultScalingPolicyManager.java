/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicy;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory scaling policy manager that stores and retrieves scaling policies.
 *
 * @doc.type class
 * @doc.purpose Manages scaling policies for auto-scaling decisions
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultScalingPolicyManager implements ScalingPolicyManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultScalingPolicyManager.class);

    private final ConcurrentHashMap<String, ScalingPolicy> policies = new ConcurrentHashMap<>();

    @Override
    public List<ScalingPolicy> getApplicablePolicies(String clusterId) {
        return policies.values().stream()
            .filter(ScalingPolicy::isEnabled)
            .filter(p -> p.getApplicableClusters() == null
                || p.getApplicableClusters().isEmpty()
                || p.getApplicableClusters().contains(clusterId))
            .toList();
    }

    @Override
    public ScalingPolicy getPolicy(String policyId) {
        return policies.get(policyId);
    }

    @Override
    public void addPolicy(ScalingPolicy policy) {
        if (policy == null || policy.getPolicyId() == null) {
            throw new IllegalArgumentException("Policy and policyId must not be null");
        }
        log.info("Adding scaling policy: id={}, name={}", policy.getPolicyId(), policy.getName());
        policies.put(policy.getPolicyId(), policy);
    }

    @Override
    public void removePolicy(String policyId) {
        ScalingPolicy removed = policies.remove(policyId);
        if (removed != null) {
            log.info("Removed scaling policy: id={}", policyId);
        }
    }

    @Override
    public List<ScalingPolicy> getAllPolicies() {
        return List.copyOf(policies.values());
    }
}
