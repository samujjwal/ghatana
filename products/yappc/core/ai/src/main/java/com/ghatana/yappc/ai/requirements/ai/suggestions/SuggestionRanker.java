package com.ghatana.yappc.ai.requirements.ai.suggestions;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility for ranking and sorting AI suggestions by relevance and priority.
 *
 * <p><b>Purpose:</b> Provides multiple sorting strategies for suggestions
 * to support different ranking needs (relevance-first, priority-first, combined).
 *
 * <p><b>Thread Safety:</b> Completely stateless. All methods are static
 * and thread-safe. Operates on the input list in-place and returns it.
 *
 * <p><b>Ranking Strategies:</b>
 * <ul>
 *   <li><b>BY_RELEVANCE:</b> Sort by relevance score descending</li>
 *   <li><b>BY_PRIORITY:</b> Sort by priority score descending</li>
 *   <li><b>BY_RANK_SCORE:</b> Sort by combined rank (average of both)</li>
 *   <li><b>BY_PERSONA:</b> Group by persona, then sort within groups</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   List<AISuggestion> suggestions = generateSuggestions(feature);
 *
 *   // Sort by relevance
 *   SuggestionRanker.rankByRelevance(suggestions);
 *
 *   // Get top 5
 *   List<AISuggestion> top5 = suggestions.subList(0, Math.min(5, suggestions.size()));
 *
 *   // Filter and rank
 *   suggestions.stream()
 *       .filter(s -> s.relevanceScore() > 0.8f)
 *       .sorted(SuggestionRanker.comparatorByRankScore())
 *       .limit(10)
 *       .forEach(this::display);
 * }</pre>
 *
 * @see AISuggestion
 * @doc.type class
 * @doc.purpose Utility for ranking and sorting suggestions
 * @doc.layer product
 * @doc.pattern Utility (static helper methods)
 * @since 1.0.0
 */
public final class SuggestionRanker {
  private SuggestionRanker() {
    // Utility class
  }

  /**
   * Sort suggestions by relevance score (descending, highest first).
   *
   * <p>Modifies the input list in-place and returns it for chaining.
   *
   * @param suggestions list to sort (non-null)
   * @return the input list, sorted by relevance
   */
  public static List<AISuggestion> rankByRelevance(List<AISuggestion> suggestions) {
    suggestions.sort(
        (a, b) -> Float.compare(b.relevanceScore(), a.relevanceScore()));
    return suggestions;
  }

  /**
   * Sort suggestions by priority score (descending, highest first).
   *
   * <p>Modifies the input list in-place and returns it for chaining.
   *
   * @param suggestions list to sort (non-null)
   * @return the input list, sorted by priority
   */
  public static List<AISuggestion> rankByPriority(List<AISuggestion> suggestions) {
    suggestions.sort(
        (a, b) -> Float.compare(b.priorityScore(), a.priorityScore()));
    return suggestions;
  }

  /**
   * Sort suggestions by combined rank score (average of relevance and priority).
   *
   * <p>This is the most common ranking strategy, balancing both dimensions.
   * Modifies the input list in-place.
   *
   * @param suggestions list to sort (non-null)
   * @return the input list, sorted by rank score
   */
  public static List<AISuggestion> rankByRankScore(List<AISuggestion> suggestions) {
    suggestions.sort(
        (a, b) -> Float.compare(b.rankScore(), a.rankScore()));
    return suggestions;
  }

  /**
   * Sort suggestions by persona, then by rank score within each persona.
   *
   * <p>Useful for grouping related suggestions together.
   * Modifies the input list in-place.
   *
   * @param suggestions list to sort (non-null)
   * @return the input list, sorted by persona then rank score
   */
  public static List<AISuggestion> rankByPersonaThenScore(List<AISuggestion> suggestions) {
    suggestions.sort(
        Comparator.comparing(AISuggestion::persona)
            .thenComparing((a, b) -> Float.compare(b.rankScore(), a.rankScore())));
    return suggestions;
  }

  /**
   * Sort suggestions by status, then by rank score.
   *
   * <p>Puts pending suggestions first, then approved, then rejected.
   * Useful for status-aware UI display.
   *
   * @param suggestions list to sort (non-null)
   * @return the input list, sorted by status then rank score
   */
  public static List<AISuggestion> rankByStatusThenScore(List<AISuggestion> suggestions) {
    suggestions.sort(
        Comparator.comparing(AISuggestion::status)
            .thenComparing((a, b) -> Float.compare(b.rankScore(), a.rankScore())));
    return suggestions;
  }

  /**
   * Get a comparator that sorts by relevance (for Stream operations).
   *
   * @return comparator sorted by relevance descending
   */
  public static Comparator<AISuggestion> comparatorByRelevance() {
    return (a, b) -> Float.compare(b.relevanceScore(), a.relevanceScore());
  }

  /**
   * Get a comparator that sorts by priority (for Stream operations).
   *
   * @return comparator sorted by priority descending
   */
  public static Comparator<AISuggestion> comparatorByPriority() {
    return (a, b) -> Float.compare(b.priorityScore(), a.priorityScore());
  }

  /**
   * Get a comparator that sorts by rank score (for Stream operations).
   *
   * @return comparator sorted by rank score descending
   */
  public static Comparator<AISuggestion> comparatorByRankScore() {
    return (a, b) -> Float.compare(b.rankScore(), a.rankScore());
  }

  /**
   * Get a comparator that sorts by persona (for Stream operations).
   *
   * @return comparator sorted by persona alphabetically
   */
  public static Comparator<AISuggestion> comparatorByPersona() {
    return Comparator.comparing(AISuggestion::persona);
  }

  /**
   * Get top N suggestions from a list, sorted by rank score.
   *
   * <p>Convenience method that ranks and returns top N.
   *
   * @param suggestions the full list of suggestions (non-null)
   * @param topN number of suggestions to return
   * @return list of top N suggestions, sorted by rank score
   */
  public static List<AISuggestion> topN(List<AISuggestion> suggestions, int topN) {
    rankByRankScore(suggestions);
    return suggestions.subList(0, Math.min(topN, suggestions.size()));
  }

  /**
   * Filter suggestions by minimum rank score threshold.
   *
   * <p>Returns a new list containing only suggestions above the threshold.
   * Original list is unchanged.
   *
   * @param suggestions the suggestions to filter (non-null)
   * @param minScore minimum rank score threshold [0, 1]
   * @return new list with only suggestions >= minScore
   */
  public static List<AISuggestion> filterByRankScore(
      List<AISuggestion> suggestions, float minScore) {
    return suggestions.stream()
        .filter(s -> s.rankScore() >= minScore)
        .sorted(comparatorByRankScore())
        .toList();
  }

  /**
   * Filter suggestions by status.
   *
   * @param suggestions the suggestions to filter (non-null)
   * @param status the status to filter for (non-null)
   * @return new list with only suggestions in the given status
   */
  public static List<AISuggestion> filterByStatus(
      List<AISuggestion> suggestions, SuggestionStatus status) {
    return suggestions.stream()
        .filter(s -> s.status() == status)
        .sorted(comparatorByRankScore())
        .toList();
  }

  /**
   * Calculate average rank score across all suggestions.
   *
   * @param suggestions the suggestions to analyze (non-null)
   * @return average rank score (0 if empty list)
   */
  public static float averageRankScore(List<AISuggestion> suggestions) {
    if (suggestions.isEmpty()) {
      return 0;
    }
    return (float) suggestions.stream()
        .mapToDouble(AISuggestion::rankScore)
        .average()
        .orElse(0);
  }

  /**
   * Calculate distribution of suggestions by persona.
   *
   * @param suggestions the suggestions to analyze (non-null)
   * @return map of persona to count of suggestions from that persona
   */
  public static java.util.Map<com.ghatana.yappc.ai.requirements.ai.persona.Persona, Integer> distributionByPersona(
      List<AISuggestion> suggestions) {
    java.util.Map<com.ghatana.yappc.ai.requirements.ai.persona.Persona, Integer> distribution =
        new java.util.HashMap<>();
    for (AISuggestion s : suggestions) {
      distribution.merge(s.persona(), 1, Integer::sum);
    }
    return distribution;
  }
}