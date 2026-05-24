/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks policies for route categories before allowing operations.
 *
 * <p>This service integrates with PolicyEvaluator to enforce governance
 * policies at the route level. Each route category can have associated
 * policies that must be evaluated before the operation proceeds.
 *
 * @doc.type class
 * @doc.purpose Route-level policy enforcement for all API operations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class RoutePolicyChecker {

    private final PolicyEvaluator policyEvaluator;
    private final PolicyService policyService;

    public RoutePolicyChecker(PolicyEvaluator policyEvaluator, PolicyService policyService) {
        this.policyEvaluator = Objects.requireNonNull(policyEvaluator, "policyEvaluator must not be null");
        this.policyService = Objects.requireNonNull(policyService, "policyService must not be null");
    }

    /**
     * Checks if an operation is allowed for a given route category.
     *
     * @param category the route category
     * @param record the data artifact being operated on
     * @param context the evaluation context
     * @return list of policy violations (empty if operation is allowed)
     */
    public List<PolicyEvaluator.PolicyViolation> checkRoutePolicy(
            RouteCategory category,
            Map<String, Object> record,
            PolicyEvaluator.EvaluationContext context) {

        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(record, "record must not be null");
        Objects.requireNonNull(context, "context must not be null");

        // Get policies applicable to this route category
        List<PolicyService.Policy> applicablePolicies = policyService.getPoliciesForCategory(category);

        // Evaluate all applicable policies
        return policyEvaluator.evaluateAll(applicablePolicies, record, context);
    }

    /**
     * Checks if an operation is allowed and throws if violations are found.
     *
     * @param category the route category
     * @param record the data artifact being operated on
     * @param context the evaluation context
     * @throws PolicyViolationException if any policy violations are detected
     */
    public void enforceRoutePolicy(
            RouteCategory category,
            Map<String, Object> record,
            PolicyEvaluator.EvaluationContext context) {

        List<PolicyEvaluator.PolicyViolation> violations = checkRoutePolicy(category, record, context);

        if (!violations.isEmpty()) {
            throw new PolicyViolationException(
                "Policy violations detected for route category " + category,
                violations);
        }
    }

    /**
     * Exception thrown when policy violations are detected.
     */
    public static final class PolicyViolationException extends RuntimeException {
        private final List<PolicyEvaluator.PolicyViolation> violations;

        public PolicyViolationException(
                String message,
                List<PolicyEvaluator.PolicyViolation> violations) {
            super(message);
            this.violations = List.copyOf(violations);
        }

        public List<PolicyEvaluator.PolicyViolation> getViolations() {
            return violations;
        }
    }
}
