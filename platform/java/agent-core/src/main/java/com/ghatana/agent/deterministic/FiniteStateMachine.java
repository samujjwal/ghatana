/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight Finite-State Machine (FSM) runtime.
 *
 * <p>Supports:
 * <ul>
 *   <li>Typed states with enter/exit metadata</li>
 *   <li>Guarded transitions (condition-based)</li>
 *   <li>Transition actions (key-value pairs merged into output)</li>
 *   <li>Per-instance state tracking (keyed by entity id)</li>
 *   <li>Thread-safe state access via {@link ConcurrentHashMap}</li>
 * </ul>
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Finite state machine for deterministic agent state transitions
 * @doc.layer platform
 * @doc.pattern Service
 */
public class FiniteStateMachine {

    private static final Logger log = LoggerFactory.getLogger(FiniteStateMachine.class);

    private final FSMDefinition definition;
    private final ConcurrentHashMap<String, String> entityStates = new ConcurrentHashMap<>();

    public FiniteStateMachine(@NotNull FSMDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Transition
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes an event for the given entity and returns the transition result.
     *
     * @param entityId unique entity identifier (e.g. user-id, session-id)
     * @param input    the event data
     * @return transition result (may not have transitioned if no guard matched)
     */
    @NotNull
    public TransitionResult process(@NotNull String entityId, @NotNull Map<String, Object> input) {
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(input, "input must not be null");

        String currentState = entityStates.computeIfAbsent(entityId, k -> definition.getInitialState());

        // Find applicable transitions from current state
        List<FSMDefinition.Transition> transitions = definition.getTransitions().stream()
                .filter(t -> t.getFromState().equals(currentState))
                .sorted(Comparator.comparingInt(FSMDefinition.Transition::getPriority))
                .toList();

        for (FSMDefinition.Transition t : transitions) {
            if (evaluateGuard(t.getGuard(), input)) {
                String previousState = currentState;
                entityStates.put(entityId, t.getToState());

                log.debug("FSM transition: entity={}, {} → {}, trigger={}",
                        entityId, previousState, t.getToState(), t.getName());

                return TransitionResult.builder()
                        .transitioned(true)
                        .previousState(previousState)
                        .currentState(t.getToState())
                        .transitionName(t.getName())
                        .actions(t.getActions())
                        .isFinalState(definition.getFinalStates().contains(t.getToState()))
                        .entityId(entityId)
                        .build();
            }
        }

        // No transition matched
        return TransitionResult.builder()
                .transitioned(false)
                .previousState(currentState)
                .currentState(currentState)
                .actions(Map.of())
                .isFinalState(definition.getFinalStates().contains(currentState))
                .entityId(entityId)
                .build();
    }

    /**
     * Gets the current state for an entity.
     */
    @NotNull
    public String getState(@NotNull String entityId) {
        return entityStates.getOrDefault(entityId, definition.getInitialState());
    }

    /**
     * Resets an entity to the initial state.
     */
    public void reset(@NotNull String entityId) {
        entityStates.put(entityId, definition.getInitialState());
    }

    /**
     * Resets all entities.
     */
    public void resetAll() {
        entityStates.clear();
    }

    /**
     * Returns the number of tracked entities.
     */
    public int getTrackedEntityCount() {
        return entityStates.size();
    }

    // ── Guard evaluation ────────────────────────────────────────────────────

    private boolean evaluateGuard(@Nullable List<RuleCondition> guard, Map<String, Object> input) {
        if (guard == null || guard.isEmpty()) return true; // No guard = always matches
        for (RuleCondition c : guard) {
            if (!c.evaluate(input)) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TransitionResult
    // ═══════════════════════════════════════════════════════════════════════════

    @Value
    @Builder
    public static class TransitionResult {
        /** Whether a state transition occurred. */
        boolean transitioned;
        /** State before processing. */
        @NotNull String previousState;
        /** State after processing. */
        @NotNull String currentState;
        /** Name of the transition that fired (null if no transition). */
        @Nullable String transitionName;
        /** Actions from the transition. */
        @NotNull Map<String, Object> actions;
        /** Whether the current state is a final/accepting state. */
        boolean isFinalState;
        /** The entity id. */
        @NotNull String entityId;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FSM Definition (immutable)
    // ═══════════════════════════════════════════════════════════════════════════

    @Value
    @Builder(toBuilder = true)
    public static class FSMDefinition {

        /** FSM identifier. */
        @NotNull String id;

        /** Human-readable name. */
        @NotNull String name;

        /** Set of all valid states. */
        @Singular @NotNull Set<String> states;

        /** The initial state. */
        @NotNull String initialState;

        /** Set of final/accepting states. */
        @Singular @NotNull Set<String> finalStates;

        /** Ordered list of transitions. */
        @Singular @NotNull List<Transition> transitions;

        @Value
        @Builder(toBuilder = true)
        public static class Transition {
            /** Transition name. */
            @NotNull String name;
            /** Source state. */
            @NotNull String fromState;
            /** Target state. */
            @NotNull String toState;
            /** Guard conditions (all must be true). Null/empty = always matches. */
            @Nullable List<RuleCondition> guard;
            /** Actions to produce when this transition fires. */
            @Builder.Default @NotNull Map<String, Object> actions = Map.of();
            /** Evaluation priority (lower = higher priority). */
            @Builder.Default int priority = 100;
        }
    }
}
