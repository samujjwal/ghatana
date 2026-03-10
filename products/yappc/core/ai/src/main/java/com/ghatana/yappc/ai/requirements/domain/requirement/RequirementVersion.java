package com.ghatana.yappc.ai.requirements.domain.requirement;

import java.time.Instant;

/**
 * Immutable snapshot of a requirement at a specific point in time.
 *
 * <p><b>Purpose</b><br>
 * Implements Memento pattern to capture requirement state for version history.
 * Each version is immutable and represents a complete snapshot of the requirement
 * at a specific time, recording what changed and who made the change.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RequirementVersion v1 = new RequirementVersion(
 *     "req-123",
 *     1,
 *     "User Authentication",
 *     "System must authenticate users",
 *     RequirementType.FUNCTIONAL,
 *     RequirementPriority.MUST_HAVE,
 *     RequirementStatus.DRAFT,
 *     "user-456",
 *     "Initial creation",
 *     Instant.now()
 * );
 * 
 * // Later, after changes
 * RequirementVersion v2 = new RequirementVersion(
 *     "req-123",
 *     2,
 *     "User Authentication & MFA",
 *     "System must authenticate users with MFA support",
 *     RequirementType.FUNCTIONAL,
 *     RequirementPriority.MUST_HAVE,
 *     RequirementStatus.IN_REVIEW,
 *     "user-456",
 *     "Added MFA requirement",
 *     Instant.now()
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable record - completely thread-safe
 *
 * @doc.type record
 * @doc.purpose Requirement version snapshot
 * @doc.layer product
 * @doc.pattern Value Object, Memento
 */
public record RequirementVersion(
    /**
     * Requirement ID this version belongs to.
     */
    String requirementId,

    /**
     * Version number (1-based, monotonic).
     */
    int versionNumber,

    /**
     * Requirement title at this version.
     */
    String title,

    /**
     * Requirement description at this version.
     */
    String description,

    /**
     * Requirement type at this version.
     */
    RequirementType type,

    /**
     * Requirement priority at this version.
     */
    RequirementPriority priority,

    /**
     * Requirement status at this version.
     */
    RequirementStatus status,

    /**
     * User ID who made this change.
     */
    String changedBy,

    /**
     * Reason for this change/version.
     */
    String changeReason,

    /**
     * Timestamp of this version creation.
     */
    Instant changedAt) {

  /**
   * Create requirement version with validation.
   *
   * @param requirementId requirement ID
   * @param versionNumber version number
   * @param title title
   * @param description description
   * @param type requirement type
   * @param priority priority
   * @param status status
   * @param changedBy user making change
   * @param changeReason reason for change
   * @param changedAt timestamp
   */
  public RequirementVersion {
    if (versionNumber <= 0) {
      throw new IllegalArgumentException("Version number must be positive: " + versionNumber);
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Title cannot be empty");
    }
  }

  /**
   * Check if this is the initial version.
   *
   * @return true if version number is 1
   */
  public boolean isInitialVersion() {
    return versionNumber == 1;
  }

  /**
   * Check if this version is approved.
   *
   * @return true if status is APPROVED
   */
  public boolean isApproved() {
    return status == RequirementStatus.APPROVED;
  }

  /**
   * Check if this version is critical (MUST_HAVE).
   *
   * @return true if priority is MUST_HAVE
   */
  public boolean isCritical() {
    return priority.isCritical();
  }

  /**
   * Get human-readable version description.
   *
   * @return version description string
   */
  public String getVersionDescription() {
    return "v" + versionNumber + ": " + changeReason + " (by " + changedBy + ")";
  }
}