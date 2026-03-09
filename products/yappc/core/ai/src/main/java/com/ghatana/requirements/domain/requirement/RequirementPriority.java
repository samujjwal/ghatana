package com.ghatana.requirements.domain.requirement;

/**
 * Requirement priority using MoSCoW prioritization scheme.
 *
 * <p><b>Purpose</b><br>
 * Prioritizes requirements using the MoSCoW method:
 * - Must Have: Critical, required for MVP
 * - Should Have: Important, should be included
 * - Could Have: Nice to have, can be deferred
 * - Won't Have: Explicitly out of scope
 *
 * <p><b>Weight System</b><br>
 * Each priority level has a numeric weight for sorting and scheduling.
 * Higher weight = higher priority.
 *
 * @doc.type enum
 * @doc.purpose MoSCoW priority scheme
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum RequirementPriority {
  /**
 * Must have - critical for MVP (weight 4) */
  MUST_HAVE(4, "Must Have"),
  /**
 * Should have - important, included if possible (weight 3) */
  SHOULD_HAVE(3, "Should Have"),
  /**
 * Could have - nice to have, can be deferred (weight 2) */
  COULD_HAVE(2, "Could Have"),
  /**
 * Won't have - explicitly out of scope (weight 1) */
  WONT_HAVE(1, "Won't Have");

  private final int weight;
  private final String displayName;

  /**
   * Create requirement priority.
   *
   * @param weight numeric priority weight
   * @param displayName human-readable display name
   */
  RequirementPriority(int weight, String displayName) {
    this.weight = weight;
    this.displayName = displayName;
  }

  /**
   * Get priority weight for sorting.
   *
   * @return numeric weight (1-4)
   */
  public int getWeight() {
    return weight;
  }

  /**
   * Get display name.
   *
   * @return human-readable name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Compare priorities by weight.
   *
   * @param other other priority to compare
   * @return comparison result (-1, 0, 1)
   */
  public int compareByWeight(RequirementPriority other) {
    return Integer.compare(this.weight, other.weight);
  }

  /**
   * Check if this priority is critical (MUST_HAVE).
   *
   * @return true if MUST_HAVE
   */
  public boolean isCritical() {
    return this == MUST_HAVE;
  }
}