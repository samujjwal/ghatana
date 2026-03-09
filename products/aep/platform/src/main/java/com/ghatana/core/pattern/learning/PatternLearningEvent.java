package com.ghatana.core.pattern.learning;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted during pattern learning.
 *
 * @doc.type class
 * @doc.purpose Pattern learning event representation
 * @doc.layer core
 * @doc.pattern Learning
 */
public class PatternLearningEvent {

    private final LearningEventType eventType;
    private final Object data;
    private final Instant timestamp;

    public PatternLearningEvent(LearningEventType eventType, Object data) {
        this.eventType = Objects.requireNonNull(eventType);
        this.data = data;
        this.timestamp = Instant.now();
    }

    public LearningEventType getEventType() { return eventType; }
    public Object getData() { return data; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("PatternLearningEvent{type=%s, timestamp=%s}", eventType, timestamp);
    }
}
