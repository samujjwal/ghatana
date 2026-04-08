/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.planning;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Immutable directed acyclic graph (DAG) of {@link PlannedAction} nodes.
 *
 * <p>A {@code PlanGraph} is the output of a planning agent. It models the set of
 * actions that must be executed to fulfil an objective, together with the dependency
 * ordering between them. The graph must be acyclic — a {@link PlanCycleException}
 * is thrown during construction if a cycle is detected.
 *
 * <p>Usage:
 * <pre>{@code
 * PlanGraph plan = PlanGraph.of("plan-1", "agent-1", List.of(
 *     PlannedAction.simple("a1", "Retrieve context", ActionClass.READ),
 *     new PlannedAction("a2", "Call weather API", "weather-lookup",
 *         Set.of("a1"), ActionClass.CALL_EXTERNAL, false)));
 * }</pre>
 *
 * @param planId    unique plan identifier; must not be blank
 * @param agentId   the planning agent that produced this graph; must not be blank
 * @param objective human-readable statement of the plan objective; must not be blank
 * @param actions   ordered list of {@link PlannedAction} nodes; must form a DAG
 * @param createdAt timestamp when this plan was compiled
 *
 * @doc.type record
 * @doc.purpose Immutable DAG of planned actions produced by a planning agent
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record PlanGraph(
        @NotNull String planId,
        @NotNull String agentId,
        @NotNull String objective,
        @NotNull List<PlannedAction> actions,
        @NotNull Instant createdAt
) {
    /**
     * Compact constructor — validates required fields, makes actions immutable, and checks for cycles.
     *
     * @throws PlanCycleException if the dependency graph contains a cycle
     * @throws IllegalArgumentException if a dependency references an unknown action ID
     */
    public PlanGraph {
        if (Objects.requireNonNull(planId, "planId").isBlank()) {
            throw new IllegalArgumentException("planId must not be blank");
        }
        if (Objects.requireNonNull(agentId, "agentId").isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (Objects.requireNonNull(objective, "objective").isBlank()) {
            throw new IllegalArgumentException("objective must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        validateAndDetectCycles(actions);
    }

    /**
     * Factory method — uses the current instant for {@code createdAt}.
     */
    @NotNull
    public static PlanGraph of(
            @NotNull String planId,
            @NotNull String agentId,
            @NotNull String objective,
            @NotNull List<PlannedAction> actions) {
        return new PlanGraph(planId, agentId, objective, actions, Instant.now());
    }

    /**
     * Returns all action IDs that have no dependencies (root nodes).
     */
    @NotNull
    public List<PlannedAction> roots() {
        return actions.stream()
                .filter(a -> a.dependencies().isEmpty())
                .toList();
    }

    /**
     * Returns all actions that depend on the given {@code actionId}.
     */
    @NotNull
    public List<PlannedAction> dependents(@NotNull String actionId) {
        Objects.requireNonNull(actionId, "actionId");
        return actions.stream()
                .filter(a -> a.dependencies().contains(actionId))
                .toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private static void validateAndDetectCycles(List<PlannedAction> actions) {
        Set<String> knownIds = new HashSet<>();
        for (PlannedAction a : actions) {
            knownIds.add(a.actionId());
        }
        for (PlannedAction a : actions) {
            for (String dep : a.dependencies()) {
                if (!knownIds.contains(dep)) {
                    throw new IllegalArgumentException(
                            "Action '" + a.actionId() + "' references unknown dependency '" + dep + "'");
                }
            }
        }
        // Kahn's algorithm for cycle detection
        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (PlannedAction a : actions) {
            deps.put(a.actionId(), new HashSet<>(a.dependencies()));
            inDegree.put(a.actionId(), a.dependencies().size());
        }
        Queue<String> queue = new ArrayDeque<>();
        for (PlannedAction a : actions) {
            if (a.dependencies().isEmpty()) {
                queue.add(a.actionId());
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (PlannedAction a : actions) {
                if (a.dependencies().contains(current)) {
                    int newDegree = inDegree.merge(a.actionId(), -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(a.actionId());
                    }
                }
            }
        }
        if (visited < actions.size()) {
            throw new PlanCycleException(
                    "PlanGraph contains a dependency cycle. Only " + visited
                    + " of " + actions.size() + " actions were reachable.");
        }
    }
}
