package com.ghatana.datacloud.entity.storage;

import java.util.Objects;

/**
 * Sort specification for entity queries with direction control.
 * 
 * <p>Defines how to sort query results by field with ascending or descending order.
 * Supports both system fields (createdAt, updatedAt, version) and JSONB data fields.
 * 
 * <p><b>Usage Examples:</b>
 * <pre>{@code
 * // Sort by name ascending
 * SortSpec.asc("name")
 * 
 * // Sort by created date descending (most recent first)
 * SortSpec.desc("createdAt")
 * 
 * // Sort by price descending
 * SortSpec.desc("price")
 * 
 * // Default sort (descending)
 * SortSpec.of("age")  // Same as SortSpec.desc("age")
 * }</pre>
 * 
 * <p><b>Multi-field sorting:</b>
 * <pre>{@code
 * List<SortSpec> sorts = List.of(
 *     SortSpec.asc("category"),   // Primary: category ascending
 *     SortSpec.desc("price"),     // Secondary: price descending
 *     SortSpec.desc("createdAt")  // Tertiary: created date descending
 * );
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Sort specification with direction control
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class SortSpec {
    
    public enum Direction {
        ASC("ASC"),
        DESC("DESC");
        
        private final String sql;
        
        Direction(String sql) {
            this.sql = sql;
        }
        
        public String getSql() {
            return sql;
        }
    }
    
    private final String field;
    private final Direction direction;
    
    private SortSpec(String field, Direction direction) {
        this.field = Objects.requireNonNull(field, "field");
        this.direction = Objects.requireNonNull(direction, "direction");
    }
    
    // Factory methods
    
    /**
     * Create ascending sort specification.
     */
    public static SortSpec asc(String field) {
        return new SortSpec(field, Direction.ASC);
    }
    
    /**
     * Create descending sort specification.
     */
    public static SortSpec desc(String field) {
        return new SortSpec(field, Direction.DESC);
    }
    
    /**
     * Create sort specification with default direction (descending).
     */
    public static SortSpec of(String field) {
        return new SortSpec(field, Direction.DESC);
    }
    
    /**
     * Create sort specification with explicit direction.
     */
    public static SortSpec of(String field, Direction direction) {
        return new SortSpec(field, direction);
    }
    
    // Getters
    
    public String getField() {
        return field;
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    @Override
    public String toString() {
        return "SortSpec{" +
                "field='" + field + '\'' +
                ", direction=" + direction +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SortSpec sortSpec = (SortSpec) o;
        return Objects.equals(field, sortSpec.field) &&
                direction == sortSpec.direction;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }
}
