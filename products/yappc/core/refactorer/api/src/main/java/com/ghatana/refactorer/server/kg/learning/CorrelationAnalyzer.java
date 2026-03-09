package com.ghatana.refactorer.server.kg.learning;

import java.util.*;

/**
 * Analyzes temporal correlations between events to discover related patterns.

 *

 * <p>CorrelationAnalyzer identifies pairs of events that frequently occur together within a time

 * window, indicating potential causal or temporal relationships. This analysis feeds into pattern

 * suggestion and anomaly detection pipelines.

 *

 * <p>Correlation Metrics:

 * - Co-occurrence: How often events appear together

 * - Temporal distance: Time gap between correlated events

 * - Confidence: Probability of second event given first

 * - Support: Frequency of the pair in the event stream

 *

 * <p>Example:

 * ```java

 * CorrelationAnalyzer analyzer = new CorrelationAnalyzer(1000, Duration.ofMinutes(5));

 * analyzer.recordEvent("login");

 * analyzer.recordEvent("data-access", 2000); // 2 seconds after login

 * List<Correlation> correlations = analyzer.getCorrelations();

 * // Returns: Correlation(login -> data-access, confidence=0.95, support=0.87)

 * ```

 *

 * <p>Binding Decision #11: Learning operators provide pattern discovery without direct KG library

 * coupling, enabling pluggable mining algorithms and customizable correlation metrics.

 *

 * @doc.type class

 * @doc.purpose Run analytics algorithms over event sequences to surface correlations.

 * @doc.layer product

 * @doc.pattern Domain Service

 */

public final class CorrelationAnalyzer {
  private final int windowSizeMillis;
  private final LinkedList<Event> eventWindow;
  private final Map<String, Integer> eventCounts;
  private final Map<String, PairStats> pairStats;
  private long totalPairs = 0;

  /**
   * Immutable event record for correlation analysis.
   */
  private static class Event {
    final String type;
    final long timestamp;

    Event(String type, long timestamp) {
      this.type = type;
      this.timestamp = timestamp;
    }
  }

  /**
   * Statistics for event pairs.
   */
  private static class PairStats {
    String first;
    String second;
    int coOccurrences = 0;
    long totalDistance = 0;
    int occurrencesWithinWindow = 0;

    void recordOccurrence(long distance) {
      coOccurrences++;
      totalDistance += distance;
      if (distance <= 0) {
        occurrencesWithinWindow++;
      }
    }

    double getAverageDistance() {
      return coOccurrences > 0 ? (double) totalDistance / coOccurrences : 0;
    }

    double getConfidence(int firstCount) {
      return firstCount > 0 ? (double) coOccurrences / firstCount : 0;
    }
  }

  /**
   * Represents a discovered correlation between two events.
   */
  public record Correlation(
      String firstEvent,
      String secondEvent,
      double confidence,
      double support,
      long coOccurrences,
      double averageTemporalDistance) {

    /**
     * Human-readable format: "A -> B (conf: 0.95, sup: 0.87, dist: 2.3s)"
     */
    @Override
    public String toString() {
      return String.format(
          "%s -> %s (conf: %.2f, sup: %.2f, dist: %.1fs)",
          firstEvent,
          secondEvent,
          confidence,
          support,
          averageTemporalDistance / 1000.0);
    }
  }

  /**
   * Creates a correlation analyzer with specified window size.
   *
   * @param windowSizeMillis Time window for event correlation (e.g., 5 minutes)
   */
  public CorrelationAnalyzer(int windowSizeMillis) {
    this.windowSizeMillis = windowSizeMillis;
    this.eventWindow = new LinkedList<>();
    this.eventCounts = new HashMap<>();
    this.pairStats = new HashMap<>();
  }

  /**
   * Records an event occurrence at the current time.
   *
   * @param eventType Type of event
   */
  public void recordEvent(String eventType) {
    recordEvent(eventType, System.currentTimeMillis());
  }

  /**
   * Records an event occurrence at a specific timestamp.
   *
   * @param eventType Type of event
   * @param timestamp Timestamp in milliseconds
   */
  public void recordEvent(String eventType, long timestamp) {
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("Event type cannot be null or empty");
    }

    // Remove old events outside the window
    long cutoffTime = timestamp - windowSizeMillis;
    while (!eventWindow.isEmpty() && eventWindow.getFirst().timestamp <= cutoffTime) {
      eventWindow.removeFirst();
    }

    // Analyze correlations with existing events in window
    for (Event event : eventWindow) {
      long distance = timestamp - event.timestamp;
      String pairKey = event.type + "|" + eventType;
      pairStats
          .computeIfAbsent(pairKey, k -> {
            PairStats stats = new PairStats();
            stats.first = event.type;
            stats.second = eventType;
            return stats;
          })
          .recordOccurrence(distance);
      totalPairs++;
    }

    // Record this event
    eventWindow.add(new Event(eventType, timestamp));
    eventCounts.merge(eventType, 1, Integer::sum);
  }

  /**
   * Gets all discovered correlations.
   *
   * @return List of correlations sorted by confidence (highest first)
   */
  public List<Correlation> getCorrelations() {
    return getCorrelations(0.0); // Return all
  }

  /**
   * Gets correlations above a minimum confidence threshold.
   *
   * @param minConfidence Minimum confidence threshold (0-1)
   * @return List of correlations sorted by confidence (highest first)
   */
  public List<Correlation> getCorrelations(double minConfidence) {
    List<Correlation> correlations = new ArrayList<>();

    for (PairStats stats : pairStats.values()) {
      int firstCount = eventCounts.getOrDefault(stats.first, 0);
      double confidence = Math.min(1.0, stats.getConfidence(firstCount));

      if (confidence >= minConfidence) {
        double support =
            totalPairs > 0 ? (double) stats.coOccurrences / totalPairs : 0;
        correlations.add(
            new Correlation(
                stats.first,
                stats.second,
                confidence,
                support,
                stats.coOccurrences,
                stats.getAverageDistance()));
      }
    }

    // Sort by confidence (descending)
    correlations.sort((a, b) -> Double.compare(b.confidence, a.confidence));
    return correlations;
  }

  /**
   * Gets the number of events in the current window.
   *
   * @return Count of events
   */
  public int getEventWindowSize() {
    return eventWindow.size();
  }

  /**
   * Gets the total number of event pairs analyzed.
   *
   * @return Total pairs count
   */
  public long getTotalPairsAnalyzed() {
    return totalPairs;
  }

  /**
   * Gets counts for each event type in the current window.
   *
   * @return Unmodifiable map of event type to count
   */
  public Map<String, Integer> getEventCounts() {
    return Map.copyOf(eventCounts);
  }

  /**
   * Clears all recorded events and statistics.
   */
  public void reset() {
    eventWindow.clear();
    eventCounts.clear();
    pairStats.clear();
    totalPairs = 0;
  }
}
