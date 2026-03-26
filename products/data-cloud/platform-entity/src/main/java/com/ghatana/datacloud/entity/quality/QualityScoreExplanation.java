package com.ghatana.datacloud.entity.quality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object providing detailed explanation of quality score.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates detailed reasoning behind quality score, including specific findings,
 * improvement recommendations, and dimension-specific feedback. Enables users to
 * understand why content received a particular score and what actions to take.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QualityScoreExplanation explanation = QualityScoreExplanation.builder()
 *     .score(82)
 *     .level(QualityLevel.GOOD)
 *     .finding("All required fields populated")
 *     .finding("Date formats inconsistent in 2 fields")
 *     .recommendation("Standardize date format to ISO 8601")
 *     .dimensionFeedback("completeness", "100% of required fields present")
 *     .dimensionFeedback("consistency", "Minor formatting issues detected")
 *     .build();
 * }</pre>
 *
 * <p><b>Components</b><br>
 * - <b>Score</b>: Overall quality score 0-100
 * - <b>Findings</b>: List of specific observations about the content
 * - <b>Recommendations</b>: List of actionable improvement suggestions
 * - <b>Dimension Feedback</b>: Score-specific feedback for each quality dimension
 *
 * @doc.type class
 * @doc.purpose Detailed quality score explanation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class QualityScoreExplanation {
  private final int score;
  private final QualityLevel level;
  private final List<String> findings;
  private final List<String> recommendations;
  private final java.util.Map<String, String> dimensionFeedback;

  private QualityScoreExplanation(
      int score,
      QualityLevel level,
      List<String> findings,
      List<String> recommendations,
      java.util.Map<String, String> dimensionFeedback) {
    if (score < 0 || score > 100) {
      throw new IllegalArgumentException(
          String.format("Score must be in range [0, 100], got %d", score));
    }
    Objects.requireNonNull(level, "Quality level must not be null");
    Objects.requireNonNull(findings, "Findings must not be null");
    Objects.requireNonNull(recommendations, "Recommendations must not be null");
    Objects.requireNonNull(dimensionFeedback, "Dimension feedback must not be null");

    this.score = score;
    this.level = level;
    this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
    this.recommendations = Collections.unmodifiableList(new ArrayList<>(recommendations));
    this.dimensionFeedback =
        Collections.unmodifiableMap(
            new java.util.LinkedHashMap<>(dimensionFeedback));
  }

  /**
   * Creates a new builder for constructing QualityScoreExplanation.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets overall quality score (0-100).
   *
   * @return quality score
   */
  public int getScore() {
    return score;
  }

  /**
   * Gets quality level classification.
   *
   * @return quality level (EXCELLENT, GOOD, FAIR, or POOR)
   */
  public QualityLevel getLevel() {
    return level;
  }

  /**
   * Gets immutable list of findings about content quality.
   *
   * <p>Findings are specific observations such as "Missing 3 required fields" or
   * "Inconsistent date format detected".
   *
   * @return unmodifiable list of findings
   */
  public List<String> getFindings() {
    return findings;
  }

  /**
   * Gets immutable list of improvement recommendations.
   *
   * <p>Recommendations are actionable suggestions to improve quality, such as
   * "Populate description field" or "Standardize date formats".
   *
   * @return unmodifiable list of recommendations
   */
  public List<String> getRecommendations() {
    return recommendations;
  }

  /**
   * Gets feedback for specific quality dimension.
   *
   * @param dimension dimension name (completeness, consistency, accuracy, relevance)
   * @return feedback message, or empty if dimension not found
   */
  public String getDimensionFeedback(String dimension) {
    return dimensionFeedback.getOrDefault(
        Objects.requireNonNull(dimension, "Dimension must not be null"), "");
  }

  /**
   * Gets immutable map of all dimension feedback.
   *
   * @return unmodifiable map of dimension → feedback
   */
  public java.util.Map<String, String> getAllDimensionFeedback() {
    return dimensionFeedback;
  }

  /**
   * Checks if explanation has findings.
   *
   * @return true if at least one finding present
   */
  public boolean hasFindings() {
    return !findings.isEmpty();
  }

  /**
   * Checks if explanation has recommendations.
   *
   * @return true if at least one recommendation present
   */
  public boolean hasRecommendations() {
    return !recommendations.isEmpty();
  }

  /**
   * Gets summary string combining score, level, and key findings.
   *
   * @return summary string
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Quality Score: %d (%s)", score, level.getDisplayName()));
    if (!findings.isEmpty()) {
      sb.append("\nFindings: ");
      sb.append(findings.get(0));
      if (findings.size() > 1) {
        sb.append(String.format(" (+%d more)", findings.size() - 1));
      }
    }
    if (!recommendations.isEmpty()) {
      sb.append("\nRecommendations: ");
      sb.append(recommendations.get(0));
      if (recommendations.size() > 1) {
        sb.append(String.format(" (+%d more)", recommendations.size() - 1));
      }
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    QualityScoreExplanation that = (QualityScoreExplanation) o;
    return score == that.score
        && level == that.level
        && Objects.equals(findings, that.findings)
        && Objects.equals(recommendations, that.recommendations)
        && Objects.equals(dimensionFeedback, that.dimensionFeedback);
  }

  @Override
  public int hashCode() {
    return Objects.hash(score, level, findings, recommendations, dimensionFeedback);
  }

  @Override
  public String toString() {
    return String.format(
        "QualityScoreExplanation{score=%d, level=%s, findings=%d, recommendations=%d}",
        score, level, findings.size(), recommendations.size());
  }

  /**
   * Builder for constructing QualityScoreExplanation instances with fluent API.
   */
  public static final class Builder {
    private int score = 0;
    private QualityLevel level = QualityLevel.POOR;
    private final List<String> findings = new ArrayList<>();
    private final List<String> recommendations = new ArrayList<>();
    private final java.util.Map<String, String> dimensionFeedback = new java.util.LinkedHashMap<>();

    public Builder score(int score) {
      this.score = score;
      this.level = QualityLevel.fromScore(score);
      return this;
    }

    public Builder level(QualityLevel level) {
      this.level = Objects.requireNonNull(level, "Level must not be null");
      return this;
    }

    public Builder finding(String finding) {
      if (finding != null && !finding.isBlank()) {
        this.findings.add(finding.strip());
      }
      return this;
    }

    public Builder findings(List<String> findings) {
      if (findings != null) {
        findings.forEach(this::finding);
      }
      return this;
    }

    public Builder recommendation(String recommendation) {
      if (recommendation != null && !recommendation.isBlank()) {
        this.recommendations.add(recommendation.strip());
      }
      return this;
    }

    public Builder recommendations(List<String> recommendations) {
      if (recommendations != null) {
        recommendations.forEach(this::recommendation);
      }
      return this;
    }

    public Builder dimensionFeedback(String dimension, String feedback) {
      if (dimension != null && !dimension.isBlank() && feedback != null && !feedback.isBlank()) {
        this.dimensionFeedback.put(dimension.strip().toLowerCase(), feedback.strip());
      }
      return this;
    }

    /**
     * Builds the QualityScoreExplanation instance.
     *
     * @return configured QualityScoreExplanation
     * @throws IllegalArgumentException if score not in valid range
     */
    public QualityScoreExplanation build() {
      return new QualityScoreExplanation(score, level, findings, recommendations, dimensionFeedback);
    }
  }
}
