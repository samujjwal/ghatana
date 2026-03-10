package com.ghatana.yappc.ai.requirements.ai.suggestions;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for AISuggestion value object.
 *
 * <p>Tests validate:
 * - Immutability and correctness
 * - Score validation and clamping
 * - Status transitions
 * - Equality and hash code
 * - Builder methods for creating modified copies
 */
@DisplayName("AISuggestion Tests")
/**
 * @doc.type class
 * @doc.purpose Handles ai suggestion test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class AISuggestionTest {

  @Test
  @DisplayName("Should create valid suggestion with all parameters")
  void shouldCreateValidSuggestion() {
    AISuggestion suggestion =
        new AISuggestion(
            "req-123",
            "Add OAuth2 authentication",
            Persona.DEVELOPER,
            0.85f,
            0.75f,
            SuggestionStatus.PENDING,
            "user-456",
            null);

    assertThat(suggestion.requirementId()).isEqualTo("req-123");
    assertThat(suggestion.suggestionText()).isEqualTo("Add OAuth2 authentication");
    assertThat(suggestion.persona()).isEqualTo(Persona.DEVELOPER);
    assertThat(suggestion.relevanceScore()).isEqualTo(0.85f);
    assertThat(suggestion.priorityScore()).isEqualTo(0.75f);
    assertThat(suggestion.status()).isEqualTo(SuggestionStatus.PENDING);
  }

  @Test
  @DisplayName("Should calculate rank score as average of relevance and priority")
  void shouldCalculateRankScore() {
    AISuggestion suggestion =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.PRODUCT_MANAGER,
            0.8f,
            0.6f,
            SuggestionStatus.PENDING,
            null,
            null);

    assertThat(suggestion.rankScore()).isCloseTo(0.7f, within(0.001f));
  }

  @Test
  @DisplayName("Should throw NullPointerException when requirement ID is null")
  void shouldThrowWhenRequirementIdNull() {
    assertThatThrownBy(
            () ->
                new AISuggestion(
                    null,
                    "Text",
                    Persona.QA,
                    0.8f,
                    0.7f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("requirementId cannot be null");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when suggestion text is empty")
  void shouldThrowWhenTextEmpty() {
    assertThatThrownBy(
            () ->
                new AISuggestion(
                    "req-123",
                    "   ",
                    Persona.UX_DESIGNER,
                    0.8f,
                    0.7f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be empty");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when relevance score > 1.0")
  void shouldThrowWhenRelevanceTooHigh() {
    assertThatThrownBy(
            () ->
                new AISuggestion(
                    "req-123",
                    "Text",
                    Persona.ARCHITECT,
                    1.1f,
                    0.7f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("relevanceScore");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when priority score < 0")
  void shouldThrowWhenPriorityNegative() {
    assertThatThrownBy(
            () ->
                new AISuggestion(
                    "req-123",
                    "Text",
                    Persona.DEVELOPER,
                    0.8f,
                    -0.1f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("priorityScore");
  }

  @Test
  @DisplayName("Should create modified copy with new status")
  void shouldCreateModifiedCopyWithStatus() {
    AISuggestion original =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            "user",
            null);

    AISuggestion updated = original.withStatus(SuggestionStatus.APPROVED);

    assertThat(updated.status()).isEqualTo(SuggestionStatus.APPROVED);
    assertThat(updated.relevanceScore()).isEqualTo(original.relevanceScore());
    assertThat(original.status()).isEqualTo(SuggestionStatus.PENDING); // Original unchanged
  }

  @Test
  @DisplayName("Should create modified copy with updated scores")
  void shouldCreateModifiedCopyWithScores() {
    AISuggestion original =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.PRODUCT_MANAGER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            "user",
            null);

    AISuggestion updated = original.withScores(0.9f, 0.6f);

    assertThat(updated.relevanceScore()).isCloseTo(0.9f, within(0.001f));
    assertThat(updated.priorityScore()).isCloseTo(0.6f, within(0.001f));
    assertThat(original.relevanceScore()).isCloseTo(0.8f, within(0.001f)); // Original unchanged
  }

  @Test
  @DisplayName("Should have feedback link if set")
  void shouldTrackFeedback() {
    AISuggestion withoutFeedback =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.QA,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            "user",
            null);

    assertThat(withoutFeedback.hasFeedback()).isFalse();

    AISuggestion withFeedback = withoutFeedback.withFeedback(999L);

    assertThat(withFeedback.hasFeedback()).isTrue();
    assertThat(withFeedback.feedbackId()).isEqualTo(999L);
  }

  @Test
  @DisplayName("Should be equal when all fields match")
  void shouldBeEqual() {
    AISuggestion s1 =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.APPROVED,
            "user-456",
            999L);

    AISuggestion s2 =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.APPROVED,
            "user-456",
            999L);

    assertThat(s1).isEqualTo(s2);
    assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
  }

  @Test
  @DisplayName("Should have informative toString")
  void shouldHaveInformativeToString() {
    AISuggestion suggestion =
        new AISuggestion(
            "req-123",
            "Text",
            Persona.ARCHITECT,
            0.84f,
            0.76f,
            SuggestionStatus.PENDING,
            "user",
            null);

    String str = suggestion.toString();

    assertThat(str).contains("req-123").contains("ARCH").contains("PENDING");
  }
}