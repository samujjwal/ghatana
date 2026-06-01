package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical condition specification for PatternSpec.
 *
 * <p>Defines conditional logic for pattern matching, including
 * predicates, filters, and guard conditions.
 *
 * @doc.type record
 * @doc.purpose Canonical condition specification for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternCondition(
        String type,
        String expression,
        Map<String, Object> parameters,
        Map<String, Object> options) {

    public PatternCondition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(expression, "expression");
    }

    /**
     * Create a simple predicate condition.
     *
     * @param expression predicate expression
     * @return PatternCondition instance
     */
    public static PatternCondition predicate(String expression) {
        return new PatternCondition("predicate", expression, null, null);
    }

    /**
     * Create a filter condition.
     *
     * @param expression filter expression
     * @return PatternCondition instance
     */
    public static PatternCondition filter(String expression) {
        return new PatternCondition("filter", expression, null, null);
    }

    /**
     * Create a guard condition.
     *
     * @param expression guard expression
     * @return PatternCondition instance
     */
    public static PatternCondition guard(String expression) {
        return new PatternCondition("guard", expression, null, null);
    }

    /**
     * Convert this PatternCondition to a map representation.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("type", type);
        map.put("expression", expression);
        if (parameters != null && !parameters.isEmpty()) map.put("parameters", parameters);
        if (options != null && !options.isEmpty()) map.put("options", options);
        return java.util.Collections.unmodifiableMap(map);
    }
}
