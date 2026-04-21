/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.ai.evaluation;

import java.util.Map;

/**
 * Defines acceptance thresholds for AI/ML model evaluation metrics.
 *
 * <p>Specifies minimum acceptable values for various evaluation metrics.
 * Models must meet or exceed these thresholds to be considered acceptable.</p>
 *
 * @doc.type class
 * @doc.purpose Acceptance threshold definitions for AI/ML evaluation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class EvaluationThresholds {

    private final String modelType;
    private final Map<String, Double> thresholds;

    private EvaluationThresholds(Builder builder) {
        this.modelType = builder.modelType;
        this.thresholds = Map.copyOf(builder.thresholds);
    }

    public String getModelType() {
        return modelType;
    }

    public Map<String, Double> getThresholds() {
        return thresholds;
    }

    public boolean meetsThreshold(String metric, double value) {
        Double threshold = thresholds.get(metric);
        if (threshold == null) {
            return true; // No threshold defined, consider met
        }
        return value >= threshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String modelType;
        private final Map<String, Double> thresholds = new java.util.HashMap<>();

        public Builder modelType(String modelType) {
            this.modelType = modelType;
            return this;
        }

        public Builder threshold(String metric, double value) {
            this.thresholds.put(metric, value);
            return this;
        }

        public Builder accuracy(double value) {
            return threshold("accuracy", value);
        }

        public Builder precision(double value) {
            return threshold("precision", value);
        }

        public Builder recall(double value) {
            return threshold("recall", value);
        }

        public Builder f1Score(double value) {
            return threshold("f1_score", value);
        }

        public Builder latency(double value) {
            return threshold("latency", value);
        }

        public EvaluationThresholds build() {
            if (modelType == null) {
                throw new IllegalStateException("modelType is required");
            }
            return new EvaluationThresholds(this);
        }
    }
}
