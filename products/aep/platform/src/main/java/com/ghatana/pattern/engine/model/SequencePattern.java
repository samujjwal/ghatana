package com.ghatana.pattern.engine.model;

import java.util.List;

/**
 * Represents a sequence pattern (SEQ) combining multiple patterns in order.
 *
 * <p><b>Purpose</b><br>
 * Composite pattern that matches when sub-patterns occur in strict temporal order.
 *
 * <p><b>Architecture Role</b><br>
 * Part of pattern-engine model hierarchy for pattern composition.
 *
 * @doc.type class
 * @doc.purpose Sequence pattern for ordered pattern matching
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class SequencePattern implements IPatternSpec {
    private final List<IPatternSpec> patterns;

    public SequencePattern(List<IPatternSpec> patterns) {
        this.patterns = patterns;
    }

    @Override
    public List<IPatternSpec> getPatterns() {
        return patterns;
    }

    @Override
    public <T> T accept(PatternVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
