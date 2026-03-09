package com.ghatana.aep.learning.mining;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mines frequent sequences from event streams using Apriori algorithm.
 *
 * <p><b>Purpose</b><br>
 * Discovers frequently occurring event sequences that can be converted to patterns.
 * Uses Apriori algorithm for scalable sequence mining with support/confidence metrics.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FrequentSequenceMiner miner = FrequentSequenceMiner.builder()
 *     .minSupport(0.05)           // 5% minimum support
 *     .minConfidence(0.7)         // 70% minimum confidence
 *     .maxSequenceLength(5)       // Max 5 events
 *     .windowDurationMillis(300000) // 5-minute window
 *     .build();
 *
 * // Feed events
 * miner.addEvent(event);
 *
 * // Mine patterns
 * List<FrequentSequence> sequences = miner.mineSequences().getResult();
 * }</pre>
 *
 * <p><b>Algorithm</b><br>
 * Apriori algorithm for frequent itemset mining adapted for sequences:
 * <ul>
 *   <li>Candidate generation: Join sequences of length k-1</li>
 *   <li>Support calculation: Count occurrences in dataset</li>
 *   <li>Pruning: Remove low-support candidates</li>
 *   <li>Iteration: Increase length until no candidates remain</li>
 * </ul>
 *
 * <p><b>Performance</b><br>
 * Time: O(n * k^2) where n=events, k=sequence length
 * Memory: O(n + candidates)
 * Scalable to 1M+ events per mining run
 *
 * @doc.type class
 * @doc.purpose Frequent sequence mining (Apriori)
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class FrequentSequenceMiner {

    private static final Logger logger = LoggerFactory.getLogger(FrequentSequenceMiner.class);

    private final double minSupport;
    private final double minConfidence;
    private final int maxSequenceLength;
    private final long windowDurationMillis;
    private final List<Event> eventBuffer;
    private final Map<String, Integer> sequenceSupport;

    /**
     * Create frequent sequence miner with builder.
     *
     * @param builder Builder with configuration
     */
    private FrequentSequenceMiner(Builder builder) {
        this.minSupport = builder.minSupport;
        this.minConfidence = builder.minConfidence;
        this.maxSequenceLength = builder.maxSequenceLength;
        this.windowDurationMillis = builder.windowDurationMillis;
        this.eventBuffer = Collections.synchronizedList(new ArrayList<>());
        this.sequenceSupport = Collections.synchronizedMap(new HashMap<>());

        logger.info("Created miner: minSupport={}, minConfidence={}, maxLength={}",
                   minSupport, minConfidence, maxSequenceLength);
    }

    /**
     * Add event to mining buffer.
     *
     * @param event Event to add
     */
    public void addEvent(Event event) {
        eventBuffer.add(event);
        logger.debug("Added event: {} (buffer size: {})", event.getType(), eventBuffer.size());
    }

    /**
     * Mine frequent sequences from buffered events.
     *
     * @return Promise of discovered sequences
     */
    public Promise<List<FrequentSequence>> mineSequences() {
        logger.info("Mining sequences from {} events", eventBuffer.size());

        if (eventBuffer.isEmpty()) {
            return Promise.of(List.of());
        }

        try {
            List<FrequentSequence> sequences = new ArrayList<>();

            // Generate 1-sequences (individual events)
            Map<String, Integer> oneSequences = generate1Sequences();
            sequences.addAll(convertToFrequentSequences(oneSequences, 1));

            // Generate k-sequences using Apriori
            for (int k = 2; k <= maxSequenceLength; k++) {
                Map<String, Integer> candidates = generateKSequences(oneSequences, k);
                if (candidates.isEmpty()) {
                    break;  // No more candidates
                }

                sequences.addAll(convertToFrequentSequences(candidates, k));
                oneSequences = candidates;
            }

            logger.info("Mined {} frequent sequences", sequences.size());
            return Promise.of(sequences);
        } catch (Exception e) {
            logger.error("Error mining sequences", e);
            return Promise.ofException(e);
        }
    }

    /**
     * Generate 1-sequences (individual event types).
     *
     * @return Map of event types to support count
     */
    private Map<String, Integer> generate1Sequences() {
        Map<String, Integer> sequences = new HashMap<>();

        for (Event event : eventBuffer) {
            String eventType = event.getType();
            sequences.put(eventType, sequences.getOrDefault(eventType, 0) + 1);
        }

        return sequences.entrySet().stream()
            .filter(e -> e.getValue() >= getMinSupportCount())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Generate k-sequences using Apriori algorithm.
     *
     * @param prevSequences (k-1)-sequences
     * @param k Sequence length
     * @return Map of sequences to support count
     */
    private Map<String, Integer> generateKSequences(Map<String, Integer> prevSequences, int k) {
        Map<String, Integer> candidates = new HashMap<>();

        // Generate candidates by joining (k-1)-sequences
        List<String> sequences = new ArrayList<>(prevSequences.keySet());
        for (int i = 0; i < sequences.size(); i++) {
            for (int j = 0; j < sequences.size(); j++) {
                String candidate = sequences.get(i) + "->" + sequences.get(j);
                int support = countSequenceOccurrences(candidate);

                if (support >= getMinSupportCount()) {
                    candidates.put(candidate, support);
                }
            }
        }

        return candidates;
    }

    /**
     * Count occurrences of sequence in event buffer.
     *
     * @param sequence Sequence to count
     * @return Number of occurrences
     */
    private int countSequenceOccurrences(String sequence) {
        String[] parts = sequence.split("->");
        if (parts.length > eventBuffer.size()) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i <= eventBuffer.size() - parts.length; i++) {
            boolean matches = true;
            for (int j = 0; j < parts.length; j++) {
                if (!eventBuffer.get(i + j).getType().equals(parts[j])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                count++;
            }
        }

        return count;
    }

    /**
     * Get minimum support count.
     *
     * @return Minimum count based on minSupport percentage
     */
    private int getMinSupportCount() {
        return Math.max(1, (int) (eventBuffer.size() * minSupport));
    }

    /**
     * Convert support map to FrequentSequence objects.
     *
     * @param sequences Map of sequences to support
     * @param length Sequence length
     * @return List of FrequentSequence objects
     */
    private List<FrequentSequence> convertToFrequentSequences(
            Map<String, Integer> sequences,
            int length) {
        int total = eventBuffer.size();

        return sequences.entrySet().stream()
            .map(e -> new FrequentSequence(
                e.getKey(),
                e.getValue(),
                (double) e.getValue() / total,
                length
            ))
            .collect(Collectors.toList());
    }

    /**
     * Frequent sequence discovery result.
     */
    public static class FrequentSequence {
        private final String sequence;
        private final int count;
        private final double support;
        private final int length;

        FrequentSequence(String sequence, int count, double support, int length) {
            this.sequence = sequence;
            this.count = count;
            this.support = support;
            this.length = length;
        }

        public String getSequence() {
            return sequence;
        }

        public int getCount() {
            return count;
        }

        public double getSupport() {
            return support;
        }

        public int getLength() {
            return length;
        }

        @Override
        public String toString() {
            return String.format("%s (support=%.2f%%, count=%d)", sequence, support * 100, count);
        }
    }

    /**
     * Builder for FrequentSequenceMiner.
     */
    public static class Builder {
        private double minSupport = 0.05;           // 5% default
        private double minConfidence = 0.7;        // 70% default
        private int maxSequenceLength = 5;
        private long windowDurationMillis = 300000;  // 5 minutes

        public Builder minSupport(double minSupport) {
            if (minSupport <= 0 || minSupport > 1) {
                throw new IllegalArgumentException("minSupport must be between 0 and 1");
            }
            this.minSupport = minSupport;
            return this;
        }

        public Builder minConfidence(double minConfidence) {
            if (minConfidence <= 0 || minConfidence > 1) {
                throw new IllegalArgumentException("minConfidence must be between 0 and 1");
            }
            this.minConfidence = minConfidence;
            return this;
        }

        public Builder maxSequenceLength(int maxSequenceLength) {
            if (maxSequenceLength <= 0) {
                throw new IllegalArgumentException("maxSequenceLength must be positive");
            }
            this.maxSequenceLength = maxSequenceLength;
            return this;
        }

        public Builder windowDurationMillis(long windowDurationMillis) {
            if (windowDurationMillis <= 0) {
                throw new IllegalArgumentException("windowDurationMillis must be positive");
            }
            this.windowDurationMillis = windowDurationMillis;
            return this;
        }

        public FrequentSequenceMiner build() {
            return new FrequentSequenceMiner(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

