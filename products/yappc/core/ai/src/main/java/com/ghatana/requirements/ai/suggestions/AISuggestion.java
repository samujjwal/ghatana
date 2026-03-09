package com.ghatana.requirements.ai.suggestions;

import com.ghatana.requirements.ai.persona.Persona;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing an AI-generated requirement suggestion.
 *
 * <p><b>Purpose:</b> Encapsulates a suggestion with metadata about its origin,
 * quality scores, status, and user feedback. Central to the suggestion ranking
 * and feedback learning loop.
 *
 * <p><b>Thread Safety:</b> Completely immutable. Safe to share across threads.
 * All fields are final and either primitive or immutable reference types.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   AISuggestion suggestion = new AISuggestion(
 *       "req-123",
 *       "Add OAuth2 authentication support",
 *       Persona.DEVELOPER,
 *       0.85f,  // relevance
 *       0.75f,  // priority
 *       SuggestionStatus.PENDING,
 *       "user-456",
 *       null    // no feedback yet
 *   );
 *
 *   if (suggestion.relevanceScore() > 0.8f) {
 *       displayToUser(suggestion);
 *   }
 *
 *   // When user provides feedback
 *   AISuggestion updated = suggestion.withStatus(SuggestionStatus.APPROVED);
 * }</pre>
 *
 * @see Persona
 * @see SuggestionStatus
 * @doc.type class
 * @doc.purpose Immutable AI-generated suggestion with metadata
 * @doc.layer product
 * @doc.pattern Value Object
 * @since 1.0.0
 */
public final class AISuggestion {
  private final String requirementId;
  private final String suggestionText;
  private final Persona persona;
  private final float relevanceScore;
  private final float priorityScore;
  private final SuggestionStatus status;
  private final String createdBy;
  private final Instant createdAt;
  private final Long feedbackId;

  /**
   * Create a new AI suggestion.
   *
   * @param requirementId the requirement this suggestion is for (non-null)
   * @param suggestionText the suggested requirement text (non-null, non-empty)
   * @param persona the persona who generated this suggestion (non-null)
   * @param relevanceScore how relevant is this suggestion? [0, 1]
   * @param priorityScore how important is this suggestion? [0, 1]
   * @param status the current status (non-null)
   * @param createdBy user ID who created this (may be null for system-generated)
   * @param feedbackId link to user feedback (may be null)
   * @throws NullPointerException if requirementId, suggestionText, persona, or status is null
   * @throws IllegalArgumentException if text is empty or scores are out of range
   */
  public AISuggestion(
      String requirementId,
      String suggestionText,
      Persona persona,
      float relevanceScore,
      float priorityScore,
      SuggestionStatus status,
      String createdBy,
      Long feedbackId) {
    this.requirementId = Objects.requireNonNull(requirementId, "requirementId cannot be null");
    this.suggestionText =
        Objects.requireNonNull(suggestionText, "suggestionText cannot be null");
    if (suggestionText.trim().isEmpty()) {
      throw new IllegalArgumentException("suggestionText cannot be empty");
    }
    this.persona = Objects.requireNonNull(persona, "persona cannot be null");
    if (relevanceScore < 0 || relevanceScore > 1) {
      throw new IllegalArgumentException(
          "relevanceScore must be in [0, 1], got: " + relevanceScore);
    }
    if (priorityScore < 0 || priorityScore > 1) {
      throw new IllegalArgumentException(
          "priorityScore must be in [0, 1], got: " + priorityScore);
    }
    this.relevanceScore = relevanceScore;
    this.priorityScore = priorityScore;
    this.status = Objects.requireNonNull(status, "status cannot be null");
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.feedbackId = feedbackId;
  }

  /**
 * Get the requirement this suggestion is for. */
  public String requirementId() {
    return requirementId;
  }

  /**
 * Get the suggested requirement text. */
  public String suggestionText() {
    return suggestionText;
  }

  /**
 * Get the persona that generated this suggestion. */
  public Persona persona() {
    return persona;
  }

  /**
 * Get the relevance score (how relevant to the requirement). */
  public float relevanceScore() {
    return relevanceScore;
  }

  /**
 * Get the priority score (how important is this suggestion). */
  public float priorityScore() {
    return priorityScore;
  }

  /**
 * Get the combined rank score (for sorting suggestions). */
  public float rankScore() {
    return (relevanceScore + priorityScore) / 2.0f;
  }

  /**
 * Get the current status of this suggestion. */
  public SuggestionStatus status() {
    return status;
  }

  /**
 * Get the user who created this suggestion (or null if system-generated). */
  public String createdBy() {
    return createdBy;
  }

  /**
 * Get when this suggestion was created. */
  public Instant createdAt() {
    return createdAt;
  }

  /**
 * Get the feedback ID if user provided feedback (null if none). */
  public Long feedbackId() {
    return feedbackId;
  }

  /**
 * Check if this suggestion has user feedback. */
  public boolean hasFeedback() {
    return feedbackId != null;
  }

  /**
   * Create a copy of this suggestion with a new status.
   *
   * @param newStatus the new status
   * @return new AISuggestion with updated status
   */
  public AISuggestion withStatus(SuggestionStatus newStatus) {
    return new AISuggestion(
        requirementId,
        suggestionText,
        persona,
        relevanceScore,
        priorityScore,
        newStatus,
        createdBy,
        feedbackId);
  }

  /**
   * Create a copy of this suggestion with updated feedback link.
   *
   * @param newFeedbackId the feedback ID
   * @return new AISuggestion with updated feedback link
   */
  public AISuggestion withFeedback(Long newFeedbackId) {
    return new AISuggestion(
        requirementId,
        suggestionText,
        persona,
        relevanceScore,
        priorityScore,
        status,
        createdBy,
        newFeedbackId);
  }

  /**
   * Create a copy of this suggestion with updated scores.
   *
   * @param newRelevance the new relevance score [0, 1]
   * @param newPriority the new priority score [0, 1]
   * @return new AISuggestion with updated scores
   */
  public AISuggestion withScores(float newRelevance, float newPriority) {
    return new AISuggestion(
        requirementId,
        suggestionText,
        persona,
        newRelevance,
        newPriority,
        status,
        createdBy,
        feedbackId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AISuggestion)) return false;
    AISuggestion that = (AISuggestion) o;
    return Float.compare(that.relevanceScore, relevanceScore) == 0
        && Float.compare(that.priorityScore, priorityScore) == 0
        && requirementId.equals(that.requirementId)
        && suggestionText.equals(that.suggestionText)
        && persona == that.persona
        && status == that.status
        && java.util.Objects.equals(createdBy, that.createdBy)
        && java.util.Objects.equals(feedbackId, that.feedbackId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(
        requirementId, suggestionText, persona, relevanceScore, priorityScore, status, createdBy,
        feedbackId);
  }

  @Override
  public String toString() {
    return String.format(
        "AISuggestion{req=%s, persona=%s, rank=%.2f, status=%s}",
        requirementId, persona.code(), rankScore(), status);
  }
}