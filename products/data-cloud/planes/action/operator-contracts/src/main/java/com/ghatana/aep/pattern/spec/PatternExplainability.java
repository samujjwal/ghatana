/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Explainability metadata for pattern execution (P4-02).
 *
 * <p>P4-02: Provides human-readable explanations of pattern behavior,
 * data flow, transformations, and expected outcomes for debugging,
 * auditing, and operational understanding.
 *
 * @doc.type record
 * @doc.purpose Explainability metadata for pattern execution with human-readable explanations
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternExplainability(
    String summary,
    List<String> executionSteps,
    Map<String, Object> dataFlow,
    List<String> warnings,
    Optional<String> executionEstimate,
    List<String> requiredCapabilities,
    List<String> sideEffects
) {
    public PatternExplainability {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary is required");
        }
        executionSteps = List.copyOf(executionSteps != null ? executionSteps : List.of());
        dataFlow = Map.copyOf(dataFlow != null ? dataFlow : Map.of());
        warnings = List.copyOf(warnings != null ? warnings : List.of());
        executionEstimate = executionEstimate != null ? executionEstimate : Optional.empty();
        requiredCapabilities = List.copyOf(requiredCapabilities != null ? requiredCapabilities : List.of());
        sideEffects = List.copyOf(sideEffects != null ? sideEffects : List.of());
    }

    /**
     * Check if this pattern has any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Check if this pattern has any side effects.
     */
    public boolean hasSideEffects() {
        return !sideEffects.isEmpty();
    }

    /**
     * Check if this pattern requires any external capabilities.
     */
    public boolean requiresCapabilities() {
        return !requiredCapabilities.isEmpty();
    }

    /**
     * Create an empty explainability object.
     */
    public static PatternExplainability empty() {
        return new PatternExplainability(
            "No explanation available",
            List.of(),
            Map.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of()
        );
    }

    /**
     * Create a simple explainability object with just a summary.
     */
    public static PatternExplainability of(String summary) {
        return new PatternExplainability(
            summary,
            List.of(),
            Map.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of()
        );
    }

    /**
     * Builder for constructing PatternExplainability.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String summary;
        private final java.util.ArrayList<String> executionSteps = new java.util.ArrayList<>();
        private final java.util.LinkedHashMap<String, Object> dataFlow = new java.util.LinkedHashMap<>();
        private final java.util.ArrayList<String> warnings = new java.util.ArrayList<>();
        private String executionEstimate;
        private final java.util.ArrayList<String> requiredCapabilities = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> sideEffects = new java.util.ArrayList<>();

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder addExecutionStep(String step) {
            this.executionSteps.add(step);
            return this;
        }

        public Builder addDataFlow(String key, Object value) {
            this.dataFlow.put(key, value);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder executionEstimate(String estimate) {
            this.executionEstimate = estimate;
            return this;
        }

        public Builder addRequiredCapability(String capability) {
            this.requiredCapabilities.add(capability);
            return this;
        }

        public Builder addSideEffect(String sideEffect) {
            this.sideEffects.add(sideEffect);
            return this;
        }

        public PatternExplainability build() {
            return new PatternExplainability(
                summary,
                executionSteps,
                dataFlow,
                warnings,
                Optional.ofNullable(executionEstimate),
                requiredCapabilities,
                sideEffects
            );
        }
    }
}
