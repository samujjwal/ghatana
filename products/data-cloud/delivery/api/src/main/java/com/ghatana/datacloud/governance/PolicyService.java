/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for policy enforcement and governance.
 *
 * @doc.type interface
 * @doc.purpose Policy management and enforcement
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface PolicyService {

    /**
     * Evaluate policy against context.
     *
     * @param policyId policy identifier
     * @param context evaluation context
     * @return promise of evaluation result
     */
    Promise<PolicyResult> evaluate(String policyId, PolicyContext context);

    /**
     * Create or update policy.
     *
     * @param policy policy definition
     * @return promise of saved policy
     */
    Promise<Policy> savePolicy(Policy policy);

    /**
     * Get policy by ID.
     *
     * @param policyId policy identifier
     * @return promise of policy if found
     */
    Promise<Optional<Policy>> getPolicy(String policyId);

    /**
     * List policies for tenant.
     *
     * @param tenantId tenant identifier
     * @param type optional type filter
     * @return promise of policy list
     */
    Promise<List<Policy>> listPolicies(String tenantId, PolicyType type);

    /**
     * Delete policy.
     *
     * @param policyId policy identifier
     * @return promise completing when deleted
     */
    Promise<Void> deletePolicy(String policyId);

    /**
     * Validate action against all applicable policies.
     *
     * @param action action to validate
     * @param context validation context
     * @return promise of validation result
     */
    Promise<ValidationResult> validateAction(String action, PolicyContext context);

    /**
     * Get policy violations.
     *
     * @param tenantId tenant identifier
     * @param since start time
     * @return promise of violation list
     */
    Promise<List<PolicyViolation>> getViolations(String tenantId, Instant since);

    /**
     * Policy types.
     */
    enum PolicyType {
        DATA_RETENTION,
        ACCESS_CONTROL,
        DATA_CLASSIFICATION,
        COMPLIANCE,
        CUSTOM
    }

    /**
     * Policy definition.
     */
    record Policy(
        String id,
        String name,
        String description,
        String tenantId,
        PolicyType type,
        List<Rule> rules,
        boolean enabled,
        int priority,
        Instant createdAt,
        Instant updatedAt
    ) {}

    /**
     * Policy rule.
     */
    record Rule(
        String name,
        Condition condition,
        Effect effect,
        String message
    ) {}

    /**
     * Condition for rule matching.
     */
    record Condition(
        String attribute,
        Operator operator,
        Object value
    ) {
        public enum Operator {
            EQUALS, NOT_EQUALS, CONTAINS, GREATER_THAN, LESS_THAN,
            EXISTS, NOT_EXISTS, MATCHES, IN, NOT_IN
        }
    }

    /**
     * Rule effect.
     */
    enum Effect {
        ALLOW, DENY, AUDIT, REQUIRE_APPROVAL
    }

    /**
     * Policy evaluation context.
     */
    record PolicyContext(
        String userId,
        String tenantId,
        String action,
        String resource,
        Map<String, Object> attributes,
        Instant timestamp
    ) {}

    /**
     * Policy evaluation result.
     */
    record PolicyResult(
        String policyId,
        boolean allowed,
        List<String> matchedRules,
        List<String> violatedRules,
        Map<String, Object> metadata
    ) {
        public boolean isAllowed() {
            return allowed && violatedRules.isEmpty();
        }
    }

    /**
     * Validation result.
     */
    record ValidationResult(
        boolean valid,
        List<PolicyResult> policyResults,
        List<String> errors,
        List<String> warnings
    ) {}

    /**
     * Policy violation record.
     */
    record PolicyViolation(
        String id,
        String policyId,
        String tenantId,
        String userId,
        String action,
        String resource,
        String violationType,
        Instant timestamp,
        Map<String, Object> details
    ) {}
}
