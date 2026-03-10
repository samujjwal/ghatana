package com.ghatana.yappc.ai.requirements.ai.feedback;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import io.activej.promise.Promise;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
 * non-blocking async execution. In-memory counters use atomic operations.
 *
 * <p><b>Learning Mechanisms:</b>
 * <ul>
 *   <li><b>Relevance Adjustment:</b> Increase scores for helpful suggestions</li>
 *   <li><b>Persona Calibration:</b> Track which personas generate helpful suggestions</li>
 *   <li><b>Duplicate Detection:</b> Improve filtering of overlapping suggestions</li>
 *   <li><b>Quality Metrics:</b> Monitor LLM output quality and adjust prompts</li>
 * </ul>
 *
 * @see SuggestionFeedback
 * @see FeedbackType
 * @doc.type class
 * @doc.purpose Service for learning from feedback to improve AI suggestion quality
 * @doc.layer product
 * @doc.pattern Service
 * @since 1.0.0
 */
public final class FeedbackLearningService {
  private static final Logger logger = LoggerFactory.getLogger(FeedbackLearningService.class);

  private final MetricsCollector metrics;

  // In-memory learning statistics (survives for the JVM lifetime)
  private final AtomicLong feedbackCount = new AtomicLong(0);
  private final AtomicLong helpfulCount = new AtomicLong(0);
  private final AtomicLong notHelpfulCount = new AtomicLong(0);
  private final AtomicReference<Double> runningRelevanceDelta = new AtomicReference<>(0.0);
  private final Map<FeedbackType, AtomicLong> feedbackByType = buildTypeCounters();

  /**
   * Create a feedback learning service with observability metrics.
   *
   * @param metrics collector for recording feedback processing events (non-null)
   */
  public FeedbackLearningService(MetricsCollector metrics) {
    this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    logger.info("FeedbackLearningService initialized with MetricsCollector: {}",
        metrics.getClass().getSimpleName());
  }

  /**
   * Create a feedback learning service without metrics (no-op metrics).
   *
   * <p>Use this constructor in test or development environments only.
   */
  public FeedbackLearningService() {
    this(MetricsCollector.create());
    logger.warn("FeedbackLearningService created without MetricsCollector — using noop metrics");
  }

  /**
   * Process user feedback to improve suggestion ranking.
   *
   * <p>Implements learning loop:
   * <ol>
   *   <li>Validate feedback</li>
   *   <li>Update suggestion scores based on feedback type</li>
   *   <li>Update in-memory learning counters</li>
   *   <li>Record observability metrics</li>
   *   <li>Return updated suggestion with adjusted scores</li>
   * </ol>
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

    // Compute score adjustments
    float newRelevance = adjustRelevanceScore(suggestion.relevanceScore(), feedback);
    float newPriority = adjustPriorityScore(suggestion.priorityScore(), feedback);
    float relevanceDelta = newRelevance - suggestion.relevanceScore();

    // Compute new status
    AISuggestion updated = suggestion.withStatus(computeNewStatus(feedback));
    AISuggestion finalSuggestion = updated.withScores(newRelevance, newPriority);

    // Update in-memory learning counters (thread-safe)
    feedbackCount.incrementAndGet();
    feedbackByType.get(feedback.type()).incrementAndGet();
    if (feedback.isPositive()) {
      helpfulCount.incrementAndGet();
    } else if (feedback.isNegative()) {
      notHelpfulCount.incrementAndGet();
    }
    // Cumulative running average of delta (approximate, lock-free)
    runningRelevanceDelta.updateAndGet(prev -> {
      long n = feedbackCount.get();
      return prev + (relevanceDelta - prev) / n; // Welford-style incremental avg
    });

    // Record observability metrics
    metrics.incrementCounter("feedback.processed");
    metrics.incrementCounter("feedback.processed.by_type", "type", feedback.type().name());
    if (feedback.hasRating()) {
      metrics.recordGauge("feedback.rating", (long) feedback.rating());
    }
    metrics.recordConfidenceScore("feedback.relevance_delta",
        Math.max(0, Math.min(1, (double) Math.abs(relevanceDelta))));

    logger.debug(
        "Feedback applied: type={} relevanceDelta={} newStatus={}",
        feedback.type(), relevanceDelta, finalSuggestion.status());

    return Promise.of(finalSuggestion);
  }

  /**
   * Compute new suggestion status based on feedback.
   *
   * @param feedback the feedback provided
   * @return new status
   */
  private com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus computeNewStatus(
      SuggestionFeedback feedback) {
    if (feedback.suggestsRemoval()) {
      return com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus.REJECTED;
    }
    if (feedback.isPositive()) {
      return com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus.APPROVED;
    }
    return com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus.PENDING;
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
    float adjustment = switch (feedback.type()) {
      case HELPFUL -> 0.10f;
      case NOT_HELPFUL -> -0.10f;
      case DUPLICATE, INVALID -> -0.15f;
      case UNCLEAR -> -0.05f;
      default -> 0.0f;
    };

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
    if (feedback.isPositive()) {
      adjustment = 0.05f;
    } else if (feedback.isNegative()) {
      adjustment = -0.05f;
    }
    return Math.max(0, Math.min(1, currentScore + adjustment));
  }

  /**
   * Analyze feedback distribution across feedback types.
   *
   * <p><b>Note:</b> Full persona-level analysis requires a {@code SuggestionRepository}
   * to correlate feedback with the generating persona. Without it this method
   * provides aggregate quality scores derived from the raw feedback distribution.
   * Scores range [0, 1] where 1.0 = fully positive, 0.0 = fully negative.
   *
   * @param feedbackList list of feedback entries (non-null, may be empty)
   * @return map of FeedbackType to normalized quality score for that category
   */
  public Promise<Map<com.ghatana.yappc.ai.requirements.ai.persona.Persona, Float>> analyzePersonaPerformance(
      List<SuggestionFeedback> feedbackList) {
    Objects.requireNonNull(feedbackList, "feedbackList cannot be null");

    // Aggregate positive vs negative count per type across the provided list
    long positiveCount = feedbackList.stream().filter(SuggestionFeedback::isPositive).count();
    long negativeCount = feedbackList.stream().filter(SuggestionFeedback::isNegative).count();
    long totalCount = feedbackList.size();

    float overallScore = totalCount > 0
        ? (float) positiveCount / totalCount
        : 0.5f; // neutral when no data

    logger.info(
        "Persona performance analysis: feedbackCount={} positive={} negative={} overallScore={}",
        totalCount, positiveCount, negativeCount, overallScore);

    metrics.recordConfidenceScore("feedback.persona_analysis.overall_score", (double) overallScore);
    metrics.incrementCounter("feedback.persona_analysis.runs");

    // Return an empty persona map — real persona attribution requires SuggestionRepository.
    // This is a known TODO: wire SuggestionRepository into FeedbackLearningService.
    // TODO: Load suggestion by ID → get generating persona → aggregate score per persona
    Map<com.ghatana.yappc.ai.requirements.ai.persona.Persona, Float> personaScores = new HashMap<>();
    return Promise.of(personaScores);
  }

  /**
   * Get learning metrics and statistics for the current JVM session.
   *
   * @return Promise resolving to learning metrics map
   */
  public Promise<Map<String, Object>> getLearningMetrics() {
    Map<String, Object> metricsMap = new HashMap<>();
    metricsMap.put("feedbackProcessed", feedbackCount.get());
    metricsMap.put("helpfulCount", helpfulCount.get());
    metricsMap.put("notHelpfulCount", notHelpfulCount.get());
    metricsMap.put("avgRelevanceAdjustment", runningRelevanceDelta.get());
    metricsMap.put("helpfulRate",
        feedbackCount.get() > 0
            ? (double) helpfulCount.get() / feedbackCount.get()
            : 0.0);

    // Per-type breakdown
    Map<String, Long> byType = new HashMap<>();
    feedbackByType.forEach((type, counter) -> byType.put(type.name(), counter.get()));
    metricsMap.put("feedbackByType", byType);

    return Promise.of(metricsMap);
  }

  /** Builds a thread-safe counter map for all FeedbackType values. */
  private static Map<FeedbackType, AtomicLong> buildTypeCounters() {
    Map<FeedbackType, AtomicLong> counters = new EnumMap<>(FeedbackType.class);
    for (FeedbackType type : FeedbackType.values()) {
      counters.put(type, new AtomicLong(0));
    }
    return counters;
  }
}

