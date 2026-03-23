package com.ghatana.pattern.engine.model;

import java.util.Collections;
import java.util.List;

/**
 * Base abstract class for pattern specifications.
 */
public abstract class AbstractPatternSpec implements IPatternSpec {
    @Override
    public List<IPatternSpec> getPatterns() {
        return Collections.emptyList();
    }
    
    @Override
    public <T> T accept(PatternVisitor<T> visitor) {
        throw new UnsupportedOperationException("accept must be implemented by concrete pattern classes");
    }
}
