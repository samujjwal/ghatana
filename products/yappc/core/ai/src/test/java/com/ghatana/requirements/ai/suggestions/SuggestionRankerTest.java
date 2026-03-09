package com.ghatana.requirements.ai.suggestions;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.requirements.ai.persona.Persona;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SuggestionRanker utility.
 *
 * <p>Tests validate:
 * - Ranking by various criteria (relevance, priority, rank score)
 * - Filtering and top-N operations
 * - Statistics calculation
 * - Persona distribution analysis
 */
@DisplayName("SuggestionRanker Tests")
/**
 * @doc.type class
 * @doc.purpose Handles suggestion ranker test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class SuggestionRankerTest {

  private List<AISuggestion> createTestSuggestions() {
    List<AISuggestion> suggestions = new ArrayList<>();
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Suggestion 1",
            Persona.DEVELOPER,
            0.9f,
            0.7f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Suggestion 2",
            Persona.PRODUCT_MANAGER,
            0.7f,
            0.8f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Suggestion 3",
            Persona.QA,
            0.8f,
            0.6f,
            SuggestionStatus.PENDING,
            null,
            null));
    return suggestions;
  }

  @Test
  @DisplayName("Should rank by relevance in descending order")
  void shouldRankByRelevance() {
    List<AISuggestion> suggestions = createTestSuggestions();

    SuggestionRanker.rankByRelevance(suggestions);

    assertThat(suggestions.get(0).relevanceScore()).isEqualTo(0.9f);
    assertThat(suggestions.get(1).relevanceScore()).isEqualTo(0.8f);
    assertThat(suggestions.get(2).relevanceScore()).isEqualTo(0.7f);
  }

  @Test
  @DisplayName("Should rank by priority in descending order")
  void shouldRankByPriority() {
    List<AISuggestion> suggestions = createTestSuggestions();

    SuggestionRanker.rankByPriority(suggestions);

    assertThat(suggestions.get(0).priorityScore()).isEqualTo(0.8f);
    assertThat(suggestions.get(1).priorityScore()).isEqualTo(0.7f);
    assertThat(suggestions.get(2).priorityScore()).isEqualTo(0.6f);
  }

  @Test
  @DisplayName("Should rank by combined rank score")
  void shouldRankByRankScore() {
    List<AISuggestion> suggestions = createTestSuggestions();

    SuggestionRanker.rankByRankScore(suggestions);

    float first = suggestions.get(0).rankScore();
    float second = suggestions.get(1).rankScore();
    float third = suggestions.get(2).rankScore();

    assertThat(first).isGreaterThanOrEqualTo(second);
    assertThat(second).isGreaterThanOrEqualTo(third);
  }

  @Test
  @DisplayName("Should get top N suggestions")
  void shouldGetTopN() {
    List<AISuggestion> suggestions = createTestSuggestions();

    List<AISuggestion> top2 = SuggestionRanker.topN(suggestions, 2);

    assertThat(top2).hasSize(2);
  }

  @Test
  @DisplayName("Should handle topN larger than list size")
  void shouldHandleTopNLargerThanSize() {
    List<AISuggestion> suggestions = createTestSuggestions();

    List<AISuggestion> top10 = SuggestionRanker.topN(suggestions, 10);

    assertThat(top10).hasSize(3);
  }

  @Test
  @DisplayName("Should filter by minimum rank score")
  void shouldFilterByRankScore() {
    List<AISuggestion> suggestions = createTestSuggestions();

    List<AISuggestion> filtered = SuggestionRanker.filterByRankScore(suggestions, 0.75f);

    assertThat(filtered).allMatch(s -> s.rankScore() >= 0.75f);
  }

  @Test
  @DisplayName("Should filter by status")
  void shouldFilterByStatus() {
    List<AISuggestion> suggestions = new ArrayList<>();
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Text1",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Text2",
            Persona.PRODUCT_MANAGER,
            0.7f,
            0.8f,
            SuggestionStatus.APPROVED,
            null,
            null));
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Text3",
            Persona.QA,
            0.8f,
            0.6f,
            SuggestionStatus.PENDING,
            null,
            null));

    List<AISuggestion> pending = SuggestionRanker.filterByStatus(suggestions, SuggestionStatus.PENDING);

    assertThat(pending).hasSize(2);
    assertThat(pending).allMatch(s -> s.status() == SuggestionStatus.PENDING);
  }

  @Test
  @DisplayName("Should calculate average rank score")
  void shouldCalculateAverageRankScore() {
    List<AISuggestion> suggestions = createTestSuggestions();

    float avg = SuggestionRanker.averageRankScore(suggestions);

    float expected = (0.8f + 0.75f + 0.7f) / 3;
    assertThat(avg).isCloseTo(expected, within(0.01f));
  }

  @Test
  @DisplayName("Should return 0 for average of empty list")
  void shouldReturnZeroForEmpty() {
    float avg = SuggestionRanker.averageRankScore(new ArrayList<>());

    assertThat(avg).isZero();
  }

  @Test
  @DisplayName("Should calculate persona distribution")
  void shouldCalculatePersonaDistribution() {
    List<AISuggestion> suggestions = createTestSuggestions();

    java.util.Map<Persona, Integer> distribution =
        SuggestionRanker.distributionByPersona(suggestions);

    assertThat(distribution).containsEntry(Persona.DEVELOPER, 1);
    assertThat(distribution).containsEntry(Persona.PRODUCT_MANAGER, 1);
    assertThat(distribution).containsEntry(Persona.QA, 1);
  }

  @Test
  @DisplayName("Should handle multiple suggestions from same persona")
  void shouldHandleMultipleSamePersona() {
    List<AISuggestion> suggestions = new ArrayList<>();
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Text1",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add(
        new AISuggestion(
            "req-123",
            "Text2",
            Persona.DEVELOPER,
            0.7f,
            0.8f,
            SuggestionStatus.PENDING,
            null,
            null));

    java.util.Map<Persona, Integer> distribution =
        SuggestionRanker.distributionByPersona(suggestions);

    assertThat(distribution).containsEntry(Persona.DEVELOPER, 2);
  }
}