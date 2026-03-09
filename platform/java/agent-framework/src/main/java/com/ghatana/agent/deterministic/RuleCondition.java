/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * A single condition within a {@link Rule}.
 * Evaluates a dot-path field against a reference value using an {@link Operator}.
 *
 * <p>Supports nested property access via dot notation (e.g. {@code "event.user.country"}).
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Condition predicate for rule evaluation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@Builder(toBuilder = true)
public class RuleCondition {

    /**
     * Dot-separated path to the field in the input map.
     * Example: {@code "amount"}, {@code "user.country"}, {@code "event.type"}.
     */
    @NotNull String field;

    /** Comparison operator. */
    @NotNull Operator operator;

    /** Reference value to compare against (may be null for IS_NULL / IS_NOT_NULL). */
    @Nullable Object value;

    /**
     * Evaluates this condition against the given input map.
     *
     * @param input the event / input map
     * @return true if the condition is satisfied
     */
    public boolean evaluate(@NotNull Map<String, Object> input) {
        Objects.requireNonNull(input, "input must not be null");
        Object actual = resolve(field, input);
        return operator.evaluate(actual, value);
    }

    // ── Convenience factories ───────────────────────────────────────────────

    public static RuleCondition gt(String field, Number value) {
        return builder().field(field).operator(Operator.GREATER_THAN).value(value).build();
    }

    public static RuleCondition gte(String field, Number value) {
        return builder().field(field).operator(Operator.GREATER_THAN_OR_EQUAL).value(value).build();
    }

    public static RuleCondition lt(String field, Number value) {
        return builder().field(field).operator(Operator.LESS_THAN).value(value).build();
    }

    public static RuleCondition lte(String field, Number value) {
        return builder().field(field).operator(Operator.LESS_THAN_OR_EQUAL).value(value).build();
    }

    public static RuleCondition eq(String field, Object value) {
        return builder().field(field).operator(Operator.EQUALS).value(value).build();
    }

    public static RuleCondition neq(String field, Object value) {
        return builder().field(field).operator(Operator.NOT_EQUALS).value(value).build();
    }

    public static RuleCondition contains(String field, String value) {
        return builder().field(field).operator(Operator.CONTAINS).value(value).build();
    }

    public static RuleCondition regex(String field, String pattern) {
        return builder().field(field).operator(Operator.REGEX).value(pattern).build();
    }

    public static RuleCondition isNull(String field) {
        return builder().field(field).operator(Operator.IS_NULL).build();
    }

    public static RuleCondition isNotNull(String field) {
        return builder().field(field).operator(Operator.IS_NOT_NULL).build();
    }

    // ── Dot-path resolution ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Nullable
    public static Object resolve(@NotNull String path, @NotNull Map<String, Object> map) {
        String[] segments = path.split("\\.");
        Object current = map;
        for (String segment : segments) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(segment);
            } else {
                return null;
            }
        }
        return current;
    }
}
