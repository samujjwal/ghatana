/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * WS4-12: Canonical governance policy primitive.
 *
 * <p>Defines the base contract for all governance policies in the Data Cloud.
 * This is the source of truth for governance policy evaluation and is owned
 * by the governance/core module.
 *
 * @doc.type interface
 * @doc.purpose Canonical governance policy primitive for Data Cloud
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface GovernancePolicy {

    /**
     * Gets the unique policy identifier.
     *
     * @return policy ID
     */
    String policyId();

    /**
     * Gets the policy category.
     *
     * @return policy category
     */
    PolicyCategory category();

    /**
     * Gets the policy version.
     *
     * @return policy version
     */
    String version();

    /**
     * Gets the policy description.
     *
     * @return policy description
     */
    String description();

    /**
     * Evaluates the policy against the provided context.
     *
     * @param context evaluation context
     * @return policy evaluation result
     */
    PolicyEvaluationResult evaluate(EvaluationContext context);

    /**
     * Checks if the policy is enabled.
     *
     * @return true if enabled
     */
    boolean isEnabled();

    /**
     * Gets the policy severity level.
     *
     * @return severity level
     */
    SeverityLevel severity();

    // ==================== Supporting Types ====================

    /**
     * Policy evaluation result.
     */
    record PolicyEvaluationResult(
            boolean allowed,
            String reason,
            Set<String> violatedRules,
            Map<String, Object> metadata,
            Instant evaluatedAt) {
        public PolicyEvaluationResult {
            violatedRules = Set.copyOf(violatedRules != null ? violatedRules : Set.of());
            metadata = Map.copyOf(metadata != null ? metadata : Map.of());
            evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
        }

        public boolean isDenied() {
            return !allowed;
        }

        public static PolicyEvaluationResult allow() {
            return new PolicyEvaluationResult(true, null, Set.of(), Map.of(), Instant.now());
        }

        public static PolicyEvaluationResult deny(String reason, Set<String> violatedRules) {
            return new PolicyEvaluationResult(false, reason, violatedRules, Map.of(), Instant.now());
        }
    }

    /**
     * Policy evaluation context.
     */
    record EvaluationContext(
            String tenantId,
            String userId,
            String resourceType,
            String resourceId,
            String operation,
            Map<String, Object> resourceData,
            Map<String, Object> userContext,
            Map<String, Object> tenantContext) {
        public EvaluationContext {
            resourceData = Map.copyOf(resourceData != null ? resourceData : Map.of());
            userContext = Map.copyOf(userContext != null ? userContext : Map.of());
            tenantContext = Map.copyOf(tenantContext != null ? tenantContext : Map.of());
        }
    }

    /**
     * Policy category enumeration.
     */
    enum PolicyCategory {
        DATA_CLASSIFICATION,
        DATA_RETENTION,
        DATA_ENCRYPTION,
        DATA_REDACTION,
        AUDIT_LOGGING,
        LEGAL_HOLD,
        ACCESS_CONTROL,
        TENANT_ISOLATION,
        COMPLIANCE,
        SECURITY,
        PRIVACY
    }

    /**
     * Severity level enumeration.
     */
    enum SeverityLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
