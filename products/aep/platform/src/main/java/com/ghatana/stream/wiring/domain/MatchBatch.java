package com.ghatana.stream.wiring.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a batch of pattern matches to be processed by the scoring engine.
 * Provides deduplication by matchId and batching for efficient processing.
 */
public class MatchBatch {
    private final String batchId;
    private final Instant timestamp;
    private final Map<String, MatchEvent> matches;
    private final int size;

    public MatchBatch(String batchId, Instant timestamp, Map<String, MatchEvent> matches) {
        this.batchId = Objects.requireNonNull(batchId, "batchId cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.matches = Map.copyOf(Objects.requireNonNull(matches, "matches cannot be null"));
        this.size = matches.size();
    }

    public String getBatchId() {
        return batchId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, MatchEvent> getMatches() {
        return matches;
    }

    public int getSize() {
        return size;
    }

    public boolean isEmpty() {
        return matches.isEmpty();
    }

    public boolean containsMatch(String matchId) {
        if (matches.containsKey(matchId)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchBatch that = (MatchBatch) o;
        return Objects.equals(batchId, that.batchId) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(matches, that.matches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId, timestamp, matches);
    }

    @Override
    public String toString() {
        return "MatchBatch{" +
               "batchId='" + batchId + '\'' +
               ", timestamp=" + timestamp +
               ", size=" + size +
               '}';
    }
}