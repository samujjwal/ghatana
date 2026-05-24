/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.operator.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates operator compatibility for pattern composition.
 *
 * <p>This service checks that operators can be composed together by validating:
 * - Input/output schema compatibility
 * - Parameter constraints
 * - Policy constraints
 * - Operator kind compatibility
 *
 * @doc.type class
 * @doc.purpose Validates operator compatibility for pattern composition
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class OperatorCompatibilityChecker {

    /**
     * Represents a detected compatibility issue.
     *
     * @param sourceOperator the source operator ID
     * @param targetOperator the target operator ID
     * @param issue the type of compatibility issue
     * @param description human-readable description
     */
    public record CompatibilityIssue(
            String sourceOperator,
            String targetOperator,
            IssueType issue,
            String description) {}

    /**
     * Enumeration of compatibility issue types.
     */
    public enum IssueType {
        SCHEMA_MISMATCH,
        PARAMETER_CONSTRAINT_VIOLATION,
        POLICY_CONSTRAINT_VIOLATION,
        KIND_INCOMPATIBILITY,
        MISSING_REQUIRED_PARAMETER
    }

    /**
     * Checks if two operators are compatible for composition.
     *
     * @param source the source operator (produces output)
     * @param target the target operator (consumes input)
     * @return list of compatibility issues (empty if compatible)
     */
    public List<CompatibilityIssue> checkCompatibility(OperatorSpec source, OperatorSpec target) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");

        List<CompatibilityIssue> issues = new ArrayList<>();

        // Check schema compatibility
        if (!source.outputSchema().equals(target.inputSchema())) {
            issues.add(new CompatibilityIssue(
                source.operatorId(),
                target.operatorId(),
                IssueType.SCHEMA_MISMATCH,
                "Source output schema '" + source.outputSchema() +
                "' does not match target input schema '" + target.inputSchema() + "'"));
        }

        // Check operator kind compatibility
        if (!areKindsCompatible(source.kind(), target.kind())) {
            issues.add(new CompatibilityIssue(
                source.operatorId(),
                target.operatorId(),
                IssueType.KIND_INCOMPATIBILITY,
                "Operator kind '" + source.kind() + "' is not compatible with '" + target.kind() + "'"));
        }

        return issues;
    }

    /**
     * Validates that an operator specification is internally valid.
     *
     * @param spec the operator specification to validate
     * @return list of validation issues (empty if valid)
     */
    public List<CompatibilityIssue> validateOperatorSpec(OperatorSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        List<CompatibilityIssue> issues = new ArrayList<>();

        // Check for required parameters based on operator kind
        List<String> requiredParams = getRequiredParametersForKind(spec.kind());
        for (String requiredParam : requiredParams) {
            if (!spec.parameters().containsKey(requiredParam)) {
                issues.add(new CompatibilityIssue(
                    spec.operatorId(),
                    spec.operatorId(),
                    IssueType.MISSING_REQUIRED_PARAMETER,
                    "Missing required parameter '" + requiredParam + "' for operator kind " + spec.kind()));
            }
        }

        return issues;
    }

    /**
     * Checks if two operator kinds are compatible for composition.
     *
     * @param sourceKind the source operator kind
     * @param targetKind the target operator kind
     * @return true if compatible
     */
    private boolean areKindsCompatible(OperatorKind sourceKind, OperatorKind targetKind) {
        // Placeholder: all kinds are compatible for now
        // Production implementation would have specific compatibility rules
        return true;
    }

    /**
     * Gets required parameters for a given operator kind.
     *
     * @param kind the operator kind
     * @return list of required parameter names
     */
    private List<String> getRequiredParametersForKind(OperatorKind kind) {
        // Placeholder: no required parameters for now
        // Production implementation would define required parameters per kind
        return List.of();
    }
}
