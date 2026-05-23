/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.connector.googleads;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Google Ads connector readiness state machine - enforces valid connector state transitions.
 *
 * <p>This state machine enforces the connector readiness lifecycle transitions defined in
 * {@link GoogleAdsConnectorReadinessState}. All state transitions must go through this
 * machine to ensure business rule compliance and prevent invalid state changes.</p>
 *
 * <h2>Valid transitions:</h2>
 * <ul>
 *   <li>{@code NOT_READY} → {@code READY} (when configuration is validated)</li>
 *   <li>{@code READY} → {@code AUTH_FAILED} (when authentication fails)</li>
 *   <li>{@code READY} → {@code RATE_LIMITED} (when API rate limit is hit)</li>
 *   <li>{@code READY} → {@code REMOTE_VALIDATION_FAILED} (when validation fails)</li>
 *   <li>{@code READY} → {@code PUBLISH_FAILED} (when publish operation fails)</li>
 *   <li>{@code READY} → {@code ENVIRONMENT_BLOCKED} (when environment is blocked)</li>
 *   <li>{@code AUTH_FAILED} → {@code READY} (when authentication succeeds)</li>
 *   <li>{@code RATE_LIMITED} → {@code READY} (when rate limit expires)</li>
 *   <li>{@code REMOTE_VALIDATION_FAILED} → {@code READY} (when validation passes)</li>
 *   <li>{@code PUBLISH_FAILED} → {@code READY} (when retry succeeds)</li>
 *   <li>{@code ENVIRONMENT_BLOCKED} → {@code READY} (when environment is unblocked)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Google Ads connector readiness state machine with transition validation
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public final class GoogleAdsConnectorReadinessStateMachine {

    private final EnumMap<GoogleAdsConnectorReadinessState, Set<GoogleAdsConnectorReadinessState>> validTransitions;

    public GoogleAdsConnectorReadinessStateMachine() {
        this.validTransitions = initializeTransitions();
    }

    /**
     * Check if a state transition is valid.
     *
     * @param from the current state
     * @param to the target state
     * @return true if the transition is valid, false otherwise
     */
    public boolean canTransition(GoogleAdsConnectorReadinessState from, GoogleAdsConnectorReadinessState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        Set<GoogleAdsConnectorReadinessState> allowed = validTransitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Execute a state transition with validation.
     *
     * @param from the current state
     * @param to the target state
     * @throws IllegalStateException if the transition is invalid
     */
    public void transition(GoogleAdsConnectorReadinessState from, GoogleAdsConnectorReadinessState to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                String.format("Invalid connector readiness state transition: %s → %s", from, to)
            );
        }
    }

    /**
     * Get the initial state for a newly configured connector.
     *
     * @return the initial state (NOT_READY)
     */
    public GoogleAdsConnectorReadinessState getInitialState() {
        return GoogleAdsConnectorReadinessState.NOT_READY;
    }

    /**
     * Get all valid next states from a given state.
     *
     * @param from the current state
     * @return set of valid next states
     */
    public Set<GoogleAdsConnectorReadinessState> getValidNextStates(GoogleAdsConnectorReadinessState from) {
        Set<GoogleAdsConnectorReadinessState> next = validTransitions.get(from);
        return next != null ? new HashSet<>(next) : new HashSet<>();
    }

    private EnumMap<GoogleAdsConnectorReadinessState, Set<GoogleAdsConnectorReadinessState>> initializeTransitions() {
        EnumMap<GoogleAdsConnectorReadinessState, Set<GoogleAdsConnectorReadinessState>> transitions = 
            new EnumMap<>(GoogleAdsConnectorReadinessState.class);

        // NOT_READY → READY (configuration validated)
        transitions.put(GoogleAdsConnectorReadinessState.NOT_READY, Set.of(GoogleAdsConnectorReadinessState.READY));

        // READY → all failure states
        transitions.put(GoogleAdsConnectorReadinessState.READY, Set.of(
            GoogleAdsConnectorReadinessState.AUTH_FAILED,
            GoogleAdsConnectorReadinessState.RATE_LIMITED,
            GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED,
            GoogleAdsConnectorReadinessState.PUBLISH_FAILED,
            GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED
        ));

        // All failure states → READY (recovery)
        transitions.put(GoogleAdsConnectorReadinessState.AUTH_FAILED, Set.of(GoogleAdsConnectorReadinessState.READY));
        transitions.put(GoogleAdsConnectorReadinessState.RATE_LIMITED, Set.of(GoogleAdsConnectorReadinessState.READY));
        transitions.put(GoogleAdsConnectorReadinessState.REMOTE_VALIDATION_FAILED, Set.of(GoogleAdsConnectorReadinessState.READY));
        transitions.put(GoogleAdsConnectorReadinessState.PUBLISH_FAILED, Set.of(GoogleAdsConnectorReadinessState.READY));
        transitions.put(GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED, Set.of(GoogleAdsConnectorReadinessState.READY));

        return transitions;
    }
}
