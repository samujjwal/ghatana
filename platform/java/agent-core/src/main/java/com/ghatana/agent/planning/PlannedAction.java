/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.planning;

import com.ghatana.agent.framework.governance.ActionClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * An immutable record representing a single planned action within a {@link PlanGraph}.
 *
 * <p>Each planned action captures:
 * <ul>
 *   <li>A unique action identifier within the plan.</li>
 *   <li>A human-readable specification describing the intended action.</li>
 *   <li>An optional tool identifier to be invoked.</li>
 *   <li>The set of action IDs that must complete before this action may start.</li>
 *   <li>The {@link ActionClass} governing risk and approval requirements.</li>
 *   <li>Whether the action requires explicit human approval before execution.</li>
 * </ul>
 *
 * @param actionId         unique identifier for this action within the plan; must not be blank
 * @param specification    human-readable description of what this action does; must not be blank
 * @param toolId           optional tool identifier to invoke; null for reasoning-only steps
 * @param dependencies     set of {@code actionId}s that must complete before this action; never null
 * @param actionClass      risk classification of this action; must not be null
 * @param requiresApproval whether human approval is required before this action executes
 *
 * @doc.type record
 * @doc.purpose Immutable planned action node in a PlanGraph
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PlannedAction(
        @NotNull  String actionId,
        @NotNull  String specification,
        @Nullable String toolId,
        @NotNull  Set<String> dependencies,
        @NotNull  ActionClass actionClass,
        boolean requiresApproval
) {
    /**
     * Compact constructor — validates required fields and makes dependencies immutable.
     */
    public PlannedAction {
        if (Objects.requireNonNull(actionId, "actionId").isBlank()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        if (Objects.requireNonNull(specification, "specification").isBlank()) {
            throw new IllegalArgumentException("specification must not be blank");
        }
        Objects.requireNonNull(actionClass, "actionClass");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }

    /**
     * Factory for a simple action with no dependencies that does not require approval.
     *
     * @param actionId      identifier for this action
     * @param specification description of the action
     * @param actionClass   risk classification
     * @return a new {@code PlannedAction} with no tool and no dependencies
     */
    @NotNull
    public static PlannedAction simple(
            @NotNull String actionId,
            @NotNull String specification,
            @NotNull ActionClass actionClass) {
        return new PlannedAction(actionId, specification, null, Set.of(), actionClass, false);
    }
}
