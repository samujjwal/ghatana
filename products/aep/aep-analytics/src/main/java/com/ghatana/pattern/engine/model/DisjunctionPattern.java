package com.ghatana.pattern.engine.model;

import java.util.List;

/**
 * Represents a disjunction pattern (OR) combining multiple patterns.
 *
 * <p><b>Purpose</b><br>
 * Composite pattern that matches when any sub-pattern occurs.
 *
 * <p><b>Architecture Role</b><br>
 * Part of pattern-engine model hierarchy for pattern composition.
 *
 * @doc.type class
 * @doc.purpose Disjunction pattern for alternative pattern matching
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class DisjunctionPattern implements IPatternSpec {
    private final List<IPatternSpec> patterns;

    public DisjunctionPattern(List<IPatternSpec> patterns) {
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
