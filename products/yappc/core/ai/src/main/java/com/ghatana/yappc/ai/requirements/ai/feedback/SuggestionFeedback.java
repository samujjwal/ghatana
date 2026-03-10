package com.ghatana.yappc.ai.requirements.ai.feedback;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing user feedback on a suggestion.
 *
 * <p><b>Purpose:</b> Captures explicit user feedback on AI suggestions,
 * enabling the learning loop to improve future suggestion ranking and quality.
 * Tracks both categorical feedback (helpful/not-helpful) and ratings (1-5 stars).
 *
 * <p><b>Thread Safety:</b> Completely immutable. Safe for concurrent access.
 *
 * <p><b>Feedback Types:</b>
 * <ul>
 *   <li><b>HELPFUL:</b> User found the suggestion valuable</li>
 *   <li><b>NOT_HELPFUL:</b> User didn't find value in suggestion</li>
 *   <li><b>DUPLICATE:</b> Suggestion is redundant with existing requirements</li>
 *   <li><b>INVALID:</b> Suggestion is incorrect or infeasible</li>
 *   <li><b>UNCLEAR:</b> Suggestion is ambiguous or poorly worded</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   SuggestionFeedback feedback = new SuggestionFeedback(
 *       "suggestion-123",
 *       FeedbackType.HELPFUL,
 *       "Great insight about security requirements!",
 *       5,  // 5-star rating
 *       "user-456"
 *   );
 *
 *   // Use for learning/ranking improvement
 *   if (feedback.type() == FeedbackType.HELPFUL && feedback.rating() >= 4) {
 *       boostRelevanceScore(suggestion);
 *   }
 * }</pre>
 *
 * @see FeedbackType
 * @doc.type class
 * @doc.purpose Immutable user feedback on suggestions with rating
 * @doc.layer product
 * @doc.pattern Value Object
 * @since 1.0.0
 */
public final class SuggestionFeedback {
  private final String suggestionId;
  private final FeedbackType type;
  private final String feedbackText;
  private final Integer rating;
  private final String userId;
  private final Instant createdAt;

  /**
   * Create user feedback on a suggestion.
   *
   * @param suggestionId the suggestion being reviewed (non-null)
   * @param type the feedback type category (non-null)
   * @param feedbackText optional detailed feedback (may be null or empty)
   * @param rating optional 1-5 star rating (may be null)
   * @param userId user who provided feedback (non-null)
   * @throws NullPointerException if suggestionId, type, or userId is null
   * @throws IllegalArgumentException if rating is outside [1, 5]
   */
  public SuggestionFeedback(
      String suggestionId,
      FeedbackType type,
      String feedbackText,
      Integer rating,
      String userId) {
    this.suggestionId = Objects.requireNonNull(suggestionId, "suggestionId cannot be null");
    this.type = Objects.requireNonNull(type, "type cannot be null");
    this.feedbackText = feedbackText != null ? feedbackText : "";
    if (rating != null && (rating < 1 || rating > 5)) {
      throw new IllegalArgumentException("rating must be in [1, 5], got: " + rating);
    }
    this.rating = rating;
    this.userId = Objects.requireNonNull(userId, "userId cannot be null");
    this.createdAt = Instant.now();
  }

  /**
 * Get the suggestion ID being reviewed. */
  public String suggestionId() {
    return suggestionId;
  }

  /**
 * Get the feedback type category. */
  public FeedbackType type() {
    return type;
  }

  /**
 * Get the detailed feedback text (may be empty). */
  public String feedbackText() {
    return feedbackText;
  }

  /**
 * Check if this feedback includes a rating. */
  public boolean hasRating() {
    return rating != null;
  }

  /**
   * Get the star rating (1-5) if provided.
   *
   * @return rating or null if not provided
   */
  public Integer rating() {
    return rating;
  }

  /**
 * Get the user ID who provided this feedback. */
  public String userId() {
    return userId;
  }

  /**
 * Get when this feedback was created. */
  public Instant createdAt() {
    return createdAt;
  }

  /**
 * Check if this is positive feedback. */
  public boolean isPositive() {
    return type == FeedbackType.HELPFUL || (hasRating() && rating >= 4);
  }

  /**
 * Check if this is negative feedback. */
  public boolean isNegative() {
    return type == FeedbackType.NOT_HELPFUL
        || type == FeedbackType.INVALID
        || (hasRating() && rating <= 2);
  }

  /**
 * Check if this feedback suggests the suggestion should be removed. */
  public boolean suggestsRemoval() {
    return type == FeedbackType.DUPLICATE
        || type == FeedbackType.INVALID
        || (hasRating() && rating == 1);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SuggestionFeedback)) return false;
    SuggestionFeedback that = (SuggestionFeedback) o;
    return suggestionId.equals(that.suggestionId)
        && type == that.type
        && feedbackText.equals(that.feedbackText)
        && java.util.Objects.equals(rating, that.rating)
        && userId.equals(that.userId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(suggestionId, type, feedbackText, rating, userId);
  }

  @Override
  public String toString() {
    return String.format(
        "SuggestionFeedback{suggestion=%s, type=%s, rating=%s, user=%s}",
        suggestionId, type, rating, userId);
  }
}