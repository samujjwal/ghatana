/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.lifecycle;

import java.util.Set;

/**
 * Pipeline lifecycle states and transitions for Action Plane/AEP.
 * 
 * P7.1: Defines canonical pipeline lifecycle states and valid transitions.
 * 
 * Lifecycle Flow:
 * DRAFT -> VALIDATED -> ACTIVE -> PAUSED -> COMPLETED
 *         -> FAILED
 * Any state -> ARCHIVED
 * FAILED -> DRAFT (for retry)
 * PAUSED -> ACTIVE (resume)
 * 
 * @doc.type enum
 * @doc.purpose Pipeline lifecycle state definition
 * @doc.layer product
 * @doc.pattern State Machine
 */
public enum PipelineLifecycle {
    /**
     * Pipeline is being designed and configured.
     * Can transition to: VALIDATED, ARCHIVED
     */
    DRAFT(Set.of("VALIDATED", "ARCHIVED")),
    
    /**
     * Pipeline has passed validation and is ready for execution.
     * Can transition to: ACTIVE, ARCHIVED
     */
    VALIDATED(Set.of("ACTIVE", "ARCHIVED")),
    
    /**
     * Pipeline is actively executing or scheduled.
     * Can transition to: PAUSED, COMPLETED, FAILED, ARCHIVED
     */
    ACTIVE(Set.of("PAUSED", "COMPLETED", "FAILED", "ARCHIVED")),
    
    /**
     * Pipeline execution is temporarily paused.
     * Can transition to: ACTIVE, ARCHIVED
     */
    PAUSED(Set.of("ACTIVE", "ARCHIVED")),
    
    /**
     * Pipeline has completed successfully.
     * Can transition to: ARCHIVED
     */
    COMPLETED(Set.of("ARCHIVED")),
    
    /**
     * Pipeline execution failed.
     * Can transition to: DRAFT (for retry), ARCHIVED
     */
    FAILED(Set.of("DRAFT", "ARCHIVED")),
    
    /**
     * Pipeline is archived and no longer active.
     * Terminal state - no transitions allowed.
     */
    ARCHIVED(Set.of());

    private final Set<String> allowedTransitions;

    PipelineLifecycle(Set<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    /**
     * Checks if a transition to the target state is allowed.
     *
     * @param targetState the target lifecycle state
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(PipelineLifecycle targetState) {
        return allowedTransitions.contains(targetState.name());
    }

    /**
     * Attempts to transition to the target state.
     *
     * @param targetState the target lifecycle state
     * @return the target state if transition is allowed
     * @throws IllegalStateException if transition is not allowed
     */
    public PipelineLifecycle transitionTo(PipelineLifecycle targetState) {
        if (!canTransitionTo(targetState)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s. Allowed transitions: %s",
                    this.name(), targetState.name(), allowedTransitions)
            );
        }
        return targetState;
    }

    /**
     * Checks if this state is terminal (no further transitions allowed).
     *
     * @return true if this is a terminal state
     */
    public boolean isTerminal() {
        return allowedTransitions.isEmpty();
    }

    /**
     * Checks if this state allows execution.
     *
     * @return true if pipelines in this state can execute
     */
    public boolean canExecute() {
        return this == ACTIVE;
    }

    /**
     * Checks if this state allows modification.
     *
     * @return true if pipelines in this state can be modified
     */
    public boolean canModify() {
        return this == DRAFT || this == FAILED;
    }

    /**
     * Checks if this state allows deletion.
     *
     * @return true if pipelines in this state can be deleted
     */
    public boolean canDelete() {
        return this == DRAFT || this == FAILED || this == COMPLETED || this == ARCHIVED;
    }
}
