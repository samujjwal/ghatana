package com.ghatana.pattern.engine.model;

import java.util.List;

/**
 * Base interface for all pattern specifications.
 */
public interface IPatternSpec {
    /**
     * @return List of sub-patterns that make up this pattern
     */
    List<IPatternSpec> getPatterns();
    
    /**
     * Accepts a visitor for this pattern.
     */
    <T> T accept(PatternVisitor<T> visitor);
}
