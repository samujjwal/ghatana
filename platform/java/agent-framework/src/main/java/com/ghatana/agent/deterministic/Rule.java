/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A deterministic rule consisting of one or more {@link RuleCondition}s (ANDed)
 * and an action to produce when all conditions match.
 *
 * <p>Rules are evaluated in priority order (lower number = higher priority).
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Represents a single evaluation rule with conditions and actions
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@Builder(toBuilder = true)
public class Rule {

    /** Unique rule identifier. */
    @NotNull String id;

    /** Human-readable name. */
    @NotNull String name;

    /** Optional description. */
    @Nullable String description;

    /** Priority — lower is higher priority. Default 100. */
    @Builder.Default int priority = 100;

    /** Conditions that must ALL be true for this rule to fire. */
    @Singular @NotNull List<RuleCondition> conditions;

    /** Key-value pairs merged into the output when this rule fires. */
    @Singular @NotNull Map<String, Object> actions;

    /** Whether to stop evaluating subsequent rules after this one fires. */
    @Builder.Default boolean terminal = true;

    /**
     * Evaluates whether all conditions in this rule are satisfied.
     *
     * @param input the event input
     * @return true if every condition evaluates to true
     */
    public boolean matches(@NotNull Map<String, Object> input) {
        if (conditions.isEmpty()) return false; // Safety: never fire an empty rule
        for (RuleCondition c : conditions) {
            if (!c.evaluate(input)) return false;
        }
        return true;
    }
}
