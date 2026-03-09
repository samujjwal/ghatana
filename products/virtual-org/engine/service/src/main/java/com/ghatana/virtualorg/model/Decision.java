package com.ghatana.virtualorg.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an agent decision with rationale and metadata.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates decision outcomes from agent task processing:
 * - Decision type (APPROVE, REJECT, ESCALATE, etc.)
 * - Rationale and reasoning
 * - Confidence score
 * - Escalation target if applicable
 * - Additional context metadata
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Decision decision = Decision.builder()
 *     .type(DecisionType.APPROVE)
 *     .rationale("All quality gates passed")
 *     .confidence(0.95)
 *     .build();
 *
 * if (decision.getType() == DecisionType.ESCALATE) {
 *     escalateTo(decision.getEscalationTarget().orElse("TEAM_LEAD"));
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @doc.type record
 * @doc.purpose Agent decision with rationale
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record Decision(
        DecisionType type,
        String rationale,
        double confidence,
        String escalationTarget) {

    /**
     * Compact constructor with validation.
     */
    public Decision {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rationale, "rationale");
        
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        
        if (type == DecisionType.ESCALATE && (escalationTarget == null || escalationTarget.isBlank())) {
            throw new IllegalArgumentException("escalationTarget required for ESCALATE decision");
        }
    }

    /**
     * Gets decision type.
     */
    public DecisionType getType() {
        return type;
    }

    /**
     * Gets decision rationale.
     */
    public String getRationale() {
        return rationale;
    }

    /**
     * Alias for getRationale() - for compatibility.
     */
    public String getReasoning() {
        return rationale;
    }

    /**
     * Gets confidence score (0.0 to 1.0).
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Gets escalation target if decision is ESCALATE.
     */
    public Optional<String> getEscalationTarget() {
        return Optional.ofNullable(escalationTarget);
    }

    /**
     * Creates a new Decision builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Decision.
     */
    public static final class Builder {
        private DecisionType type;
        private String rationale;
        private double confidence = 1.0;
        private String escalationTarget;

        private Builder() {}

        public Builder type(DecisionType type) {
            this.type = type;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder escalationTarget(String escalationTarget) {
            this.escalationTarget = escalationTarget;
            return this;
        }

        public Decision build() {
            return new Decision(type, rationale, confidence, escalationTarget);
        }
    }
}
