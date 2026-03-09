package com.ghatana.stream.wiring.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a pattern match event from the detection pipeline.
 * Contains match metadata and performance data for scoring engine updates.
 */
public class MatchEvent {
    private final String matchId;
    private final String patternId;
    private final String tenantId;
    private final Instant matchTime;
    private final double confidence;
    private final long processingLatencyMs;
    private final Map<String, Object> context;
    private final MatchOutcome outcome;

    public MatchEvent(String matchId, String patternId, String tenantId, 
                     Instant matchTime, double confidence, long processingLatencyMs, 
                     Map<String, Object> context, MatchOutcome outcome) {
        this.matchId = Objects.requireNonNull(matchId, "matchId cannot be null");
        this.patternId = Objects.requireNonNull(patternId, "patternId cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.matchTime = Objects.requireNonNull(matchTime, "matchTime cannot be null");
        this.confidence = confidence;
        this.processingLatencyMs = processingLatencyMs;
        this.context = Map.copyOf(Objects.requireNonNull(context, "context cannot be null"));
        this.outcome = Objects.requireNonNull(outcome, "outcome cannot be null");
    }

    public String getMatchId() {
        return matchId;
    }

    public String getPatternId() {
        return patternId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Instant getMatchTime() {
        return matchTime;
    }

    public double getConfidence() {
        return confidence;
    }

    public long getProcessingLatencyMs() {
        return processingLatencyMs;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public MatchOutcome getOutcome() {
        return outcome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MatchEvent that = (MatchEvent) o;
        return Objects.equals(matchId, that.matchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId);
    }

    @Override
    public String toString() {
        return "MatchEvent{" +
               "matchId='" + matchId + '\'' +
               ", patternId='" + patternId + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", confidence=" + confidence +
               ", outcome=" + outcome +
               '}';
    }

    /**
     * Outcome of pattern match processing for scoring feedback.
     */
    public enum MatchOutcome {
        SUCCESS,     // Match processed successfully
        FAILURE,     // Match processing failed
        TIMEOUT,     // Match processing timed out
        REJECTED     // Match rejected due to quality/confidence
    }
}