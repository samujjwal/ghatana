package com.ghatana.pattern.engine.model;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Represents a temporal constraint pattern (WITHIN) constraining pattern matching to a time window.
 *
 * <p><b>Purpose</b><br>
 * Composite pattern that matches sub-pattern within a specified time window.
 *
 * <p><b>Architecture Role</b><br>
 * Part of pattern-engine model hierarchy for temporal pattern composition.
 *
 * @doc.type class
 * @doc.purpose Temporal constraint pattern for windowed matching
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class WithinPattern implements IPatternSpec {
    private final IPatternSpec pattern;
    private final Duration timeWindow;

    public WithinPattern(IPatternSpec pattern, Duration timeWindow) {
        this.pattern = pattern;
        this.timeWindow = timeWindow;
    }

    public IPatternSpec getPattern() {
        return pattern;
    }

    public Duration getTimeWindow() {
        return timeWindow;
    }

    @Override
    public List<IPatternSpec> getPatterns() {
        return Collections.singletonList(pattern);
    }

    @Override
    public <T> T accept(PatternVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
