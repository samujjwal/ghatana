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
@DisplayName("FeedbackLearningService Tests [GH-90000]")
class FeedbackLearningServiceTest extends EventloopTestBase {

  private FeedbackLearningService service;

  @BeforeEach
  void setUp() { // GH-90000
    service = new FeedbackLearningService(); // GH-90000
  }

  // ===== Feedback Processing Tests =====

  @Nested
  @DisplayName("Feedback Processing [GH-90000]")
  class FeedbackProcessing {

    @Test
    @DisplayName("Should increase relevance score for HELPFUL feedback [GH-90000]")
    void shouldIncreaseRelevanceForHelpful() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Great!", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.relevanceScore()).isGreaterThan(0.5f); // GH-90000
    }

    @Test
    @DisplayName("Should decrease relevance score for NOT_HELPFUL feedback [GH-90000]")
    void shouldDecreaseRelevanceForNotHelpful() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.NOT_HELPFUL, "Not useful", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.relevanceScore()).isLessThan(0.5f); // GH-90000
    }

    @Test
    @DisplayName("Should decrease relevance more for DUPLICATE feedback [GH-90000]")
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
    @DisplayName("Should clamp relevance score to [0, 1] [GH-90000]")
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
    @DisplayName("Should clamp relevance score at upper bound [GH-90000]")
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
  @DisplayName("Priority Score Adjustment [GH-90000]")
  class PriorityAdjustment {

    @Test
    @DisplayName("Should increase priority for positive feedback [GH-90000]")
    void shouldIncreasePriorityForPositive() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Useful", null, "user-1");

      AISuggestion updated = runPromise( // GH-90000
          () -> service.processFeedback(suggestion, feedback)); // GH-90000

      assertThat(updated.priorityScore()).isGreaterThan(0.5f); // GH-90000
    }

    @Test
    @DisplayName("Should decrease priority for negative feedback [GH-90000]")
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
  @DisplayName("Null Handling [GH-90000]")
  class NullHandling {

    @Test
    @DisplayName("Should reject null suggestion [GH-90000]")
    void shouldRejectNullSuggestion() { // GH-90000
      SuggestionFeedback feedback = new SuggestionFeedback( // GH-90000
          "sug-1", FeedbackType.HELPFUL, "Good", null, "user-1");

      assertThatThrownBy(() -> runPromise( // GH-90000
          () -> service.processFeedback(null, feedback))) // GH-90000
          .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject null feedback [GH-90000]")
    void shouldRejectNullFeedback() { // GH-90000
      AISuggestion suggestion = createSuggestion(0.5f, 0.5f); // GH-90000

      assertThatThrownBy(() -> runPromise( // GH-90000
          () -> service.processFeedback(suggestion, null))) // GH-90000
          .isInstanceOf(NullPointerException.class); // GH-90000
    }
  }

  // ===== Learning Metrics Tests =====

  @Nested
  @DisplayName("Learning Metrics [GH-90000]")
  class LearningMetrics {

    @Test
    @DisplayName("Should return learning metrics [GH-90000]")
    void shouldReturnMetrics() { // GH-90000
      Map<String, Object> metrics = runPromise(service::getLearningMetrics); // GH-90000

      assertThat(metrics).isNotNull(); // GH-90000
      assertThat(metrics).containsKeys("feedbackProcessed", "avgRelevanceAdjustment"); // GH-90000
    }
  }

  // ===== Persona Analysis Tests =====

  @Nested
  @DisplayName("Persona Analysis [GH-90000]")
  class PersonaAnalysis {

    @Test
    @DisplayName("Should return persona performance analysis [GH-90000]")
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
