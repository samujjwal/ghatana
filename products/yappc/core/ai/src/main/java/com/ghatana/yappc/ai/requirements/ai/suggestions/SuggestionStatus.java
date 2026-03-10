package com.ghatana.yappc.ai.requirements.ai.suggestions;

/**
 * Enumeration of suggestion lifecycle statuses.
 *
 * <p><b>Purpose:</b> Represents the workflow state of an AI-generated suggestion
 * from creation through approval/rejection/archival.
 *
 * <p><b>Status Transitions:</b>
 * <pre>
 * PENDING → APPROVED → ACCEPTED (incorporated into project)
 *        → REJECTED (user didn't like it)
 *        → ARCHIVED (hidden from view)
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   // Check if suggestion is actionable
 *   if (suggestion.status() == SuggestionStatus.PENDING) {
 *       displayForUserReview(suggestion);
 *   }
 *
 *   // Archive old suggestions
 *   if (suggestion.createdAt().isBefore(oneMonthAgo)) {
 *       archive(suggestion);
 *   }
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Suggestion lifecycle status enumeration
 * @doc.layer product
 * @doc.pattern Enum
 * @since 1.0.0
 */
public enum SuggestionStatus {
  /**
   * Suggestion is awaiting user review.
   *
   * <p>Initial status when suggestion is created.
   * Moves to APPROVED/REJECTED based on user action.
   */
  PENDING("Pending review"),

  /**
   * User approved the suggestion as valuable.
   *
   * <p>Indicates user found the suggestion relevant and helpful.
   * Next step: incorporate into requirement or archive.
   */
  APPROVED("User approved"),

  /**
   * User rejected the suggestion as not relevant.
   *
   * <p>Indicates user didn't find value in the suggestion.
   * Used to improve suggestion ranking for future attempts.
   */
  REJECTED("User rejected"),

  /**
   * Suggestion has been archived/hidden from view.
   *
   * <p>Final state. Suggestion is no longer shown in UI
   * but is retained for historical analysis and learning.
   */
  ARCHIVED("Archived");

  private final String displayName;

  SuggestionStatus(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Get human-readable display name.
   *
   * @return display name for UI
   */
  public String displayName() {
    return displayName;
  }

  /**
   * Check if this is a terminal status (no further transitions).
   *
   * @return true if this is ARCHIVED or final state
   */
  public boolean isTerminal() {
    return this == ARCHIVED;
  }

  /**
   * Check if this status represents user approval.
   *
   * @return true if APPROVED
   */
  public boolean isApproved() {
    return this == APPROVED;
  }

  /**
   * Check if this status represents user rejection.
   *
   * @return true if REJECTED
   */
  public boolean isRejected() {
    return this == REJECTED;
  }

  /**
   * Check if this status means suggestion is still pending action.
   *
   * @return true if PENDING
   */
  public boolean isPending() {
    return this == PENDING;
  }
}