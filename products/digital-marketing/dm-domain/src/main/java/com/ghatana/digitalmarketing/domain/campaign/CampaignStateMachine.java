/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.digitalmarketing.domain.campaign;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Campaign state machine - enforces valid campaign lifecycle transitions.
 *
 * <p>This state machine enforces the campaign lifecycle transitions defined in {@link CampaignStatus}.
 * All state transitions must go through this machine to ensure business rule compliance.</p>
 *
 * <h2>Valid transitions:</h2>
 * <ul>
 *   <li>{@code DRAFT} → {@code PENDING_APPROVAL} → {@code APPROVED}</li>
 *   <li>{@code APPROVED} → {@code PENDING_LAUNCH} → {@code LAUNCH_RUNNING} → {@code LAUNCHED}</li>
 *   <li>{@code PENDING_LAUNCH} → {@code EXTERNAL_EXECUTION_BLOCKED}</li>
 *   <li>{@code PENDING_LAUNCH} → {@code LAUNCH_FAILED}</li>
 *   <li>{@code LAUNCHED} → {@code PAUSED}</li>
 *   <li>{@code LAUNCHED} → {@code COMPLETED}</li>
 *   <li>{@code PAUSED} → {@code COMPLETED}</li>
 *   <li>{@code COMPLETED} → {@code ARCHIVED}</li>
 *   <li>{@code LAUNCH_FAILED} → {@code ROLLED_BACK}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Campaign lifecycle state machine with transition validation
 * @doc.layer product
 * @doc.pattern StateMachine
 */
public class CampaignStateMachine {

    private final EnumMap<CampaignStatus, Set<CampaignStatus>> validTransitions;

    public CampaignStateMachine() {
        this.validTransitions = initializeTransitions();
    }

    /**
     * Check if a state transition is valid.
     *
     * @param from the current state
     * @param to the target state
     * @return true if the transition is valid, false otherwise
     */
    public boolean canTransition(CampaignStatus from, CampaignStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        Set<CampaignStatus> allowed = validTransitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Execute a state transition with validation.
     *
     * @param from the current state
     * @param to the target state
     * @throws IllegalStateException if the transition is invalid
     */
    public void transition(CampaignStatus from, CampaignStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                String.format("Invalid campaign status transition: %s → %s", from, to)
            );
        }
    }

    /**
     * Get the terminal states (states with no outgoing transitions).
     *
     * @return set of terminal states
     */
    public Set<CampaignStatus> getTerminalStates() {
        Set<CampaignStatus> terminal = new HashSet<>();
        for (CampaignStatus status : CampaignStatus.values()) {
            if (validTransitions.get(status).isEmpty()) {
                terminal.add(status);
            }
        }
        return terminal;
    }

    /**
     * Check if a state is terminal.
     *
     * @param status the state to check
     * @return true if the state is terminal
     */
    public boolean isTerminal(CampaignStatus status) {
        return getTerminalStates().contains(status);
    }

    /**
     * Get all valid next states from a given state.
     *
     * @param from the current state
     * @return set of valid next states
     */
    public Set<CampaignStatus> getValidNextStates(CampaignStatus from) {
        Set<CampaignStatus> next = validTransitions.get(from);
        return next != null ? new HashSet<>(next) : new HashSet<>();
    }

    private EnumMap<CampaignStatus, Set<CampaignStatus>> initializeTransitions() {
        EnumMap<CampaignStatus, Set<CampaignStatus>> transitions = new EnumMap<>(CampaignStatus.class);

        // DRAFT → PENDING_APPROVAL
        transitions.put(CampaignStatus.DRAFT, Set.of(CampaignStatus.PENDING_APPROVAL));

        // PENDING_APPROVAL → APPROVED
        transitions.put(CampaignStatus.PENDING_APPROVAL, Set.of(CampaignStatus.APPROVED));

        // APPROVED → PENDING_LAUNCH
        transitions.put(CampaignStatus.APPROVED, Set.of(CampaignStatus.PENDING_LAUNCH));

        // PENDING_LAUNCH → LAUNCH_RUNNING, EXTERNAL_EXECUTION_BLOCKED, LAUNCH_FAILED
        transitions.put(
            CampaignStatus.PENDING_LAUNCH,
            Set.of(
                CampaignStatus.LAUNCH_RUNNING,
                CampaignStatus.EXTERNAL_EXECUTION_BLOCKED,
                CampaignStatus.LAUNCH_FAILED
            )
        );

        // LAUNCH_RUNNING → LAUNCHED
        transitions.put(CampaignStatus.LAUNCH_RUNNING, Set.of(CampaignStatus.LAUNCHED));

        // LAUNCHED → PAUSED, COMPLETED
        transitions.put(CampaignStatus.LAUNCHED, Set.of(CampaignStatus.PAUSED, CampaignStatus.COMPLETED));

        // PAUSED → COMPLETED
        transitions.put(CampaignStatus.PAUSED, Set.of(CampaignStatus.COMPLETED));

        // COMPLETED → ARCHIVED
        transitions.put(CampaignStatus.COMPLETED, Set.of(CampaignStatus.ARCHIVED));

        // LAUNCH_FAILED → ROLLED_BACK
        transitions.put(CampaignStatus.LAUNCH_FAILED, Set.of(CampaignStatus.ROLLED_BACK));

        // EXTERNAL_EXECUTION_BLOCKED has no outgoing transitions
        transitions.put(CampaignStatus.EXTERNAL_EXECUTION_BLOCKED, Set.of());

        // ARCHIVED has no outgoing transitions
        transitions.put(CampaignStatus.ARCHIVED, Set.of());

        // ROLLED_BACK has no outgoing transitions
        transitions.put(CampaignStatus.ROLLED_BACK, Set.of());

        return transitions;
    }
}
