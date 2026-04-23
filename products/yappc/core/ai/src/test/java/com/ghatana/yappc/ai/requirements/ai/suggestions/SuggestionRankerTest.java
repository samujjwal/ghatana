package com.ghatana.yappc.ai.requirements.ai.suggestions;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SuggestionRanker utility.
 *
 * <p>Tests validate:
 * - Ranking by various criteria (relevance, priority, rank score) // GH-90000
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

  private List<AISuggestion> createTestSuggestions() { // GH-90000
    List<AISuggestion> suggestions = new ArrayList<>(); // GH-90000
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Suggestion 1",
            Persona.DEVELOPER,
            0.9f,
            0.7f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Suggestion 2",
            Persona.PRODUCT_MANAGER,
            0.7f,
            0.8f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
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
  void shouldRankByRelevance() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    SuggestionRanker.rankByRelevance(suggestions); // GH-90000

    assertThat(suggestions.get(0).relevanceScore()).isEqualTo(0.9f); // GH-90000
    assertThat(suggestions.get(1).relevanceScore()).isEqualTo(0.8f); // GH-90000
    assertThat(suggestions.get(2).relevanceScore()).isEqualTo(0.7f); // GH-90000
  }

  @Test
  @DisplayName("Should rank by priority in descending order")
  void shouldRankByPriority() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    SuggestionRanker.rankByPriority(suggestions); // GH-90000

    assertThat(suggestions.get(0).priorityScore()).isEqualTo(0.8f); // GH-90000
    assertThat(suggestions.get(1).priorityScore()).isEqualTo(0.7f); // GH-90000
    assertThat(suggestions.get(2).priorityScore()).isEqualTo(0.6f); // GH-90000
  }

  @Test
  @DisplayName("Should rank by combined rank score")
  void shouldRankByRankScore() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    SuggestionRanker.rankByRankScore(suggestions); // GH-90000

    float first = suggestions.get(0).rankScore(); // GH-90000
    float second = suggestions.get(1).rankScore(); // GH-90000
    float third = suggestions.get(2).rankScore(); // GH-90000

    assertThat(first).isGreaterThanOrEqualTo(second); // GH-90000
    assertThat(second).isGreaterThanOrEqualTo(third); // GH-90000
  }

  @Test
  @DisplayName("Should get top N suggestions")
  void shouldGetTopN() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    List<AISuggestion> top2 = SuggestionRanker.topN(suggestions, 2); // GH-90000

    assertThat(top2).hasSize(2); // GH-90000
  }

  @Test
  @DisplayName("Should handle topN larger than list size")
  void shouldHandleTopNLargerThanSize() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    List<AISuggestion> top10 = SuggestionRanker.topN(suggestions, 10); // GH-90000

    assertThat(top10).hasSize(3); // GH-90000
  }

  @Test
  @DisplayName("Should filter by minimum rank score")
  void shouldFilterByRankScore() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    List<AISuggestion> filtered = SuggestionRanker.filterByRankScore(suggestions, 0.75f); // GH-90000

    assertThat(filtered).allMatch(s -> s.rankScore() >= 0.75f); // GH-90000
  }

  @Test
  @DisplayName("Should filter by status")
  void shouldFilterByStatus() { // GH-90000
    List<AISuggestion> suggestions = new ArrayList<>(); // GH-90000
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Text1",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Text2",
            Persona.PRODUCT_MANAGER,
            0.7f,
            0.8f,
            SuggestionStatus.APPROVED,
            null,
            null));
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Text3",
            Persona.QA,
            0.8f,
            0.6f,
            SuggestionStatus.PENDING,
            null,
            null));

    List<AISuggestion> pending = SuggestionRanker.filterByStatus(suggestions, SuggestionStatus.PENDING); // GH-90000

    assertThat(pending).hasSize(2); // GH-90000
    assertThat(pending).allMatch(s -> s.status() == SuggestionStatus.PENDING); // GH-90000
  }

  @Test
  @DisplayName("Should calculate average rank score")
  void shouldCalculateAverageRankScore() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    float avg = SuggestionRanker.averageRankScore(suggestions); // GH-90000

    float expected = (0.8f + 0.75f + 0.7f) / 3; // GH-90000
    assertThat(avg).isCloseTo(expected, within(0.01f)); // GH-90000
  }

  @Test
  @DisplayName("Should return 0 for average of empty list")
  void shouldReturnZeroForEmpty() { // GH-90000
    float avg = SuggestionRanker.averageRankScore(new ArrayList<>()); // GH-90000

    assertThat(avg).isZero(); // GH-90000
  }

  @Test
  @DisplayName("Should calculate persona distribution")
  void shouldCalculatePersonaDistribution() { // GH-90000
    List<AISuggestion> suggestions = createTestSuggestions(); // GH-90000

    java.util.Map<Persona, Integer> distribution =
        SuggestionRanker.distributionByPersona(suggestions); // GH-90000

    assertThat(distribution).containsEntry(Persona.DEVELOPER, 1); // GH-90000
    assertThat(distribution).containsEntry(Persona.PRODUCT_MANAGER, 1); // GH-90000
    assertThat(distribution).containsEntry(Persona.QA, 1); // GH-90000
  }

  @Test
  @DisplayName("Should handle multiple suggestions from same persona")
  void shouldHandleMultipleSamePersona() { // GH-90000
    List<AISuggestion> suggestions = new ArrayList<>(); // GH-90000
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Text1",
            Persona.DEVELOPER,
            0.8f,
            0.7f,
            SuggestionStatus.PENDING,
            null,
            null));
    suggestions.add( // GH-90000
        new AISuggestion( // GH-90000
            "req-123",
            "Text2",
            Persona.DEVELOPER,
            0.7f,
            0.8f,
            SuggestionStatus.PENDING,
            null,
            null));

    java.util.Map<Persona, Integer> distribution =
        SuggestionRanker.distributionByPersona(suggestions); // GH-90000

    assertThat(distribution).containsEntry(Persona.DEVELOPER, 2); // GH-90000
  }
}
