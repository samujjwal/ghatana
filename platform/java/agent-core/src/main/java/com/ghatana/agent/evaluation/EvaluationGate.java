/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * An evaluation gate that must be passed for a learning artifact to advance.
 *
 * <p>Gates define quality, safety, and performance thresholds that must be met
 * before an artifact can be promoted or deployed.
 *
 * @doc.type record
 * @doc.purpose Evaluation gate for quality/safety thresholds
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record EvaluationGate(
        @NotNull String gateId,
        @NotNull String name,
        @NotNull String description,
        @NotNull GateType type,
        @NotNull String metricName,
        double threshold,
        @NotNull ComparisonOperator operator,
        boolean required,
        @NotNull Map<String, String> metadata
) {
    public EvaluationGate {
        Objects.requireNonNull(gateId, "gateId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(metricName, "metricName must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Evaluates whether a given value passes this gate.
     *
     * @param value the value to evaluate
     * @return true if the value passes the gate, false otherwise
     */
    public boolean passes(double value) {
        return switch (operator) {
            case GREATER_THAN -> value > threshold;
            case GREATER_THAN_OR_EQUAL -> value >= threshold;
            case LESS_THAN -> value < threshold;
            case LESS_THAN_OR_EQUAL -> value <= threshold;
            case EQUALS -> value == threshold;
            case NOT_EQUALS -> value != threshold;
        };
    }

    /**
     * Types of evaluation gates.
     */
    public enum GateType {
        /** Quality gate for accuracy, precision, recall, etc. */
        QUALITY,
        /** Safety gate for harm reduction, bias detection, etc. */
        SAFETY,
        /** Performance gate for latency, throughput, etc. */
        PERFORMANCE,
        /** Security gate for vulnerability checks, etc. */
        SECURITY,
        /** Compatibility gate for version compatibility checks. */
        COMPATIBILITY
    }

    /**
     * Comparison operators for gate evaluation.
     */
    public enum ComparisonOperator {
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        EQUALS,
        NOT_EQUALS
    }
}
