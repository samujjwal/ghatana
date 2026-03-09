package com.ghatana.requirements.ai.feedback;

import com.ghatana.requirements.ai.suggestions.AISuggestion;
import io.activej.promise.Promise;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for learning from user feedback to improve suggestions.
 *
 * <p><b>Purpose:</b> Implements the feedback loop that continuously improves
 * suggestion ranking, quality, and persona calibration based on user feedback.
 * Enables the system to learn which personas and approaches work best for
 * different requirement types.
 *
 * <p><b>Thread Safety:</b> Thread-safe. All operations return Promise for
 * non-blocking async execution.
 *
 * <p><b>Learning Mechanisms:</b>
 * <ul>
 *   <li><b>Relevance Adjustment:</b> Increase scores for helpful suggestions</li>
 *   <li><b>Persona Calibration:</b> Track which personas generate helpful suggestions</li>
 *   <li><b>Duplicate Detection:</b> Improve filtering of overlapping suggestions</li>
 *   <li><b>Quality Metrics:</b> Monitor LLM output quality and adjust prompts</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   FeedbackLearningService learner = new FeedbackLearningService(metrics);
 *
 *   // Process user feedback
 *   SuggestionFeedback feedback = new SuggestionFeedback(
 *       "suggestion-123",
 *       FeedbackType.HELPFUL,
 *       "Great security insight!",
 *       5,
 *       "user-456"
 *   );
 *
 *   Promise<Void> result = learner.processFeedback(suggestion, feedback)
 *       .then(r -> {
 *           // Update suggestion ranking
 *           // Calibrate persona weights
 *           // Update metrics
 *           return Promise.complete();
 *       });
 * }</pre>
 *
 * @see SuggestionFeedback
 * @see FeedbackType
 * @doc.type class
 * @doc.purpose Service for learning from feedback to improve suggestions
 * @doc.layer product
 * @doc.pattern Service
 * @since 1.0.0
 */
public final class FeedbackLearningService {
  private static final Logger logger = LoggerFactory.getLogger(FeedbackLearningService.class);

  /**
   * Create a feedback learning service.
   *
   * <p>Currently a stub. Production implementation should inject metrics
   * and persistence providers.
   */
  public FeedbackLearningService() {
    logger.info("FeedbackLearningService initialized");
  }

  /**
   * Process user feedback to improve suggestion ranking.
   *
   * <p>Implements learning loop:
   * 1. Validate feedback
   * 2. Update suggestion scores based on feedback type
   * 3. Update persona calibration weights
   * 4. Record metrics for analysis
   * 5. Return updated suggestion
   *
   * @param suggestion the suggestion being reviewed (non-null)
   * @param feedback the user feedback (non-null)
   * @return Promise resolving to updated suggestion with new scores
   */
  public Promise<AISuggestion> processFeedback(
      AISuggestion suggestion, SuggestionFeedback feedback) {
    Objects.requireNonNull(suggestion, "suggestion cannot be null");
    Objects.requireNonNull(feedback, "feedback cannot be null");

    logger.info(
        "Processing feedback for suggestion: {}, type: {}",
        suggestion.requirementId(),
        feedback.type());

    // STUB: Replace with actual feedback processing
    // Production implementation should:
    // 1. Compute score adjustments based on feedback type
    // 2. Update persona calibration
    // 3. Update suggestion status
    // 4. Record learning events
    // 5. Return updated suggestion

    AISuggestion updated = suggestion.withStatus(computeNewStatus(feedback));
    float newRelevance = adjustRelevanceScore(suggestion.relevanceScore(), feedback);
    float newPriority = adjustPriorityScore(suggestion.priorityScore(), feedback);

    AISuggestion finalSuggestion = updated.withScores(newRelevance, newPriority);

    return Promise.of(finalSuggestion);
  }

  /**
   * Compute new suggestion status based on feedback.
   *
   * @param feedback the feedback provided
   * @return new status
   */
  private com.ghatana.requirements.ai.suggestions.SuggestionStatus computeNewStatus(
      SuggestionFeedback feedback) {
    if (feedback.suggestsRemoval()) {
      return com.ghatana.requirements.ai.suggestions.SuggestionStatus.REJECTED;
    }
    if (feedback.isPositive()) {
      return com.ghatana.requirements.ai.suggestions.SuggestionStatus.APPROVED;
    }
    return com.ghatana.requirements.ai.suggestions.SuggestionStatus.PENDING;
  }

  /**
   * Adjust relevance score based on feedback.
   *
   * <p>Increases score for positive feedback, decreases for negative.
   *
   * @param currentScore current relevance score
   * @param feedback user feedback
   * @return adjusted score (clamped to [0, 1])
   */
  private float adjustRelevanceScore(float currentScore, SuggestionFeedback feedback) {
    float adjustment = 0;

    // Adjust based on feedback type
    if (feedback.type() == FeedbackType.HELPFUL) {
      adjustment = 0.1f;
    } else if (feedback.type() == FeedbackType.NOT_HELPFUL) {
      adjustment = -0.1f;
    } else if (feedback.type() == FeedbackType.DUPLICATE
        || feedback.type() == FeedbackType.INVALID) {
      adjustment = -0.15f;
    } else if (feedback.type() == FeedbackType.UNCLEAR) {
      adjustment = -0.05f;
    }

    // Adjust based on rating if provided
    if (feedback.hasRating()) {
      int rating = feedback.rating();
      float ratingAdjustment = (rating - 3) * 0.05f; // -0.1 to +0.1
      adjustment = Math.max(adjustment, ratingAdjustment);
    }

    return Math.max(0, Math.min(1, currentScore + adjustment));
  }

  /**
   * Adjust priority score based on feedback.
   *
   * <p>Generally less sensitive than relevance adjustment.
   *
   * @param currentScore current priority score
   * @param feedback user feedback
   * @return adjusted score (clamped to [0, 1])
   */
  private float adjustPriorityScore(float currentScore, SuggestionFeedback feedback) {
    float adjustment = 0;

    // Priority increases slightly for helpful feedback
    if (feedback.isPositive()) {
      adjustment = 0.05f;
    }
    // Priority decreases slightly for negative feedback
    else if (feedback.isNegative()) {
      adjustment = -0.05f;
    }

    return Math.max(0, Math.min(1, currentScore + adjustment));
  }

  /**
   * Analyze persona performance based on feedback.
   *
   * <p>Returns metrics on which personas generate the most helpful suggestions.
   * Used to improve persona weighting in future generation.
   *
   * @param feedbackList list of feedback entries
   * @return map of persona to helpfulness score
   */
  public Promise<java.util.Map<com.ghatana.requirements.ai.persona.Persona, Float>> analyzePersonaPerformance(
      java.util.List<SuggestionFeedback> feedbackList) {
    java.util.Map<com.ghatana.requirements.ai.persona.Persona, Float> personaScores =
        new java.util.HashMap<>();

    // STUB: Aggregate feedback by persona
    // Production should:
    // 1. Load suggestions for each feedback entry
    // 2. Aggregate metrics by persona
    // 3. Return persona performance scores

    return Promise.of(personaScores);
  }

  /**
   * Get learning metrics and statistics.
   *
   * @return Promise resolving to learning metrics
   */
  public Promise<java.util.Map<String, Object>> getLearningMetrics() {
    // STUB: Return metrics on feedback processing
    java.util.Map<String, Object> metrics = new java.util.HashMap<>();
    metrics.put("feedbackProcessed", 0L);
    metrics.put("avgRelevanceAdjustment", 0.0f);
    metrics.put("personasEvaluated", 0);

    return Promise.of(metrics);
  }
}