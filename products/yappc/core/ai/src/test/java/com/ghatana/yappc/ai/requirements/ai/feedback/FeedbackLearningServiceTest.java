package com.ghatana.yappc.ai.requirements.ai.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FeedbackLearningService} — the feedback loop engine that
 * adjusts suggestion scores and persona calibration based on user feedback.
 *
 * @doc.type class
 * @doc.purpose Unit tests for feedback processing, score adjustment, and status transitions
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FeedbackLearningService Tests")
class FeedbackLearningServiceTest extends EventloopTestBase {

  private FeedbackLearningService service;

  @BeforeEach
  void setUp() {
    service = new FeedbackLearningService();
  }

  // ===== Feedback Processing Tests =====

  @Nested
  @DisplayName("Feedback Processing")
  class FeedbackProcessing {

    @Test
    @DisplayName("Should increase relevance score for HELPFUL feedback")
    void shouldIncreaseRelevanceForHelpful() {
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f);
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.HELPFUL, "Great!", null, "user-1");

      AISuggestion updated = runPromise(
          () -> service.processFeedback(suggestion, feedback));

      assertThat(updated.relevanceScore()).isGreaterThan(0.5f);
    }

    @Test
    @DisplayName("Should decrease relevance score for NOT_HELPFUL feedback")
    void shouldDecreaseRelevanceForNotHelpful() {
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f);
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.NOT_HELPFUL, "Not useful", null, "user-1");

      AISuggestion updated = runPromise(
          () -> service.processFeedback(suggestion, feedback));

      assertThat(updated.relevanceScore()).isLessThan(0.5f);
    }

    @Test
    @DisplayName("Should decrease relevance more for DUPLICATE feedback")
    void shouldDecreaseRelevanceMoreForDuplicate() {
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f);
      SuggestionFeedback duplicate = new SuggestionFeedback(
          "sug-1", FeedbackType.DUPLICATE, "Already exists", null, "user-1");
      SuggestionFeedback notHelpful = new SuggestionFeedback(
          "sug-1", FeedbackType.NOT_HELPFUL, "Nope", null, "user-1");

      AISuggestion dupUpdated = runPromise(
          () -> service.processFeedback(suggestion, duplicate));
      AISuggestion nhUpdated = runPromise(
          () -> service.processFeedback(suggestion, notHelpful));

      // DUPLICATE should penalize more than NOT_HELPFUL
      assertThat(dupUpdated.relevanceScore()).isLessThan(nhUpdated.relevanceScore());
    }

    @Test
    @DisplayName("Should clamp relevance score to [0, 1]")
    void shouldClampRelevanceScore() {
      // Start at 0.05 — INVALID feedback should reduce but not go below 0
      AISuggestion lowScore = createSuggestion(0.05f, 0.5f);
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.INVALID, "Wrong", null, "user-1");

      AISuggestion updated = runPromise(
          () -> service.processFeedback(lowScore, feedback));

      assertThat(updated.relevanceScore()).isGreaterThanOrEqualTo(0.0f);
    }

    @Test
    @DisplayName("Should clamp relevance score at upper bound")
    void shouldClampRelevanceAtUpperBound() {
      AISuggestion highScore = createSuggestion(0.98f, 0.5f);
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.HELPFUL, "Perfect!", 5, "user-1");

      AISuggestion updated = runPromise(
          () -> service.processFeedback(highScore, feedback));

      assertThat(updated.relevanceScore()).isLessThanOrEqualTo(1.0f);
    }
  }

  // ===== Priority Score Tests =====

  @Nested
  @DisplayName("Priority Score Adjustment")
  class PriorityAdjustment {

    @Test
    @DisplayName("Should increase priority for positive feedback")
    void shouldIncreasePriorityForPositive() {
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f);
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.HELPFUL, "Useful", null, "user-1");

      AISuggestion updated = runPromise(
          () -> service.processFeedback(suggestion, feedback));

      assertThat(updated.priorityScore()).isGreaterThan(0.5f);
    }

    @Test
    @DisplayName("Should decrease priority for negative feedback")
    void shouldDecreasePriorityForNegative() {
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f);
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.NOT_HELPFUL, "Bad", null, "user-1");

      AISuggestion updated = runPromise(
          () -> service.processFeedback(suggestion, feedback));

      assertThat(updated.priorityScore()).isLessThan(0.5f);
    }
  }

  // ===== Null Handling Tests =====

  @Nested
  @DisplayName("Null Handling")
  class NullHandling {

    @Test
    @DisplayName("Should reject null suggestion")
    void shouldRejectNullSuggestion() {
      SuggestionFeedback feedback = new SuggestionFeedback(
          "sug-1", FeedbackType.HELPFUL, "Good", null, "user-1");

      assertThatThrownBy(() -> runPromise(
          () -> service.processFeedback(null, feedback)))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should reject null feedback")
    void shouldRejectNullFeedback() {
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f);

      assertThatThrownBy(() -> runPromise(
          () -> service.processFeedback(suggestion, null)))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // ===== Learning Metrics Tests =====

  @Nested
  @DisplayName("Learning Metrics")
  class LearningMetrics {

    @Test
    @DisplayName("Should return learning metrics")
    void shouldReturnMetrics() {
      Map<String, Object> metrics = runPromise(service::getLearningMetrics);

      assertThat(metrics).isNotNull();
      assertThat(metrics).containsKeys("feedbackProcessed", "avgRelevanceAdjustment");
    }
  }

  // ===== Persona Analysis Tests =====

  @Nested
  @DisplayName("Persona Analysis")
  class PersonaAnalysis {

    @Test
    @DisplayName("Should return persona performance analysis")
    void shouldReturnPersonaAnalysis() {
      var result = runPromise(
          () -> service.analyzePersonaPerformance(List.of()));

      assertThat(result).isNotNull();
    }
  }

  // ===== Test Helpers =====

  private AISuggestion createSuggestion(float relevance, float priority) {
    return new AISuggestion(
        "req-123",
        "Add OAuth2 authentication",
        Persona.DEVELOPER,
        relevance,
        priority,
        SuggestionStatus.PENDING,
        "user-test",
        null);
  }
}
