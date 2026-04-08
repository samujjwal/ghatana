/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.composition;

/**
 * Voting policy applied in {@link CompositionPattern#VOTING} compositions.
 *
 * @doc.type enum
 * @doc.purpose Voting policy for multi-agent result aggregation
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum VotingPolicy {
    /** Simple majority (> n/2 agents must agree). */
    MAJORITY,
    /** All agents must produce the same result. */
    UNANIMOUS,
    /** At least one agent must succeed. */
    ANY_ONE,
    /** Weighted majority; each agent contributes a configured weight. */
    WEIGHTED_MAJORITY
}
