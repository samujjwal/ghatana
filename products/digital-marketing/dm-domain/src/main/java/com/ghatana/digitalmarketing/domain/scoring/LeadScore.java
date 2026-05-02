package com.ghatana.digitalmarketing.domain.scoring;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable lead score snapshot for a workspace prospect.
 *
 * <p>The score is numeric (0–100), graded, explained via dimensions, versioned, and
 * may be flagged for human review when confidence is low.
 *
 * @doc.type class
 * @doc.purpose DMOS deterministic lead scoring aggregate for F1-012 prospect prioritization
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class LeadScore {

    private final String scoreId;
    private final DmWorkspaceId workspaceId;
    private final int score;
    private final LeadGrade grade;
    private final List<ScoreDimension> dimensions;
    private final double confidence;
    private final boolean requiresHumanReview;
    private final String recommendedNextAction;
    private final String modelVersion;
    private final Instant scoredAt;
    private final String scoredBy;

    private LeadScore(Builder builder) {
        this.scoreId = Objects.requireNonNull(builder.scoreId, "scoreId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.dimensions = builder.dimensions != null ? List.copyOf(builder.dimensions) : List.of();
        this.confidence = builder.confidence;
        this.requiresHumanReview = builder.requiresHumanReview;
        this.recommendedNextAction = Objects.requireNonNull(
                builder.recommendedNextAction, "recommendedNextAction must not be null");
        this.modelVersion = Objects.requireNonNull(builder.modelVersion, "modelVersion must not be null");
        this.scoredAt = Objects.requireNonNull(builder.scoredAt, "scoredAt must not be null");
        this.scoredBy = Objects.requireNonNull(builder.scoredBy, "scoredBy must not be null");

        if (scoreId.isBlank()) {
            throw new IllegalArgumentException("scoreId must not be blank");
        }
        if (builder.score < 0 || builder.score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100, got: " + builder.score);
        }
        this.score = builder.score;
        this.grade = Objects.requireNonNull(builder.grade, "grade must not be null");

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (recommendedNextAction.isBlank()) {
            throw new IllegalArgumentException("recommendedNextAction must not be blank");
        }
        if (modelVersion.isBlank()) {
            throw new IllegalArgumentException("modelVersion must not be blank");
        }
        if (scoredBy.isBlank()) {
            throw new IllegalArgumentException("scoredBy must not be blank");
        }
    }

    /**
     * Returns the unique score identifier.
     *
     * @return score ID
     */
    public String getScoreId() {
        return scoreId;
    }

    /**
     * Returns the workspace this score applies to.
     *
     * @return workspace ID
     */
    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    /**
     * Returns the numeric score from 0 to 100.
     *
     * @return score value
     */
    public int getScore() {
        return score;
    }

    /**
     * Returns the letter grade derived from the numeric score.
     *
     * @return lead grade
     */
    public LeadGrade getGrade() {
        return grade;
    }

    /**
     * Returns the individual scoring dimensions.
     *
     * @return unmodifiable list of score dimensions
     */
    public List<ScoreDimension> getDimensions() {
        return dimensions;
    }

    /**
     * Returns the model confidence between 0.0 and 1.0.
     *
     * @return confidence
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Returns whether this score requires human review due to low confidence.
     *
     * @return true when human review is required
     */
    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    /**
     * Returns the recommended next action for this prospect.
     *
     * @return recommended next action
     */
    public String getRecommendedNextAction() {
        return recommendedNextAction;
    }

    /**
     * Returns the scoring model version for auditability.
     *
     * @return model version string
     */
    public String getModelVersion() {
        return modelVersion;
    }

    /**
     * Returns when the score was generated.
     *
     * @return scored timestamp
     */
    public Instant getScoredAt() {
        return scoredAt;
    }

    /**
     * Returns the principal that triggered the scoring run.
     *
     * @return scored by
     */
    public String getScoredBy() {
        return scoredBy;
    }

    /**
     * Returns a new builder for {@link LeadScore}.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link LeadScore}.
     */
    public static final class Builder {
        private String scoreId;
        private DmWorkspaceId workspaceId;
        private int score;
        private LeadGrade grade;
        private List<ScoreDimension> dimensions;
        private double confidence;
        private boolean requiresHumanReview;
        private String recommendedNextAction;
        private String modelVersion;
        private Instant scoredAt;
        private String scoredBy;

        private Builder() {
        }

        /** Sets the score identifier. */
        public Builder scoreId(String scoreId) {
            this.scoreId = scoreId;
            return this;
        }

        /** Sets the workspace identifier. */
        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        /** Sets the numeric score (0–100). */
        public Builder score(int score) {
            this.score = score;
            return this;
        }

        /** Sets the letter grade. */
        public Builder grade(LeadGrade grade) {
            this.grade = grade;
            return this;
        }

        /** Sets the scoring dimensions. */
        public Builder dimensions(List<ScoreDimension> dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /** Sets the model confidence (0.0–1.0). */
        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        /** Sets whether human review is required. */
        public Builder requiresHumanReview(boolean requiresHumanReview) {
            this.requiresHumanReview = requiresHumanReview;
            return this;
        }

        /** Sets the recommended next action. */
        public Builder recommendedNextAction(String recommendedNextAction) {
            this.recommendedNextAction = recommendedNextAction;
            return this;
        }

        /** Sets the scoring model version. */
        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        /** Sets when the score was generated. */
        public Builder scoredAt(Instant scoredAt) {
            this.scoredAt = scoredAt;
            return this;
        }

        /** Sets who triggered the scoring run. */
        public Builder scoredBy(String scoredBy) {
            this.scoredBy = scoredBy;
            return this;
        }

        /**
         * Builds the {@link LeadScore} instance.
         *
         * @return new lead score
         */
        public LeadScore build() {
            return new LeadScore(this);
        }
    }
}
