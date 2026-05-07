package com.ghatana.pattern.engine.model;

import java.util.List;

/**
 * Represents a conjunction pattern (AND) combining multiple patterns.
 *
 * <p><b>Purpose</b><br>
 * Composite pattern that matches when all sub-patterns occur within a time window.
 *
 * <p><b>Architecture Role</b><br>
 * Part of pattern-engine model hierarchy for pattern composition.
 *
 * @doc.type class
 * @doc.purpose Conjunction pattern for concurrent pattern matching
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class ConjunctionPattern implements IPatternSpec {
    private final List<IPatternSpec> patterns;

    public ConjunctionPattern(List<IPatternSpec> patterns) {
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
