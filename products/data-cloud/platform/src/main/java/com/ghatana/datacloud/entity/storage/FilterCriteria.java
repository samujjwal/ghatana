package com.ghatana.datacloud.entity.storage;

import java.util.Objects;

/**
 * Filter criteria for entity queries with comparison operators.
 * 
 * <p>Supports various comparison operations:
 * <ul>
 *   <li>EQ - Equality (=)</li>
 *   <li>NE - Not equal (!=)</li>
 *   <li>GT - Greater than (>)</li>
 *   <li>GTE - Greater than or equal (>=)</li>
 *   <li>LT - Less than (<)</li>
 *   <li>LTE - Less than or equal (<=)</li>
 *   <li>LIKE - Pattern matching (LIKE)</li>
 *   <li>IN - Value in list (IN)</li>
 *   <li>IS_NULL - Field is null</li>
 *   <li>IS_NOT_NULL - Field is not null</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b>
 * <pre>{@code
 * // Equality
 * FilterCriteria.eq("status", "active")
 * 
 * // Comparison
 * FilterCriteria.gt("age", 30)
 * FilterCriteria.lte("price", 100.0)
 * 
 * // Pattern matching
 * FilterCriteria.like("name", "John%")
 * 
 * // List containment
 * FilterCriteria.in("category", List.of("electronics", "computers"))
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Filter criteria with comparison operators
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public class FilterCriteria {
    
    public enum Operator {
        EQ("="),           // Equal
        NE("!="),          // Not equal
        GT(">"),           // Greater than
        GTE(">="),         // Greater than or equal
        LT("<"),           // Less than
        LTE("<="),         // Less than or equal
        LIKE("LIKE"),      // Pattern matching
        ILIKE("ILIKE"),    // Case-insensitive pattern matching
        IN("IN"),          // Value in list
        IS_NULL("IS NULL"),         // Field is null
        IS_NOT_NULL("IS NOT NULL"); // Field is not null
        
        private final String sql;
        
        Operator(String sql) {
            this.sql = sql;
        }
        
        public String getSql() {
            return sql;
        }
    }
    
    private final String field;
    private final Operator operator;
    private final Object value;
    
    private FilterCriteria(String field, Operator operator, Object value) {
        this.field = Objects.requireNonNull(field, "field");
        this.operator = Objects.requireNonNull(operator, "operator");
        this.value = value; // Can be null for IS_NULL/IS_NOT_NULL
    }
    
    // Factory methods for common operations
    
    public static FilterCriteria eq(String field, Object value) {
        return new FilterCriteria(field, Operator.EQ, value);
    }
    
    public static FilterCriteria ne(String field, Object value) {
        return new FilterCriteria(field, Operator.NE, value);
    }
    
    public static FilterCriteria gt(String field, Object value) {
        return new FilterCriteria(field, Operator.GT, value);
    }
    
    public static FilterCriteria gte(String field, Object value) {
        return new FilterCriteria(field, Operator.GTE, value);
    }
    
    public static FilterCriteria lt(String field, Object value) {
        return new FilterCriteria(field, Operator.LT, value);
    }
    
    public static FilterCriteria lte(String field, Object value) {
        return new FilterCriteria(field, Operator.LTE, value);
    }
    
    public static FilterCriteria like(String field, String pattern) {
        return new FilterCriteria(field, Operator.LIKE, pattern);
    }
    
    public static FilterCriteria ilike(String field, String pattern) {
        return new FilterCriteria(field, Operator.ILIKE, pattern);
    }
    
    public static FilterCriteria in(String field, Object value) {
        return new FilterCriteria(field, Operator.IN, value);
    }
    
    public static FilterCriteria isNull(String field) {
        return new FilterCriteria(field, Operator.IS_NULL, null);
    }
    
    public static FilterCriteria isNotNull(String field) {
        return new FilterCriteria(field, Operator.IS_NOT_NULL, null);
    }
    
    // Getters
    
    public String getField() {
        return field;
    }
    
    public Operator getOperator() {
        return operator;
    }
    
    public Object getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return "FilterCriteria{" +
                "field='" + field + '\'' +
                ", operator=" + operator +
                ", value=" + value +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterCriteria that = (FilterCriteria) o;
        return Objects.equals(field, that.field) &&
                operator == that.operator &&
                Objects.equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(field, operator, value);
    }
}
