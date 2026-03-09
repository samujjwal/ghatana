package com.ghatana.requirements.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of requirement quality validation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides comprehensive quality assessment of a requirement including multiple
 * quality dimensions, identified issues, and improvement recommendations.
 *
 * <p>
 * <b>Quality Dimensions</b><br>
 * <ul>
 * <li><b>Overall Score</b>: Aggregate quality score (0.0 to 1.0)</li>
 * <li><b>Clarity</b>: How well-defined and unambiguous</li>
 * <li><b>Completeness</b>: Presence of all necessary information</li>
 * <li><b>Testability</b>: How easily it can be verified</li>
 * <li><b>Consistency</b>: Alignment with project standards</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * RequirementQualityResult result = aiService.validateQuality(requirement).getResult();
 *
 * if (result.isAcceptable()) {
 *     System.out.println("Quality: " + result.getQualityLevel());
 * } else {
 *     for (QualityIssue issue : result.getIssues()) {
 *         System.out.println("Issue: " + issue.getDescription());
 *     }
 * }
 *
 * for (String recommendation : result.getRecommendations()) {
 *     System.out.println("Recommendation: " + recommendation);
 * }
 * }</pre>
 *
 * @see RequirementAIService#validateQuality(String)
 * @doc.type class
 * @doc.purpose Requirement quality validation result
 * @doc.layer product
 * @doc.pattern DTO
 */
public class RequirementQualityResult {

    private final double overallScore;
    private final double clarityScore;
    private final double completenessScore;
    private final double testabilityScore;
    private final double consistencyScore;
    private final List<QualityIssue> issues;
    private final List<String> recommendations;
    private final QualityLevel qualityLevel;

    private RequirementQualityResult(Builder builder) {
        this.overallScore = builder.overallScore;
        this.clarityScore = builder.clarityScore;
        this.completenessScore = builder.completenessScore;
        this.testabilityScore = builder.testabilityScore;
        this.consistencyScore = builder.consistencyScore;
        this.issues = Collections.unmodifiableList(new ArrayList<>(builder.issues));
        this.recommendations = Collections.unmodifiableList(new ArrayList<>(builder.recommendations));
        this.qualityLevel = determineQualityLevel(this.overallScore);

        validateScores();
    }

    private void validateScores() {
        validateScore("overallScore", overallScore);
        validateScore("clarityScore", clarityScore);
        validateScore("completenessScore", completenessScore);
        validateScore("testabilityScore", testabilityScore);
        validateScore("consistencyScore", consistencyScore);
    }

    private void validateScore(String name, double score) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0, got: " + score);
        }
    }

    private QualityLevel determineQualityLevel(double score) {
        if (score >= 0.9) {
            return QualityLevel.EXCELLENT;
        }
        if (score >= 0.7) {
            return QualityLevel.GOOD;
        }
        if (score >= 0.5) {
            return QualityLevel.ACCEPTABLE;
        }
        return QualityLevel.POOR;
    }

    /**
     * Gets overall quality score (0.0 to 1.0).
     *
     * @return overall score
     */
    public double getOverallScore() {
        return overallScore;
    }

    /** Alias for {@link #getOverallScore()}. */
    public double getScore() {
        return overallScore;
    }

    /**
     * Gets clarity score - how well-defined and unambiguous.
     *
     * @return clarity score (0.0 to 1.0)
     */
    public double getClarityScore() {
        return clarityScore;
    }

    /**
     * Gets completeness score - presence of all necessary information.
     *
     * @return completeness score (0.0 to 1.0)
     */
    public double getCompletenessScore() {
        return completenessScore;
    }

    /**
     * Gets testability score - how easily it can be verified.
     *
     * @return testability score (0.0 to 1.0)
     */
    public double getTestabilityScore() {
        return testabilityScore;
    }

    /**
     * Gets consistency score - alignment with standards.
     *
     * @return consistency score (0.0 to 1.0)
     */
    public double getConsistencyScore() {
        return consistencyScore;
    }

    /**
     * Gets list of identified quality issues.
     *
     * @return unmodifiable list of issues
     */
    public List<QualityIssue> getIssues() {
        return issues;
    }

    /**
     * Gets list of improvement recommendations.
     *
     * @return unmodifiable list of recommendations
     */
    public List<String> getRecommendations() {
        return recommendations;
    }

    /**
     * Gets quality level category.
     *
     * @return quality level
     */
    public QualityLevel getQualityLevel() {
        return qualityLevel;
    }

    /**
     * Checks if quality is acceptable (>= 0.5).
     *
     * @return true if acceptable
     */
    public boolean isAcceptable() {
        return overallScore >= 0.5;
    }

    /**
     * Checks if there are quality issues.
     *
     * @return true if issues present
     */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    /**
     * Gets count of critical issues.
     *
     * @return count of critical issues
     */
    public int getCriticalIssueCount() {
        return (int) issues.stream()
                .filter(QualityIssue::isCritical)
                .count();
    }

    /**
     * Creates builder for RequirementQualityResult.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for RequirementQualityResult.
     */
    public static class Builder {

        private double overallScore;
        private double clarityScore;
        private double completenessScore;
        private double testabilityScore;
        private double consistencyScore = 1.0;
        private List<QualityIssue> issues = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();

        public Builder overallScore(double score) {
            this.overallScore = score;
            return this;
        }

        public Builder clarityScore(double score) {
            this.clarityScore = score;
            return this;
        }

        public Builder completenessScore(double score) {
            this.completenessScore = score;
            return this;
        }

        public Builder testabilityScore(double score) {
            this.testabilityScore = score;
            return this;
        }

        public Builder consistencyScore(double score) {
            this.consistencyScore = score;
            return this;
        }

        public Builder issues(List<QualityIssue> issues) {
            this.issues = issues != null ? issues : new ArrayList<>();
            return this;
        }

        public Builder addIssue(QualityIssue issue) {
            if (issue != null) {
                this.issues.add(issue);
            }
            return this;
        }

        public Builder recommendations(List<String> recommendations) {
            this.recommendations = recommendations != null ? recommendations : new ArrayList<>();
            return this;
        }

        public Builder addRecommendation(String recommendation) {
            if (recommendation != null && !recommendation.trim().isEmpty()) {
                this.recommendations.add(recommendation);
            }
            return this;
        }

        public RequirementQualityResult build() {
            return new RequirementQualityResult(this);
        }
    }

    /**
     * Quality level categories.
     */
    public enum QualityLevel {
        EXCELLENT("Excellent quality, ready for use"),
        GOOD("Good quality, minor improvements possible"),
        ACCEPTABLE("Acceptable quality, some improvements needed"),
        POOR("Poor quality, significant improvements required");

        private final String description;

        QualityLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Represents a quality issue.
     */
    public static class QualityIssue {

        private final String category;
        private final String description;
        private final boolean critical;

        public QualityIssue(String category, String description, boolean critical) {
            this.category = Objects.requireNonNull(category);
            this.description = Objects.requireNonNull(description);
            this.critical = critical;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        public boolean isCritical() {
            return critical;
        }

        @Override
        public String toString() {
            return (critical ? "[CRITICAL] " : "") + category + ": " + description;
        }
    }

    @Override
    public String toString() {
        return "RequirementQualityResult{"
                + "overallScore=" + overallScore
                + ", qualityLevel=" + qualityLevel
                + ", issuesCount=" + issues.size()
                + ", criticalIssues=" + getCriticalIssueCount()
                + '}';
    }
}
