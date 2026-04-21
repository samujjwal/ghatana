/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.ai.evaluation;

import java.time.Instant;
import java.util.Map;

/**
 * Tracks drift metrics for AI/ML models over time.
 *
 * <p>Monitors changes in model performance and data distribution to detect
 * concept drift, data drift, and performance degradation.</p>
 *
 * @doc.type class
 * @doc.purpose Drift metric tracking for AI/ML models
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class DriftMetrics {

    private final String modelId;
    private final String modelVersion;
    private final Instant timestamp;
    private final Map<String, Double> currentMetrics;
    private final Map<String, Double> baselineMetrics;
    private final Map<String, Double> driftScores;

    private DriftMetrics(Builder builder) {
        this.modelId = builder.modelId;
        this.modelVersion = builder.modelVersion;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.currentMetrics = Map.copyOf(builder.currentMetrics);
        this.baselineMetrics = Map.copyOf(builder.baselineMetrics);
        this.driftScores = Map.copyOf(builder.driftScores);
    }

    public String getModelId() {
        return modelId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Double> getCurrentMetrics() {
        return currentMetrics;
    }

    public Map<String, Double> getBaselineMetrics() {
        return baselineMetrics;
    }

    public Map<String, Double> getDriftScores() {
        return driftScores;
    }

    /**
     * Checks if any metric has drifted beyond the acceptable threshold.
     *
     * @param maxDriftScore maximum acceptable drift score (0-1)
     * @return true if significant drift detected
     */
    public boolean hasSignificantDrift(double maxDriftScore) {
        return driftScores.values().stream()
                .anyMatch(score -> score > maxDriftScore);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String modelId;
        private String modelVersion;
        private Instant timestamp;
        private final Map<String, Double> currentMetrics = new java.util.HashMap<>();
        private final Map<String, Double> baselineMetrics = new java.util.HashMap<>();
        private final Map<String, Double> driftScores = new java.util.HashMap<>();

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder currentMetric(String name, double value) {
            this.currentMetrics.put(name, value);
            return this;
        }

        public Builder baselineMetric(String name, double value) {
            this.baselineMetrics.put(name, value);
            return this;
        }

        public Builder driftScore(String name, double score) {
            this.driftScores.put(name, score);
            return this;
        }

        public DriftMetrics build() {
            if (modelId == null) {
                throw new IllegalStateException("modelId is required");
            }
            return new DriftMetrics(this);
        }
    }
}
