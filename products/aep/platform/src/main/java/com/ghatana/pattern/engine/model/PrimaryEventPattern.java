package com.ghatana.pattern.engine.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a primary event pattern that matches a single event type.
 */
public class PrimaryEventPattern extends AbstractPatternSpec {
    private final String eventType;
    private final String alias;
    
    public PrimaryEventPattern(String eventType, String alias) {
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.alias = alias;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public String getAlias() {
        return alias;
    }
    
    @Override
    public List<IPatternSpec> getPatterns() {
        return Collections.emptyList();
    }
    
    @Override
    public <T> T accept(PatternVisitor<T> visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimaryEventPattern that = (PrimaryEventPattern) o;
        return eventType.equals(that.eventType) && 
               Objects.equals(alias, that.alias);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventType, alias);
    }
    
    @Override
    public String toString() {
        return alias != null ? 
            String.format("%s as %s", eventType, alias) : 
            eventType;
    }
}
