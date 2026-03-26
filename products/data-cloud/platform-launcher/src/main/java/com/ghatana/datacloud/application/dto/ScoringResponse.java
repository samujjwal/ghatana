package com.ghatana.datacloud.application.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response DTO for quality scoring operations.
 *
 * <p>Contains quality metrics across four dimensions (completeness, consistency,
 * accuracy, relevance) plus overall score and quality level classification.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ScoringResponse response = ScoringResponse.builder()
 *     .tenantId("tenant-123")
 *     .success(true)
 *     .completeness(85)
 *     .consistency(90)
 *     .accuracy(88)
 *     .relevance(92)
 *     .durationMs(300)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Response transfer object for quality scoring
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ScoringResponse {
    private final String tenantId;
    private final boolean success;
    private final int completeness;
    private final int consistency;
    private final int accuracy;
    private final int relevance;
    private final int overall;
    private final String level;
    private final String summary;
    private final List<String> findings;
    private final List<String> recommendations;
    private final long durationMs;

    /**
     * Constructs a new scoring response.
     *
     * @param tenantId tenant identifier (non-blank)
     * @param success whether scoring succeeded
     * @param completeness completeness score (0-100)
     * @param consistency consistency score (0-100)
     * @param accuracy accuracy score (0-100)
     * @param relevance relevance score (0-100)
     * @param overall overall score (0-100)
     * @param level quality level (EXCELLENT/GOOD/FAIR/POOR)
     * @param summary score explanation summary
     * @param findings list of quality findings
     * @param recommendations list of improvement recommendations
     * @param durationMs processing duration in milliseconds (non-negative)
     * @throws NullPointerException if any non-optional parameter is null
     * @throws IllegalArgumentException if validation fails
     */
    private ScoringResponse(String tenantId, boolean success, int completeness, int consistency,
            int accuracy, int relevance, int overall, String level, String summary,
            List<String> findings, List<String> recommendations, long durationMs) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.findings = Objects.requireNonNull(findings, "findings cannot be null");
        this.recommendations = Objects.requireNonNull(recommendations, "recommendations cannot be null");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (completeness < 0 || completeness > 100) {
            throw new IllegalArgumentException("completeness must be 0-100");
        }
        if (consistency < 0 || consistency > 100) {
            throw new IllegalArgumentException("consistency must be 0-100");
        }
        if (accuracy < 0 || accuracy > 100) {
            throw new IllegalArgumentException("accuracy must be 0-100");
        }
        if (relevance < 0 || relevance > 100) {
            throw new IllegalArgumentException("relevance must be 0-100");
        }
        if (overall < 0 || overall > 100) {
            throw new IllegalArgumentException("overall must be 0-100");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }

        this.success = success;
        this.completeness = completeness;
        this.consistency = consistency;
        this.accuracy = accuracy;
        this.relevance = relevance;
        this.overall = overall;
        this.summary = summary;
        this.durationMs = durationMs;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return tenant ID (non-blank)
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Checks if scoring succeeded.
     *
     * @return true if scoring completed successfully
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the completeness score.
     *
     * @return score 0-100
     */
    public int getCompleteness() {
        return completeness;
    }

    /**
     * Gets the consistency score.
     *
     * @return score 0-100
     */
    public int getConsistency() {
        return consistency;
    }

    /**
     * Gets the accuracy score.
     *
     * @return score 0-100
     */
    public int getAccuracy() {
        return accuracy;
    }

    /**
     * Gets the relevance score.
     *
     * @return score 0-100
     */
    public int getRelevance() {
        return relevance;
    }

    /**
     * Gets the overall quality score.
     *
     * @return score 0-100
     */
    public int getOverall() {
        return overall;
    }

    /**
     * Gets the quality level classification.
     *
     * @return level (EXCELLENT/GOOD/FAIR/POOR)
     */
    public String getLevel() {
        return level;
    }

    /**
     * Gets the score explanation summary.
     *
     * @return summary text
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Gets the quality findings.
     *
     * @return list of findings
     */
    public List<String> getFindings() {
        return findings;
    }

    /**
     * Gets the improvement recommendations.
     *
     * @return list of recommendations
     */
    public List<String> getRecommendations() {
        return recommendations;
    }

    /**
     * Gets the processing duration.
     *
     * @return duration in milliseconds (non-negative)
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Gets all metrics as a map.
     *
     * @return metrics map with all dimension scores
     */
    public Map<String, Integer> getMetrics() {
        return Map.of(
            "completeness", completeness,
            "consistency", consistency,
            "accuracy", accuracy,
            "relevance", relevance,
            "overall", overall
        );
    }

    /**
     * Creates a builder for constructing responses.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ScoringResponse.
     *
     * @see ScoringResponse
     */
    public static class Builder {
        private String tenantId;
        private boolean success;
        private int completeness;
        private int consistency;
        private int accuracy;
        private int relevance;
        private int overall;
        private String level;
        private String summary;
        private List<String> findings;
        private List<String> recommendations;
        private long durationMs;

        /**
         * Sets the tenant identifier.
         *
         * @param tenantId tenant ID
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the success status.
         *
         * @param success success flag
         * @return this builder
         */
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        /**
         * Sets the completeness score.
         *
         * @param completeness score 0-100
         * @return this builder
         */
        public Builder completeness(int completeness) {
            this.completeness = completeness;
            return this;
        }

        /**
         * Sets the consistency score.
         *
         * @param consistency score 0-100
         * @return this builder
         */
        public Builder consistency(int consistency) {
            this.consistency = consistency;
            return this;
        }

        /**
         * Sets the accuracy score.
         *
         * @param accuracy score 0-100
         * @return this builder
         */
        public Builder accuracy(int accuracy) {
            this.accuracy = accuracy;
            return this;
        }

        /**
         * Sets the relevance score.
         *
         * @param relevance score 0-100
         * @return this builder
         */
        public Builder relevance(int relevance) {
            this.relevance = relevance;
            return this;
        }

        /**
         * Sets the overall score.
         *
         * @param overall score 0-100
         * @return this builder
         */
        public Builder overall(int overall) {
            this.overall = overall;
            return this;
        }

        /**
         * Sets the quality level.
         *
         * @param level classification
         * @return this builder
         */
        public Builder level(String level) {
            this.level = level;
            return this;
        }

        /**
         * Sets the summary.
         *
         * @param summary explanation text
         * @return this builder
         */
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        /**
         * Sets the findings.
         *
         * @param findings list of findings
         * @return this builder
         */
        public Builder findings(List<String> findings) {
            this.findings = findings;
            return this;
        }

        /**
         * Sets the recommendations.
         *
         * @param recommendations list of recommendations
         * @return this builder
         */
        public Builder recommendations(List<String> recommendations) {
            this.recommendations = recommendations;
            return this;
        }

        /**
         * Sets the processing duration.
         *
         * @param durationMs duration in milliseconds
         * @return this builder
         */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        /**
         * Builds the ScoringResponse.
         *
         * @return new response instance
         * @throws NullPointerException if required fields are null
         * @throws IllegalArgumentException if validation fails
         */
        public ScoringResponse build() {
            return new ScoringResponse(tenantId, success, completeness, consistency,
                accuracy, relevance, overall, level, summary, findings, recommendations, durationMs);
        }
    }

    @Override
    public String toString() {
        return "ScoringResponse{" +
                "tenantId='" + tenantId + '\'' +
                ", success=" + success +
                ", overall=" + overall +
                ", level='" + level + '\'' +
                ", completeness=" + completeness +
                ", consistency=" + consistency +
                ", accuracy=" + accuracy +
                ", relevance=" + relevance +
                ", durationMs=" + durationMs +
                '}';
    }
}
