package com.ghatana.datacloud.entity.quality;

/**
 * Enumeration of quality levels based on overall quality score.
 *
 * <p><b>Purpose</b><br>
 * Provides human-readable classification of entity quality for reporting and
 * decision-making. Maps numeric scores to descriptive categories.
 *
 * <p><b>Levels</b><br>
 * - <b>EXCELLENT</b>: 90-100 - Production-ready, minimal improvements needed
 * - <b>GOOD</b>: 75-89 - Acceptable quality, some improvements recommended
 * - <b>FAIR</b>: 60-74 - Quality concerns, improvements advised
 * - <b>POOR</b>: 0-59 - Significant issues, major improvements required
 *
 * @doc.type enum
 * @doc.purpose Quality level classification
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum QualityLevel {
  /**
   * Excellent quality (90-100 score). Production-ready with minimal improvements needed.
   * Indicates high completeness, consistency, accuracy, and relevance.
   */
  EXCELLENT("Excellent", 90),

  /**
   * Good quality (75-89 score). Acceptable for most use cases with some improvements
   * recommended. Generally meets requirements with minor gaps.
   */
  GOOD("Good", 75),

  /**
   * Fair quality (60-74 score). Quality concerns present, improvements advised before
   * production use. Notable gaps in one or more dimensions.
   */
  FAIR("Fair", 60),

  /**
   * Poor quality (0-59 score). Significant issues present, major improvements required.
   * Should not be used without substantial rework.
   */
  POOR("Poor", 0);

  private final String displayName;
  private final int minScore;

  QualityLevel(String displayName, int minScore) {
    this.displayName = displayName;
    this.minScore = minScore;
  }

  /**
   * Gets human-readable display name for this quality level.
   *
   * @return display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Gets minimum score (inclusive) for this quality level.
   *
   * @return minimum score 0-100
   */
  public int getMinScore() {
    return minScore;
  }

  /**
   * Determines quality level for given score.
   *
   * @param score overall quality score 0-100
   * @return corresponding quality level
   * @throws IllegalArgumentException if score not in valid range
   */
  public static QualityLevel fromScore(int score) {
    if (score < 0 || score > 100) {
      throw new IllegalArgumentException(
          String.format("Score must be in range [0, 100], got %d", score));
    }
    if (score >= 90) {
      return EXCELLENT;
    } else if (score >= 75) {
      return GOOD;
    } else if (score >= 60) {
      return FAIR;
    } else {
      return POOR;
    }
  }

  /**
   * Checks if this level is acceptable for production use.
   *
   * <p>EXCELLENT and GOOD are considered production-ready.
   *
   * @return true if this level is acceptable for production
   */
  public boolean isProductionReady() {
    return this == EXCELLENT || this == GOOD;
  }

  /**
   * Checks if this level indicates quality concerns.
   *
   * <p>FAIR and POOR indicate quality concerns requiring attention.
   *
   * @return true if this level indicates quality concerns
   */
  public boolean hasQualityConcerns() {
    return this == FAIR || this == POOR;
  }
}
