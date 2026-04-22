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
@DisplayName("AISuggestion Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles ai suggestion test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class AISuggestionTest {

  @Test
  @DisplayName("Should create valid suggestion with all parameters [GH-90000]")
  void shouldCreateValidSuggestion() { // GH-90000
    AISuggestion suggestion =
        new AISuggestion( // GH-90000
            "req-123",
            "Add OAuth2 authentication",
            Persona.DEVELOPER,
            0.85f,
            0.75f,
            SuggestionStatus.PENDING,
            "user-456",
            null);

    assertThat(suggestion.requirementId()).isEqualTo("req-123 [GH-90000]");
    assertThat(suggestion.suggestionText()).isEqualTo("Add OAuth2 authentication [GH-90000]");
    assertThat(suggestion.persona()).isEqualTo(Persona.DEVELOPER); // GH-90000
    assertThat(suggestion.relevanceScore()).isEqualTo(0.85f); // GH-90000
    assertThat(suggestion.priorityScore()).isEqualTo(0.75f); // GH-90000
    assertThat(suggestion.status()).isEqualTo(SuggestionStatus.PENDING); // GH-90000
  }

  @Test
  @DisplayName("Should calculate rank score as average of relevance and priority [GH-90000]")
  void shouldCalculateRankScore() { // GH-90000
    AISuggestion suggestion =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.PRODUCT_MANAGER,
            0.8f,
            0.6f,
            SuggestionStatus.PENDING,
            null,
            null);

    assertThat(suggestion.rankScore()).isCloseTo(0.7f, within(0.001f)); // GH-90000
  }

  @Test
  @DisplayName("Should throw NullPointerException when requirement ID is null [GH-90000]")
  void shouldThrowWhenRequirementIdNull() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> // GH-90000
                new AISuggestion( // GH-90000
                    null,
                    "Text",
                    Persona.QA,
                    0.8f,
                    0.7f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(NullPointerException.class) // GH-90000
        .hasMessage("requirementId cannot be null [GH-90000]");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when suggestion text is empty [GH-90000]")
  void shouldThrowWhenTextEmpty() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> // GH-90000
                new AISuggestion( // GH-90000
                    "req-123",
                    "   ",
                    Persona.UX_DESIGNER,
                    0.8f,
                    0.7f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("cannot be empty [GH-90000]");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when relevance score > 1.0 [GH-90000]")
  void shouldThrowWhenRelevanceTooHigh() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> // GH-90000
                new AISuggestion( // GH-90000
                    "req-123",
                    "Text",
                    Persona.ARCHITECT,
                    1.1f,
                    0.7f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("relevanceScore [GH-90000]");
  }

  @Test
  @DisplayName("Should throw IllegalArgumentException when priority score < 0 [GH-90000]")
  void shouldThrowWhenPriorityNegative() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> // GH-90000
                new AISuggestion( // GH-90000
                    "req-123",
                    "Text",
                    Persona.DEVELOPER,
                    0.8f,
                    -0.1f,
                    SuggestionStatus.PENDING,
                    "user",
                    null))
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("priorityScore [GH-90000]");
  }

  @Test
  @DisplayName("Should create modified copy with new status [GH-90000]")
  void shouldCreateModifiedCopyWithStatus() { // GH-90000
    AISuggestion original =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            "user",
            null);

    AISuggestion updated = original.withStatus(SuggestionStatus.APPROVED); // GH-90000

    assertThat(updated.status()).isEqualTo(SuggestionStatus.APPROVED); // GH-90000
    assertThat(updated.relevanceScore()).isEqualTo(original.relevanceScore()); // GH-90000
    assertThat(original.status()).isEqualTo(SuggestionStatus.PENDING); // Original unchanged // GH-90000
  }

  @Test
  @DisplayName("Should create modified copy with updated scores [GH-90000]")
  void shouldCreateModifiedCopyWithScores() { // GH-90000
    AISuggestion original =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.PRODUCT_MANAGER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            "user",
            null);

    AISuggestion updated = original.withScores(0.9f, 0.6f); // GH-90000

    assertThat(updated.relevanceScore()).isCloseTo(0.9f, within(0.001f)); // GH-90000
    assertThat(updated.priorityScore()).isCloseTo(0.6f, within(0.001f)); // GH-90000
    assertThat(original.relevanceScore()).isCloseTo(0.8f, within(0.001f)); // Original unchanged // GH-90000
  }

  @Test
  @DisplayName("Should have feedback link if set [GH-90000]")
  void shouldTrackFeedback() { // GH-90000
    AISuggestion withoutFeedback =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.QA,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            "user",
            null);

    assertThat(withoutFeedback.hasFeedback()).isFalse(); // GH-90000

    AISuggestion withFeedback = withoutFeedback.withFeedback(999L); // GH-90000

    assertThat(withFeedback.hasFeedback()).isTrue(); // GH-90000
    assertThat(withFeedback.feedbackId()).isEqualTo(999L); // GH-90000
  }

  @Test
  @DisplayName("Should be equal when all fields match [GH-90000]")
  void shouldBeEqual() { // GH-90000
    AISuggestion s1 =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.APPROVED,
            "user-456",
            999L);

    AISuggestion s2 =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.APPROVED,
            "user-456",
            999L);

    assertThat(s1).isEqualTo(s2); // GH-90000
    assertThat(s1.hashCode()).isEqualTo(s2.hashCode()); // GH-90000
  }

  @Test
  @DisplayName("Should have informative toString [GH-90000]")
  void shouldHaveInformativeToString() { // GH-90000
    AISuggestion suggestion =
        new AISuggestion( // GH-90000
            "req-123",
            "Text",
            Persona.ARCHITECT,
            0.84f,
            0.76f,
            SuggestionStatus.PENDING,
            "user",
            null);

    String str = suggestion.toString(); // GH-90000

    assertThat(str).contains("req-123 [GH-90000]").contains("ARCH [GH-90000]").contains("PENDING [GH-90000]");
  }
}
