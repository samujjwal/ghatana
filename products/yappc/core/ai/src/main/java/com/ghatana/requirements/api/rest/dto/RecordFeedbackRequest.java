package com.ghatana.requirements.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Request DTO for recording user feedback on a suggestion.
 *
 * <p><b>Purpose:</b> Captures user feedback on suggestions for the learning loop.
 * Enables improvement of suggestion ranking and persona calibration.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   POST /api/suggestions/{id}/feedback
 *   Content-Type: application/json
 *
 *   {
 *     "type": "HELPFUL",
 *     "rating": 5,
 *     "feedbackText": "Great security suggestion!"
 *   }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Request DTO for suggestion feedback
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @since 1.0.0
 */
public final class RecordFeedbackRequest {
  private final String type;
  private final Integer rating;
  private final String feedbackText;

  /**
   * Create a feedback request.
   *
   * @param type feedback type (HELPFUL, NOT_HELPFUL, DUPLICATE, INVALID, UNCLEAR)
   * @param rating optional star rating (1-5)
   * @param feedbackText optional detailed feedback
   * @throws NullPointerException if type is null
   * @throws IllegalArgumentException if rating is outside [1, 5]
   */
  @JsonCreator
  public RecordFeedbackRequest(
      @JsonProperty("type") String type,
      @JsonProperty("rating") Integer rating,
      @JsonProperty("feedbackText") String feedbackText) {
    this.type = Objects.requireNonNull(type, "type cannot be null");
    if (rating != null && (rating < 1 || rating > 5)) {
      throw new IllegalArgumentException("rating must be in [1, 5], got: " + rating);
    }
    this.rating = rating;
    this.feedbackText = feedbackText;
  }

  public String getType() { return type; }
  public Integer getRating() { return rating; }
  public String getFeedbackText() { return feedbackText; }

  /**
   * Check if this request has a rating.
   *
   * @return true if rating was provided
   */
  public boolean hasRating() {
    return rating != null;
  }
}