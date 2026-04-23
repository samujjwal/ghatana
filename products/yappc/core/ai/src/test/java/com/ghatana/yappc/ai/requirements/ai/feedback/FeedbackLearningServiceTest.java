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
  void setUp() { // GH-90000
    service = new FeedbackLearningService(); // GH-90000
  }

  // ===== Feedback Processing Tests =====

  @Nested
  @DisplayName("Feedback Processing")
  class FeedbackProcessing {

    @Test
    @DisplayName("Should increase relevance score for HELPFUL feedback")
    void shouldIncreaseRelevanceForHelpful() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Great!", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.relevanceScore()).isGreaterThan(0.5f); // GH-90000
    }

    @Test
    @DisplayName("Should decrease relevance score for NOT_HELPFUL feedback")
    void shouldDecreaseRelevanceForNotHelpful() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.NOT_HELPFUL, "Not useful", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.relevanceScore()).isLessThan(0.5f); // GH-90000
    }

    @Test
    @DisplayName("Should decrease relevance more for DUPLICATE feedback")
    void shouldDecreaseRelevanceMoreForDuplicate() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback duplicate = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.DUPLICATE, "Already exists", null, "user-1");
      SuggestionFeedback notHelpful = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.NOT_HELPFUL, "Nope", null, "user-1");

      AISuggestion dupUpdated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, duplicate)); // GH-90000
      AISuggestion nhUpdated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, notHelpful)); // GH-90000

      // DUPLICATE should penalize more than NOT_HELPFUL
      assertThat(dupUpdated.relevanceScore()).isLessThan(nhUpdated.relevanceScore()); // GH-90000
    }

    @Test
    @DisplayName("Should clamp relevance score to [0, 1]")
    void shouldClampRelevanceScore() { // GH-90000
      // Start at 0.05 — INVALID feedback should reduce but not go below 0
      AISuggestion lowScore = createSuggestion(0.05f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.INVALID, "Wrong", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(lowScore, feedback)); // GH-90000

      assertThat(updated.relevanceScore()).isGreaterThanOrEqualTo(0.0f); // GH-90000
    }

    @Test
    @DisplayName("Should clamp relevance score at upper bound")
    void shouldClampRelevanceAtUpperBound() { // GH-90000
      AISuggestion highScore = createSuggestion(0.98f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Perfect!", 5, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(highScore, feedback)); // GH-90000

      assertThat(updated.relevanceScore()).isLessThanOrEqualTo(1.0f); // GH-90000
    }
  }

  // ===== Priority Score Tests =====

  @Nested
  @DisplayName("Priority Score Adjustment")
  class PriorityAdjustment {

    @Test
    @DisplayName("Should increase priority for positive feedback")
    void shouldIncreasePriorityForPositive() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Useful", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.priorityScore()).isGreaterThan(0.5f); // GH-90000
    }

    @Test
    @DisplayName("Should decrease priority for negative feedback")
    void shouldDecreasePriorityForNegative() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.NOT_HELPFUL, "Bad", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.priorityScore()).isLessThan(0.5f); // GH-90000
    }
  }

  // ===== Null Handling Tests =====

  @Nested
  @DisplayName("Null Handling")
  class NullHandling {

    @Test
    @DisplayName("Should reject null suggestion")
    void shouldRejectNullSuggestion() { // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Good", null, "user-1");

      assertThatThrownBy(() -> runPromise( // GH-90000
          () -> service.processFeedback(null, feedback))) // GH-90000
          .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject null feedback")
    void shouldRejectNullFeedback() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000

      assertThatThrownBy(() -> runPromise( // GH-90000
          () -> service.processFeedback(suggestion, null))) // GH-90000
          .isInstanceOf(NullPointerException.class); // GH-90000
    }
  }

  // ===== Learning Metrics Tests =====

  @Nested
  @DisplayName("Learning Metrics")
  class LearningMetrics {

    @Test
    @DisplayName("Should return learning metrics")
    void shouldReturnMetrics() { // GH-90000
      Map<String, Object> metrics = runPromise(service::getLearningMetrics); // GH-90000

      assertThat(metrics).isNotNull(); // GH-90000
      assertThat(metrics).containsKeys("feedbackProcessed", "avgRelevanceAdjustment"); // GH-90000
    }
  }

  // ===== Persona Analysis Tests =====

  @Nested
  @DisplayName("Persona Analysis")
  class PersonaAnalysis {

    @Test
    @DisplayName("Should return persona performance analysis")
    void shouldReturnPersonaAnalysis() { // GH-90000
      var result = runPromise( // GH-90000
          () -> service.analyzePersonaPerformance(List.of())); // GH-90000

      assertThat(result).isNotNull(); // GH-90000
    }
  }

  // ===== Test Helpers =====

  private AISuggestion createSuggestion(float relevance, float priority) { // GH-90000
    return new AISuggestion( // GH-90000
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
