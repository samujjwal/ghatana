package com.ghatana.yappc.ai.requirements.ai.feedback;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
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
  private final DataSource dataSource;

  // In-memory learning statistics (fast read path; writes are mirrored to DB)
  private final AtomicLong feedbackCount = new AtomicLong(0);
  private final AtomicLong helpfulCount = new AtomicLong(0);
  private final AtomicLong notHelpfulCount = new AtomicLong(0);
  private final AtomicReference<Double> runningRelevanceDelta = new AtomicReference<>(0.0);
  private final Map<FeedbackType, AtomicLong> feedbackByType = buildTypeCounters();

  /**
   * Create a feedback learning service with observability metrics and durable storage.
   *
   * @param metrics collector for recording feedback processing events (non-null)
   * @param dataSource JDBC data source for persisting counters (nullable; null uses in-memory only)
   */
  @Inject
  public FeedbackLearningService(MetricsCollector metrics, DataSource dataSource) {
    this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    this.dataSource = dataSource;
    loadCountersFromDb();
    logger.info("FeedbackLearningService initialized with MetricsCollector: {} and durable DB storage",
        metrics.getClass().getSimpleName());
  }

  /**
   * Create a feedback learning service without metrics (no-op metrics).
   *
   * <p>Use this constructor in test or development environments only.</p>
   * <p><b>Deprecated:</b> Use the DataSource constructor for production environments.
   */
  @Deprecated
  public FeedbackLearningService() {
    this(MetricsCollector.create(), null);
    logger.warn("FeedbackLearningService created without MetricsCollector/DataSource — using noop metrics and ephemeral counters");
  }

  private Connection getConnection() throws SQLException {
    return dataSource != null ? dataSource.getConnection() : null;
  }

  private void loadCountersFromDb() {
    if (dataSource == null) return;
    String sql = "SELECT counter_name, counter_value FROM feedback_learning_counters";
    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString("counter_name");
        long value = rs.getLong("counter_value");
        switch (name) {
          case "feedbackCount" -> feedbackCount.set(value);
          case "helpfulCount" -> helpfulCount.set(value);
          case "notHelpfulCount" -> notHelpfulCount.set(value);
          default -> {
            try {
              FeedbackType type = FeedbackType.valueOf(name);
              AtomicLong counter = feedbackByType.get(type);
              if (counter != null) counter.set(value);
            } catch (IllegalArgumentException ignored) {
              // unknown counter name
            }
          }
        }
      }
    } catch (SQLException e) {
      logger.warn("Failed to load feedback counters from DB: {}", e.getMessage());
    }
  }

  private void persistCounter(String name, long value) {
    if (dataSource == null) return;
    String upsert = "INSERT INTO feedback_learning_counters (counter_name, counter_value) VALUES (?, ?) "
        + "ON CONFLICT (counter_name) DO UPDATE SET counter_value = EXCLUDED.counter_value";
    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(upsert)) {
      ps.setString(1, name);
      ps.setLong(2, value);
      ps.executeUpdate();
    } catch (SQLException e) {
      logger.warn("Failed to persist counter {}={} to DB: {}", name, value, e.getMessage());
    }
  }

  private void persistRelevanceDelta(double value) {
    if (dataSource == null) return;
    String upsert = "INSERT INTO feedback_learning_counters (counter_name, counter_value) VALUES ('avgRelevanceAdjustment', ?) "
        + "ON CONFLICT (counter_name) DO UPDATE SET counter_value = EXCLUDED.counter_value";
    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(upsert)) {
      ps.setDouble(1, value);
      ps.executeUpdate();
    } catch (SQLException e) {
      logger.warn("Failed to persist relevance delta to DB: {}", e.getMessage());
    }
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

    // Update in-memory learning counters (thread-safe) and mirror to DB
    long currentFeedbackCount = feedbackCount.incrementAndGet();
    persistCounter("feedbackCount", currentFeedbackCount);
    long currentTypeCount = feedbackByType.get(feedback.type()).incrementAndGet();
    persistCounter(feedback.type().name(), currentTypeCount);
    if (feedback.isPositive()) {
      long currentHelpful = helpfulCount.incrementAndGet();
      persistCounter("helpfulCount", currentHelpful);
    } else if (feedback.isNegative()) {
      long currentNotHelpful = notHelpfulCount.incrementAndGet();
      persistCounter("notHelpfulCount", currentNotHelpful);
    }
    // Cumulative running average of delta (approximate, lock-free)
    Double currentDelta = runningRelevanceDelta.updateAndGet(prev -> {
      long n = feedbackCount.get();
      return prev + (relevanceDelta - prev) / n; // Welford-style incremental avg
    });
    persistRelevanceDelta(currentDelta);

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
    // Persona attribution not yet implemented: would need SuggestionRepository to load
    // suggestion by ID, get generating persona, and aggregate score per persona.
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
