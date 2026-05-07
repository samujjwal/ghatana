package com.ghatana.digitalmarketing.bridge.governance;

import java.time.Instant;
import java.util.Objects;

/**
 * P0-011: Typed experiment configuration for AI model A/B testing.
 * 
 * <p>Replaces raw string split percent with validated percentage type.
 * Adds metrics tracking, outcome tracking, and approval requirements.
 *
 * @doc.type class
 * @doc.purpose Typed experiment configuration with validated split percent and metrics tracking
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class AiExperimentConfig {

    private final String experimentId;
    private final String baselineModelRef;
    private final String variantModelRef;
    private final SplitPercent splitPercent;
    private final ExperimentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final ApprovalState approvalState;
    private final ExperimentMetrics metrics;

    private AiExperimentConfig(Builder builder) {
        this.experimentId = Objects.requireNonNull(builder.experimentId, "experimentId required");
        this.baselineModelRef = Objects.requireNonNull(builder.baselineModelRef, "baselineModelRef required");
        this.variantModelRef = Objects.requireNonNull(builder.variantModelRef, "variantModelRef required");
        this.splitPercent = Objects.requireNonNull(builder.splitPercent, "splitPercent required");
        this.status = builder.status != null ? builder.status : ExperimentStatus.DRAFT;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
        this.approvalState = builder.approvalState != null ? builder.approvalState : ApprovalState.PENDING;
        this.metrics = builder.metrics != null ? builder.metrics : new ExperimentMetrics();
    }

    public String experimentId() { return experimentId; }
    public String baselineModelRef() { return baselineModelRef; }
    public String variantModelRef() { return variantModelRef; }
    public SplitPercent splitPercent() { return splitPercent; }
    public ExperimentStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public ApprovalState approvalState() { return approvalState; }
    public ExperimentMetrics metrics() { return metrics; }

    public Builder toBuilder() {
        return new Builder()
                .experimentId(experimentId)
                .baselineModelRef(baselineModelRef)
                .variantModelRef(variantModelRef)
                .splitPercent(splitPercent)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .approvalState(approvalState)
                .metrics(metrics);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * P0-011: Validates and parses split percent string into typed value.
     * Accepts formats like "20%", "20", "0.2".
     */
    public static SplitPercent parseSplitPercent(String raw) {
        Objects.requireNonNull(raw, "split percent required");
        String normalized = raw.trim().replaceAll("%", "");
        
        try {
            double value = Double.parseDouble(normalized);
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException("Split percent must be between 0 and 100: " + raw);
            }
            return new SplitPercent(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid split percent format: " + raw, e);
        }
    }

    /**
     * P0-011: Validates model reference format (name:version).
     */
    public static void validateModelRef(String modelRef) {
        Objects.requireNonNull(modelRef, "model ref required");
        if (!modelRef.matches("^[a-zA-Z0-9_-]+:[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid model ref format, expected 'name:version': " + modelRef);
        }
    }

    /**
     * Typed split percent value (0-100).
     */
    public static final class SplitPercent {
        private final double value;

        public SplitPercent(double value) {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException("Split percent must be between 0 and 100: " + value);
            }
            this.value = value;
        }

        public double value() { return value; }
        public double asDecimal() { return value / 100.0; }
        
        @Override
        public String toString() {
            return value + "%";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SplitPercent that = (SplitPercent) o;
            return Double.compare(that.value, value) == 0;
        }

        @Override
        public int hashCode() { return Double.hashCode(value); }
    }

    /**
     * Experiment lifecycle status.
     */
    public enum ExperimentStatus {
        DRAFT,
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED
    }

    /**
     * P0-011: Approval state for experiment promotion.
     */
    public enum ApprovalState {
        PENDING,
        APPROVED,
        REJECTED,
        AUTO_APPROVED
    }

    /**
     * P0-011: Metrics and outcomes for experiment evaluation.
     */
    public static final class ExperimentMetrics {
        private long baselineSampleSize = 0;
        private long variantSampleSize = 0;
        private double baselineConversionRate = 0.0;
        private double variantConversionRate = 0.0;
        private double statisticalSignificance = 0.0;
        private String outcome = "IN_PROGRESS";

        public long baselineSampleSize() { return baselineSampleSize; }
        public long variantSampleSize() { return variantSampleSize; }
        public double baselineConversionRate() { return baselineConversionRate; }
        public double variantConversionRate() { return variantConversionRate; }
        public double statisticalSignificance() { return statisticalSignificance; }
        public String outcome() { return outcome; }

        public ExperimentMetrics withBaselineSampleSize(long size) {
            ExperimentMetrics copy = new ExperimentMetrics();
            copy.baselineSampleSize = size;
            copy.variantSampleSize = this.variantSampleSize;
            copy.baselineConversionRate = this.baselineConversionRate;
            copy.variantConversionRate = this.variantConversionRate;
            copy.statisticalSignificance = this.statisticalSignificance;
            copy.outcome = this.outcome;
            return copy;
        }

        public ExperimentMetrics withVariantSampleSize(long size) {
            ExperimentMetrics copy = new ExperimentMetrics();
            copy.baselineSampleSize = this.baselineSampleSize;
            copy.variantSampleSize = size;
            copy.baselineConversionRate = this.baselineConversionRate;
            copy.variantConversionRate = this.variantConversionRate;
            copy.statisticalSignificance = this.statisticalSignificance;
            copy.outcome = this.outcome;
            return copy;
        }

        public ExperimentMetrics withOutcome(String outcome) {
            ExperimentMetrics copy = new ExperimentMetrics();
            copy.baselineSampleSize = this.baselineSampleSize;
            copy.variantSampleSize = this.variantSampleSize;
            copy.baselineConversionRate = this.baselineConversionRate;
            copy.variantConversionRate = this.variantConversionRate;
            copy.statisticalSignificance = this.statisticalSignificance;
            copy.outcome = Objects.requireNonNull(outcome, "outcome required");
            return copy;
        }
    }

    public static final class Builder {
        private String experimentId;
        private String baselineModelRef;
        private String variantModelRef;
        private SplitPercent splitPercent;
        private ExperimentStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private ApprovalState approvalState;
        private ExperimentMetrics metrics;

        public Builder experimentId(String experimentId) {
            this.experimentId = experimentId;
            return this;
        }

        public Builder baselineModelRef(String baselineModelRef) {
            this.baselineModelRef = baselineModelRef;
            return this;
        }

        public Builder variantModelRef(String variantModelRef) {
            this.variantModelRef = variantModelRef;
            return this;
        }

        public Builder splitPercent(SplitPercent splitPercent) {
            this.splitPercent = splitPercent;
            return this;
        }

        public Builder splitPercent(String rawSplitPercent) {
            this.splitPercent = parseSplitPercent(rawSplitPercent);
            return this;
        }

        public Builder status(ExperimentStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder approvalState(ApprovalState approvalState) {
            this.approvalState = approvalState;
            return this;
        }

        public Builder metrics(ExperimentMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public AiExperimentConfig build() {
            return new AiExperimentConfig(this);
        }
    }
}
