package com.ghatana.refactorer.server.kg.learning;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mines frequent event sequences using the Apriori algorithm.

 *

 * <p>FrequentSequenceMiner discovers common event sequences from a stream of events, identifying

 * patterns that occur with sufficient frequency and confidence. The Apriori algorithm works

 * bottom-up: 1-sequences → 2-sequences → k-sequences, pruning infrequent candidates early.

 *

 * <p>Key Metrics:

 * - Support: Percentage of sequences containing the pattern

 * - Confidence: P(event B | event A occurred)

 * - Lift: How much more likely B follows A than would be expected by chance

 *

 * <p>Example:

 * ```java

 * FrequentSequenceMiner miner = new FrequentSequenceMiner(0.3); // 30% min support

 * miner.addSequence(List.of(\"login\", \"access\", \"logout\"));

 * miner.addSequence(List.of(\"login\", \"data-read\", \"logout\"));

 * miner.addSequence(List.of(\"login\", \"access\", \"error\"));

 * List<Pattern> patterns = miner.mine();

 * // Returns: [Pattern(\"login->access\", support=0.66, confidence=0.66)]

 * ```

 *

 * <p>Binding Decision #11: Learning operators provide pattern discovery without direct KG library

 * coupling, enabling customizable mining algorithms and straightforward evaluation metrics.

 *

 * @doc.type class

 * @doc.purpose Run analytics algorithms over event sequences to surface correlations.

 * @doc.layer product

 * @doc.pattern Domain Service

 */

public final class FrequentSequenceMiner {
  private final double minSupport;
  private final List<List<String>> sequences;
  private final Map<String, Integer> eventFrequency;

  /**
   * Represents a discovered frequent pattern.
   */
  public record Pattern(
      String sequence,
      List<String> events,
      double support,
      double confidence,
      long occurrences) {

    /**
     * Human-readable format: "event1 -> event2 -> event3 (sup: 0.45, conf: 0.78)"
     */
    @Override
    public String toString() {
      String eventChain = String.join(" -> ", events);
      return String.format("%s (sup: %.2f, conf: %.2f, occ: %d)", eventChain, support, confidence, occurrences);
    }
  }

  /**
   * Creates a miner with specified minimum support threshold.
   *
   * @param minSupport Minimum support threshold (0-1, e.g., 0.3 for 30%)
   */
  public FrequentSequenceMiner(double minSupport) {
    if (minSupport < 0 || minSupport > 1) {
      throw new IllegalArgumentException("Minimum support must be between 0 and 1");
    }
    this.minSupport = minSupport;
    this.sequences = new ArrayList<>();
    this.eventFrequency = new HashMap<>();
  }

  /**
   * Adds a sequence of events to the mining dataset.
   *
   * @param sequence List of event types in order
   */
  public void addSequence(List<String> sequence) {
    if (sequence == null || sequence.isEmpty()) {
      throw new IllegalArgumentException("Sequence cannot be null or empty");
    }
    sequences.add(new ArrayList<>(sequence));

    // Track 1-item frequencies
    for (String event : sequence) {
      eventFrequency.merge(event, 1, Integer::sum);
    }
  }

  /**
   * Mines frequent sequences using Apriori algorithm.
   *
   * @return List of frequent patterns sorted by support (highest first)
   */
  public List<Pattern> mine() {
    if (sequences.isEmpty()) {
      return Collections.emptyList();
    }

    int totalSequences = sequences.size();
    List<Pattern> allPatterns = new ArrayList<>();

    // Generate 1-item frequent itemsets
    Set<List<String>> candidates = new HashSet<>();
    for (String event : eventFrequency.keySet()) {
      int count = eventFrequency.get(event);
      double support = (double) count / totalSequences;
      if (support >= minSupport) {
        candidates.add(List.of(event));
      }
    }

    if (candidates.isEmpty()) {
      return Collections.emptyList();
    }

    // Iteratively generate k-itemsets
    Set<List<String>> currentItemsets = candidates;
    for (int k = 1; !currentItemsets.isEmpty() && k <= 5; k++) { // Limit to k=5 for performance
      // Count support for current itemsets
      Map<List<String>, Integer> supportCount = countSupport(currentItemsets);

      // Create patterns from frequent itemsets
      for (List<String> itemset : supportCount.keySet()) {
        int count = supportCount.get(itemset);
        double support = (double) count / totalSequences;
        if (support >= minSupport) {
          // Calculate confidence (for binary sequences)
          double confidence = calculateConfidence(itemset, support);
          String sequence = String.join(" -> ", itemset);
          allPatterns.add(new Pattern(sequence, new ArrayList<>(itemset), support, confidence, count));
        }
      }

      // Generate candidates for next level
      currentItemsets = generateCandidates(supportCount.keySet());
    }

    // Sort by support (descending)
    allPatterns.sort((a, b) -> Double.compare(b.support, a.support));
    return allPatterns;
  }

  /**
   * Counts support for each itemset in the sequences.
   */
  private Map<List<String>, Integer> countSupport(Set<List<String>> itemsets) {
    Map<List<String>, Integer> supportCount = new HashMap<>();

    for (List<String> sequence : sequences) {
      for (List<String> itemset : itemsets) {
        if (isSubsequence(itemset, sequence)) {
          supportCount.merge(itemset, 1, Integer::sum);
        }
      }
    }

    return supportCount;
  }

  /**
   * Checks if pattern is a subsequence of sequence (maintaining order).
   */
  private boolean isSubsequence(List<String> pattern, List<String> sequence) {
    int patternIdx = 0;
    for (String event : sequence) {
      if (patternIdx < pattern.size() && event.equals(pattern.get(patternIdx))) {
        patternIdx++;
      }
    }
    return patternIdx == pattern.size();
  }

  /**
   * Calculates confidence for a sequence (P(B|A) for A->B).
   */
  private double calculateConfidence(List<String> itemset, double support) {
    if (itemset.size() < 2) {
      return 0.0;
    }

    // For a sequence [A, B], confidence = P(A,B) / P(A)
    String firstEvent = itemset.get(0);
    int firstEventCount = eventFrequency.getOrDefault(firstEvent, 0);
    int totalSequences = sequences.size();

    if (firstEventCount == 0) {
      return 0.0;
    }

    // Count occurrences of the full sequence
    int sequenceCount = 0;
    for (List<String> seq : sequences) {
      if (isSubsequence(itemset, seq)) {
        sequenceCount++;
      }
    }

    double firstEventSupport = (double) firstEventCount / totalSequences;
    return firstEventSupport > 0 ? support / firstEventSupport : 0.0;
  }

  /**
   * Generates candidate itemsets for the next level (k+1).
   */
  private Set<List<String>> generateCandidates(Set<List<String>> currentItemsets) {
    Set<List<String>> candidates = new HashSet<>();
    List<List<String>> itemsetList = new ArrayList<>(currentItemsets);

    // Generate by joining itemsets that share k-1 items
    for (int i = 0; i < itemsetList.size(); i++) {
      for (int j = i + 1; j < itemsetList.size(); j++) {
        List<String> set1 = itemsetList.get(i);
        List<String> set2 = itemsetList.get(j);

        if (set1.size() == set2.size()) {
          // Check if first k-1 items match
          boolean canJoin = true;
          for (int k = 0; k < set1.size() - 1; k++) {
            if (!set1.get(k).equals(set2.get(k))) {
              canJoin = false;
              break;
            }
          }

          if (canJoin && !set1.get(set1.size() - 1).equals(set2.get(set2.size() - 1))) {
            // Generate both orderings for sequence mining (order matters)
            List<String> candidate1 = new ArrayList<>(set1);
            candidate1.add(set2.get(set2.size() - 1));
            candidates.add(candidate1);

            List<String> candidate2 = new ArrayList<>(set2);
            candidate2.add(set1.get(set1.size() - 1));
            candidates.add(candidate2);
          }
        }
      }
    }

    return candidates;
  }

  /**
   * Gets the total number of sequences analyzed.
   *
   * @return Sequence count
   */
  public int getSequenceCount() {
    return sequences.size();
  }

  /**
   * Gets the minimum support threshold.
   *
   * @return Min support (0-1)
   */
  public double getMinSupport() {
    return minSupport;
  }

  /**
   * Mines patterns and filters by minimum confidence.
   *
   * @param minConfidence Minimum confidence threshold (0-1)
   * @return Patterns meeting both support and confidence thresholds
   */
  public List<Pattern> mineWithConfidence(double minConfidence) {
    return mine().stream()
        .filter(p -> p.confidence >= minConfidence)
        .collect(Collectors.toList());
  }
}
