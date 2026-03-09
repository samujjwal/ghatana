package com.ghatana.pattern.engine.model;

import java.util.Collections;
import java.util.List;

/**
 * Represents a negation pattern (NOT) that matches absence of a pattern.
 *
 * <p><b>Purpose</b><br>
 * Composite pattern that matches when a sub-pattern does NOT occur.
 *
 * <p><b>Architecture Role</b><br>
 * Part of pattern-engine model hierarchy for pattern composition.
 *
 * @doc.type class
 * @doc.purpose Negation pattern for absence matching
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class NegationPattern implements IPatternSpec {
    private final IPatternSpec pattern;

    public NegationPattern(IPatternSpec pattern) {
        this.pattern = pattern;
    }

    public IPatternSpec getPattern() {
        return pattern;
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

