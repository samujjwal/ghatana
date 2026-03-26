package com.ghatana.datacloud.entity.quality;

import java.util.Objects;

/**
 * Immutable value object representing quality metrics for entity content.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates quality scoring dimensions (completeness, consistency, accuracy, relevance)
 * as measurable 0-100 scores. Used by QualityScoringService to evaluate entity quality
 * and provide recommendations for improvement.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QualityMetrics metrics = QualityMetrics.builder()
 *     .completeness(85)
 *     .consistency(90)
 *     .accuracy(92)
 *     .relevance(78)
 *     .build();
 *
 * double overall = metrics.getOverallScore(); // Weighted average
 * }</pre>
 *
 * <p><b>Scoring Dimensions</b><br>
 * - <b>Completeness</b> (0-100): Percentage of required fields populated
 * - <b>Consistency</b> (0-100): Internal consistency (format, type alignment)
 * - <b>Accuracy</b> (0-100): Factual correctness and validation rules
 * - <b>Relevance</b> (0-100): Content relevance to entity type and context
 *
 * <p><b>Overall Score</b><br>
 * Weighted average: (completeness × 0.25) + (consistency × 0.25) + (accuracy × 0.30) + (relevance × 0.20)
 *
 * @doc.type record
 * @doc.purpose Quality metrics value object for entity evaluation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class QualityMetrics {
  private final int completeness;
  private final int consistency;
  private final int accuracy;
  private final int relevance;

  private QualityMetrics(int completeness, int consistency, int accuracy, int relevance) {
    validateScore(completeness, "completeness");
    validateScore(consistency, "consistency");
    validateScore(accuracy, "accuracy");
    validateScore(relevance, "relevance");

    this.completeness = completeness;
    this.consistency = consistency;
    this.accuracy = accuracy;
    this.relevance = relevance;
  }

  /**
   * Creates a new builder for constructing QualityMetrics.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates QualityMetrics with all scores set to the same value.
   *
   * @param score score 0-100 for all dimensions
   * @return QualityMetrics instance
   * @throws IllegalArgumentException if score not in range [0, 100]
   */
  public static QualityMetrics uniform(int score) {
    return builder().completeness(score).consistency(score).accuracy(score).relevance(score).build();
  }

  /**
   * Creates QualityMetrics from explicit scores.
   *
   * @param completeness completeness score 0-100
   * @param consistency consistency score 0-100
   * @param accuracy accuracy score 0-100
   * @param relevance relevance score 0-100
   * @return QualityMetrics instance
   * @throws IllegalArgumentException if any score not in range [0, 100]
   */
  public static QualityMetrics of(int completeness, int consistency, int accuracy, int relevance) {
    return new QualityMetrics(completeness, consistency, accuracy, relevance);
  }

  /**
   * Validates that a score is in valid range [0, 100].
   *
   * @param score score to validate
   * @param name field name for error messages
   * @throws IllegalArgumentException if score not in valid range
   */
  private static void validateScore(int score, String name) {
    if (score < 0 || score > 100) {
      throw new IllegalArgumentException(
          String.format("%s score must be in range [0, 100], got %d", name, score));
    }
  }

  /**
   * Gets completeness score (0-100).
   *
   * <p>Represents percentage of required fields populated and non-null.
   *
   * @return completeness score
   */
  public int getCompleteness() {
    return completeness;
  }

  /**
   * Gets consistency score (0-100).
   *
   * <p>Represents internal consistency of field formats and types.
   *
   * @return consistency score
   */
  public int getConsistency() {
    return consistency;
  }

  /**
   * Gets accuracy score (0-100).
   *
   * <p>Represents factual correctness and validation rule compliance.
   *
   * @return accuracy score
   */
  public int getAccuracy() {
    return accuracy;
  }

  /**
   * Gets relevance score (0-100).
   *
   * <p>Represents content relevance to entity type and context.
   *
   * @return relevance score
   */
  public int getRelevance() {
    return relevance;
  }

  /**
   * Calculates weighted overall quality score.
   *
   * <p>Formula: (completeness × 0.25) + (consistency × 0.25) + (accuracy × 0.30) + (relevance ×
   * 0.20)
   *
   * @return overall score 0-100 (rounded to nearest integer)
   */
  public int getOverallScore() {
    return Math.round(
        (completeness * 0.25f) + (consistency * 0.25f) + (accuracy * 0.30f) + (relevance * 0.20f));
  }

  /**
   * Determines quality level based on overall score.
   *
   * <p>Levels:
   * <ul>
   *   <li>EXCELLENT: 90-100
   *   <li>GOOD: 75-89
   *   <li>FAIR: 60-74
   *   <li>POOR: 0-59
   * </ul>
   *
   * @return quality level
   */
  public QualityLevel getQualityLevel() {
    int overall = getOverallScore();
    if (overall >= 90) {
      return QualityLevel.EXCELLENT;
    } else if (overall >= 75) {
      return QualityLevel.GOOD;
    } else if (overall >= 60) {
      return QualityLevel.FAIR;
    } else {
      return QualityLevel.POOR;
    }
  }

  /**
   * Identifies the most critical dimension needing improvement.
   *
   * @return the dimension with lowest score
   */
  public String getMostCriticalDimension() {
    int min = Math.min(Math.min(completeness, consistency), Math.min(accuracy, relevance));
    if (min == completeness) return "completeness";
    if (min == consistency) return "consistency";
    if (min == accuracy) return "accuracy";
    return "relevance";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    QualityMetrics that = (QualityMetrics) o;
    return completeness == that.completeness
        && consistency == that.consistency
        && accuracy == that.accuracy
        && relevance == that.relevance;
  }

  @Override
  public int hashCode() {
    return Objects.hash(completeness, consistency, accuracy, relevance);
  }

  @Override
  public String toString() {
    return String.format(
        "QualityMetrics{overall=%d, completeness=%d, consistency=%d, accuracy=%d, relevance=%d}",
        getOverallScore(), completeness, consistency, accuracy, relevance);
  }

  /**
   * Builder for constructing QualityMetrics instances with fluent API.
   */
  public static final class Builder {
    private int completeness = 0;
    private int consistency = 0;
    private int accuracy = 0;
    private int relevance = 0;

    public Builder completeness(int completeness) {
      this.completeness = completeness;
      return this;
    }

    public Builder consistency(int consistency) {
      this.consistency = consistency;
      return this;
    }

    public Builder accuracy(int accuracy) {
      this.accuracy = accuracy;
      return this;
    }

    public Builder relevance(int relevance) {
      this.relevance = relevance;
      return this;
    }

    /**
     * Builds the QualityMetrics instance.
     *
     * @return configured QualityMetrics
     * @throws IllegalArgumentException if any score not in valid range
     */
    public QualityMetrics build() {
      return new QualityMetrics(completeness, consistency, accuracy, relevance);
    }
  }
}
