/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.util.EnumSet;
import java.util.Set;

/**
 * Release lifecycle state machine for agent releases.
 *
 * <p>State transitions are one-directional except for the {@code BLOCKED} state,
 * which is a quarantine state reachable from any live state and recoverable back
 * to the state from which it was blocked.
 *
 * <pre>
 * DRAFT → VALIDATED → SHADOW → CANARY → ACTIVE → DEPRECATED → RETIRED
 *           ↓             ↓        ↓        ↓
 *         BLOCKED       BLOCKED  BLOCKED  BLOCKED
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Release lifecycle state machine for agent releases
 * @doc.layer platform
 * @doc.pattern StateMachine
 */
public enum AgentReleaseState {

    /** Initial mutable draft, not yet submitted for validation. */
    DRAFT {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(VALIDATED, BLOCKED);
        }
    },

    /** Passed static validation (schema, policy scan, enum canonicality). Not yet live. */
    VALIDATED {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(SHADOW, BLOCKED, DRAFT);
        }
    },

    /** Running in shadow mode — processing real traffic but not serving responses. */
    SHADOW {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(CANARY, BLOCKED);
        }
    },

    /** Serving a small fraction of real traffic for canary evaluation. */
    CANARY {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(ACTIVE, DEPRECATED, BLOCKED);
        }
    },

    /** Fully active — primary release serving all traffic for the agent. */
    ACTIVE {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(DEPRECATED, BLOCKED);
        }
    },

    /** Deprecated — still capable of serving but a newer active release exists. */
    DEPRECATED {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(RETIRED, BLOCKED);
        }
    },

    /**
     * Permanently retired — no longer eligible for dispatch.
     * Terminal state: no further transitions allowed.
     */
    RETIRED {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.noneOf(AgentReleaseState.class);
        }
    },

    /**
     * Blocked / quarantined — dispatch to this release is rejected immediately.
     * Reachable from any non-terminal live state; recoverable to the prior state
     * through an explicit unblock operation.
     */
    BLOCKED {
        @Override
        public Set<AgentReleaseState> allowedTransitions() {
            return EnumSet.of(DRAFT, VALIDATED, SHADOW, CANARY, ACTIVE, DEPRECATED);
        }
    };

    /**
     * Returns the set of states reachable from this state via a single transition.
     *
     * @return allowed next states (never {@code null}, may be empty for terminal states)
     */
    public abstract Set<AgentReleaseState> allowedTransitions();

    /**
     * Returns {@code true} if transitioning to {@code target} is allowed.
     *
     * @param target the desired next state
     * @return {@code true} iff {@code target} is in {@link #allowedTransitions()}
     */
    public boolean canTransitionTo(AgentReleaseState target) {
        return allowedTransitions().contains(target);
    }

    /**
     * Returns {@code true} if this state allows the agent to be dispatched.
     *
     * <p>Only {@code ACTIVE} and {@code CANARY} releases are eligible for dispatch.
     * Shadow releases process traffic internally but are not eligible for response serving.
     *
     * @return {@code true} iff dispatch is permitted in this state
     */
    public boolean isDispatchable() {
        return this == ACTIVE || this == CANARY || this == SHADOW;
    }
}
