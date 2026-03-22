/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.framework.governance.ActionClass;
import com.ghatana.agent.framework.governance.ReversibilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Classifies agent actions by their governance characteristics.
 *
 * <p>Given an action's tool ID, target type, and contextual hints, this
 * classifier determines the {@link ActionClass} and {@link ReversibilityClass}
 * for governance routing.
 *
 * <p>Classification can be extended by registering tool-specific overrides
 * or pattern-matching rules.
 *
 * @doc.type class
 * @doc.purpose Classifies agent actions for governance routing
 * @doc.layer framework
 * @doc.pattern Strategy
 * @doc.gaa.lifecycle act
 */
public final class ActionClassifier {

    private static final Set<String> READ_TOOL_PATTERNS = Set.of(
            "get", "fetch", "query", "search", "list", "read", "find", "lookup");

    private static final Set<String> EXTERNAL_TOOL_PATTERNS = Set.of(
            "http", "api", "webhook", "email", "sms", "notification", "slack");

    private static final Set<String> IRREVERSIBLE_TOOL_PATTERNS = Set.of(
            "send", "publish", "transfer", "delete-permanent", "broadcast");

    private final Map<String, ActionClass> toolOverrides;
    private final Map<String, ReversibilityClass> reversibilityOverrides;

    /**
     * Creates a classifier with optional tool-specific overrides.
     *
     * @param toolOverrides          tool ID to action class overrides
     * @param reversibilityOverrides tool ID to reversibility class overrides
     */
    public ActionClassifier(
            @NotNull Map<String, ActionClass> toolOverrides,
            @NotNull Map<String, ReversibilityClass> reversibilityOverrides) {
        this.toolOverrides = Map.copyOf(Objects.requireNonNull(toolOverrides));
        this.reversibilityOverrides = Map.copyOf(Objects.requireNonNull(reversibilityOverrides));
    }

    /** Creates a classifier with default heuristics and no overrides. */
    public ActionClassifier() {
        this(Map.of(), Map.of());
    }

    /**
     * Classifies the action class for a tool invocation.
     *
     * @param toolId     the tool being invoked
     * @param targetType the entity type being targeted
     * @param hints      additional contextual hints
     * @return the classified action class
     */
    @NotNull
    public ActionClass classifyAction(
            @NotNull String toolId,
            @NotNull String targetType,
            @NotNull Map<String, Object> hints) {

        // Check explicit overrides first
        if (toolOverrides.containsKey(toolId)) {
            return toolOverrides.get(toolId);
        }

        String lower = toolId.toLowerCase();

        // Pattern-based classification
        if (matchesAny(lower, READ_TOOL_PATTERNS)) {
            return ActionClass.READ;
        }
        if (matchesAny(lower, EXTERNAL_TOOL_PATTERNS)) {
            return ActionClass.CALL_EXTERNAL;
        }
        if (matchesAny(lower, IRREVERSIBLE_TOOL_PATTERNS)) {
            return ActionClass.WRITE_IRREVERSIBLE;
        }
        if (lower.contains("draft") || lower.contains("stage") || lower.contains("preview")) {
            return ActionClass.DRAFT;
        }
        if (lower.contains("delegate") || lower.contains("dispatch") || lower.contains("invoke-agent")) {
            return ActionClass.DELEGATE;
        }
        if (lower.contains("memory") || lower.contains("store") || lower.contains("remember")) {
            return ActionClass.MEMORY_MUTATION;
        }
        if (lower.contains("policy") || lower.contains("config") || lower.contains("rule")) {
            return ActionClass.POLICY_CHANGE;
        }

        // Default: assume reversible write for any unrecognized mutation
        if (lower.contains("create") || lower.contains("update") || lower.contains("write")
                || lower.contains("put") || lower.contains("set")) {
            return ActionClass.WRITE_REVERSIBLE;
        }

        // Conservative default for unknown actions
        return ActionClass.WRITE_REVERSIBLE;
    }

    /**
     * Classifies the reversibility of a tool invocation.
     *
     * @param toolId      the tool being invoked
     * @param actionClass the already-classified action class
     * @return the reversibility class
     */
    @NotNull
    public ReversibilityClass classifyReversibility(
            @NotNull String toolId,
            @NotNull ActionClass actionClass) {

        if (reversibilityOverrides.containsKey(toolId)) {
            return reversibilityOverrides.get(toolId);
        }

        return switch (actionClass) {
            case READ, DRAFT -> ReversibilityClass.REVERSIBLE;
            case WRITE_REVERSIBLE, MEMORY_MUTATION -> ReversibilityClass.COMPENSATABLE;
            case WRITE_IRREVERSIBLE, CALL_EXTERNAL, POLICY_CHANGE -> ReversibilityClass.IRREVERSIBLE;
            case DELEGATE -> ReversibilityClass.COMPENSATABLE;
        };
    }

    private static boolean matchesAny(String value, Set<String> patterns) {
        for (String pattern : patterns) {
            if (value.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
