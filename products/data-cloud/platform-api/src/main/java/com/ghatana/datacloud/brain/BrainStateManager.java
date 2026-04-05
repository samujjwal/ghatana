/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.brain;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manager for brain state transitions.
 *
 * <p>Manages brain lifecycle states and transitions between them.
 *
 * @doc.type interface
 * @doc.purpose Brain state management and transitions
 * @doc.layer product
 * @doc.pattern State Machine, Service Interface
 */
public interface BrainStateManager {

    /**
     * Brain states.
     */
    enum BrainState {
        UNINITIALIZED,
        INITIALIZING,
        ACTIVE,
        PAUSED,
        LEARNING,
        RESTING,
        SHUTTING_DOWN,
        TERMINATED
    }

    /**
     * Valid state transitions.
     */
    Set<Map.Entry<BrainState, BrainState>> VALID_TRANSITIONS = Set.of(
        Map.entry(BrainState.UNINITIALIZED, BrainState.INITIALIZING),
        Map.entry(BrainState.INITIALIZING, BrainState.ACTIVE),
        Map.entry(BrainState.INITIALIZING, BrainState.TERMINATED),
        Map.entry(BrainState.ACTIVE, BrainState.PAUSED),
        Map.entry(BrainState.ACTIVE, BrainState.LEARNING),
        Map.entry(BrainState.ACTIVE, BrainState.RESTING),
        Map.entry(BrainState.ACTIVE, BrainState.SHUTTING_DOWN),
        Map.entry(BrainState.PAUSED, BrainState.ACTIVE),
        Map.entry(BrainState.PAUSED, BrainState.SHUTTING_DOWN),
        Map.entry(BrainState.LEARNING, BrainState.ACTIVE),
        Map.entry(BrainState.LEARNING, BrainState.PAUSED),
        Map.entry(BrainState.RESTING, BrainState.ACTIVE),
        Map.entry(BrainState.RESTING, BrainState.PAUSED),
        Map.entry(BrainState.SHUTTING_DOWN, BrainState.TERMINATED)
    );

    /**
     * Transition brain to new state.
     *
     * @param agentId agent identifier
     * @param targetState target state
     * @return promise of transition result
     */
    Promise<TransitionResult> transition(String agentId, BrainState targetState);

    /**
     * Get current brain state.
     *
     * @param agentId agent identifier
     * @return promise of current state
     */
    Promise<BrainState> getState(String agentId);

    /**
     * Get state history.
     *
     * @param agentId agent identifier
     * @param limit max entries
     * @return promise of state history
     */
    Promise<List<StateRecord>> getStateHistory(String agentId, int limit);

    /**
     * Check if transition is valid.
     *
     * @param agentId agent identifier
     * @param targetState target state
     * @return promise of true if valid
     */
    Promise<Boolean> canTransition(String agentId, BrainState targetState);

    /**
     * Wait for state.
     *
     * @param agentId agent identifier
     * @param expectedState state to wait for
     * @param timeout max wait time
     * @return promise of true if reached
     */
    Promise<Boolean> waitForState(String agentId, BrainState expectedState, Duration timeout);

    /**
     * Register state change listener.
     *
     * @param agentId agent identifier
     * @param listener callback
     * @return promise completing when registered
     */
    Promise<Void> onStateChange(String agentId, StateChangeListener listener);

    /**
     * State change listener.
     */
    @FunctionalInterface
    interface StateChangeListener {
        Promise<Void> onChange(BrainState from, BrainState to, TransitionContext context);
    }

    /**
     * State record.
     */
    record StateRecord(
        BrainState state,
        long timestamp,
        String reason,
        long durationMs
    ) {}

    /**
     * Transition result.
     */
    record TransitionResult(
        boolean success,
        BrainState fromState,
        BrainState toState,
        long transitionTimeMs,
        String errorMessage
    ) {
        public boolean isSuccessful() {
            return success;
        }
    }

    /**
     * Transition context.
     */
    record TransitionContext(
        String agentId,
        String triggeredBy,
        Map<String, Object> metadata,
        long timestamp
    ) {}
}
