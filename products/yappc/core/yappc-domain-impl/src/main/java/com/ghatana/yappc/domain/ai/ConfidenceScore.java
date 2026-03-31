package com.ghatana.yappc.domain.ai;

import java.util.Objects;

/**
 * Confidence score for AI-generated artifacts with quality metrics.
 *
 * <p><b>Purpose</b><br>
 * Represents the AI's confidence in generated output, enabling:
 * - Quality-based filtering and ranking
 * - Human-in-the-loop triggering for low-confidence outputs
 * - Continuous improvement feedback loops
 *
 * <p><b>Score Interpretation</b><br>
 * - 0.9-1.0: High confidence - auto-approve eligible<br>
 * - 0.7-0.9: Medium confidence - standard review<br>
 * - 0.5-0.7: Low confidence - recommend human review<br>
 * - 0.0-0.5: Very low confidence - require human review<br>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ConfidenceScore score = ConfidenceScore.builder()
 *     .overall(0.85)
 *     .completeness(0.90)
 *     .correctness(0.80)
 *     .consistency(0.88)
 *     .build();
 *
 * if (score.requiresHumanReview()) {
 *     // Queue for human approval
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AI output confidence scoring
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class ConfidenceScore {

    private final double overall;
    private final double completeness;
    private final double correctness;
    private final double consistency;
    private final double complexity;

    // Thresholds for human review
    public static final double AUTO_APPROVE_THRESHOLD = 0.90;
    public static final double STANDARD_REVIEW_THRESHOLD = 0.70;
    public static final double HUMAN_REVIEW_THRESHOLD = 0.50;

    private ConfidenceScore(Builder builder) {
        this.overall = clamp(builder.overall);
        this.completeness = clamp(builder.completeness);
        this.correctness = clamp(builder.correctness);
        this.consistency = clamp(builder.consistency);
        this.complexity = clamp(builder.complexity);
    }

    /**
     * Returns the overall confidence score (0.0-1.0).
     */
    public double overall() {
        return overall;
    }

    /**
     * Returns completeness score - how complete is the output.
     */
    public double completeness() {
        return completeness;
    }

    /**
     * Returns correctness score - how likely is the output correct.
     */
    public double correctness() {
        return correctness;
    }

    /**
     * Returns consistency score - how consistent with requirements.
     */
    public double consistency() {
        return consistency;
    }

    /**
     * Returns complexity score - how complex is the generated solution.
     */
    public double complexity() {
        return complexity;
    }

    /**
     * Returns whether this output can be auto-approved.
     */
    public boolean canAutoApprove() {
        return overall >= AUTO_APPROVE_THRESHOLD &&
               correctness >= STANDARD_REVIEW_THRESHOLD;
    }

    /**
     * Returns whether human review is required.
     */
    public boolean requiresHumanReview() {
        return overall < STANDARD_REVIEW_THRESHOLD ||
               correctness < HUMAN_REVIEW_THRESHOLD;
    }

    /**
     * Returns whether this output should be rejected.
     */
    public boolean shouldReject() {
        return overall < HUMAN_REVIEW_THRESHOLD;
    }

    /**
     * Returns the review priority based on confidence.
     */
    public ReviewPriority getReviewPriority() {
        if (shouldReject()) {
            return ReviewPriority.CRITICAL;
        }
        if (requiresHumanReview()) {
            return ReviewPriority.HIGH;
        }
        if (canAutoApprove()) {
            return ReviewPriority.NONE;
        }
        return ReviewPriority.NORMAL;
    }

    /**
     * Creates a builder for ConfidenceScore.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a high confidence score for testing.
     */
    public static ConfidenceScore high() {
        return builder()
            .overall(0.95)
            .completeness(0.95)
            .correctness(0.95)
            .consistency(0.95)
            .complexity(0.50)
            .build();
    }

    /**
     * Creates a medium confidence score for testing.
     */
    public static ConfidenceScore medium() {
        return builder()
            .overall(0.75)
            .completeness(0.80)
            .correctness(0.75)
            .consistency(0.70)
            .complexity(0.60)
            .build();
    }

    /**
     * Creates a low confidence score for testing.
     */
    public static ConfidenceScore low() {
        return builder()
            .overall(0.40)
            .completeness(0.50)
            .correctness(0.35)
            .consistency(0.40)
            .complexity(0.80)
            .build();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        return String.format("ConfidenceScore{overall=%.2f, completeness=%.2f, correctness=%.2f, " +
            "consistency=%.2f, complexity=%.2f, priority=%s}",
            overall, completeness, correctness, consistency, complexity, getReviewPriority());
    }

    /**
     * Review priority levels.
     */
    public enum ReviewPriority {
        NONE,       // Auto-approve
        NORMAL,     // Standard queue
        HIGH,       // Expedited review
        CRITICAL    // Immediate attention
    }

    /**
     * Builder for ConfidenceScore.
     */
    public static final class Builder {
        private double overall = 0.0;
        private double completeness = 0.0;
        private double correctness = 0.0;
        private double consistency = 0.0;
        private double complexity = 0.0;

        private Builder() {}

        public Builder overall(double overall) {
            this.overall = overall;
            return this;
        }

        public Builder completeness(double completeness) {
            this.completeness = completeness;
            return this;
        }

        public Builder correctness(double correctness) {
            this.correctness = correctness;
            return this;
        }

        public Builder consistency(double consistency) {
            this.consistency = consistency;
            return this;
        }

        public Builder complexity(double complexity) {
            this.complexity = complexity;
            return this;
        }

        public ConfidenceScore build() {
            return new ConfidenceScore(this);
        }
    }
}
