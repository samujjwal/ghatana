package com.ghatana.yappc.ai.requirements.ai.feedback;

/**
 * Enumeration of feedback categories for suggestion evaluation.
 *
 * <p><b>Purpose:</b> Categorizes user feedback on suggestions to enable
 * targeted learning and improvement of suggestion ranking algorithms.
 *
 * <p><b>Categories:</b>
 * <ul>
 *   <li><b>HELPFUL:</b> User found suggestion valuable and relevant</li>
 *   <li><b>NOT_HELPFUL:</b> Suggestion didn't add value</li>
 *   <li><b>DUPLICATE:</b> Overlaps with existing requirements</li>
 *   <li><b>INVALID:</b> Suggestion is incorrect or infeasible</li>
 *   <li><b>UNCLEAR:</b> Suggestion is ambiguous or poorly written</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Feedback category for suggestion evaluation
 * @doc.layer product
 * @doc.pattern Enum
 * @since 1.0.0
 */
public enum FeedbackType {
  /**
   * User found the suggestion helpful and relevant.
   *
   * <p>Signal to increase ranking score for similar suggestions.
   * This persona/approach should be weighted higher.
   */
  HELPFUL("Suggestion was helpful"),

  /**
   * User didn't find value in the suggestion.
   *
   * <p>Signal to review relevance scoring. May indicate a persona
   * or approach that doesn't match user expectations.
   */
  NOT_HELPFUL("Suggestion wasn't helpful"),

  /**
   * Suggestion overlaps with existing or already-known requirements.
   *
   * <p>Signal to improve deduplication in suggestion engine.
   * We should filter out suggestions that are too similar to existing reqs.
   */
  DUPLICATE("Duplicate of existing requirement"),

  /**
   * Suggestion is incorrect, infeasible, or contradictory.
   *
   * <p>Signal to review LLM generation quality and add validation.
   * May indicate need for better persona prompts or context.
   */
  INVALID("Suggestion is incorrect or infeasible"),

  /**
   * Suggestion is ambiguous, poorly worded, or unclear.
   *
   * <p>Signal to improve suggestion generation prompts.
   * May need better formatting, clearer language, or examples.
   */
  UNCLEAR("Suggestion is ambiguous or unclear");

  private final String displayName;

  FeedbackType(String displayName) {
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
   * Check if this is positive feedback.
   *
   * @return true if HELPFUL
   */
  public boolean isPositive() {
    return this == HELPFUL;
  }

  /**
   * Check if this is negative feedback (not helpful or problematic).
   *
   * @return true if NOT_HELPFUL, INVALID, or UNCLEAR
   */
  public boolean isNegative() {
    return this == NOT_HELPFUL || this == INVALID || this == UNCLEAR;
  }

  /**
   * Check if this suggests the suggestion should be removed.
   *
   * @return true if DUPLICATE or INVALID
   */
  public boolean suggestsRemoval() {
    return this == DUPLICATE || this == INVALID;
  }
}