/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.framework.runtime;

/**
 * Defines the level of human oversight required for agent decisions.
 *
 * <p>An agent's autonomy level determines whether it can act immediately on a high-confidence
 * result, or whether it must surface a {@code HumanApprovalRequest} before proceeding.
 * The threshold is configured per-agent in the YAML definition via
 * {@code confidence_threshold} and {@code autonomy_level}.
 *
 * <h2>Decision flow</h2>
 * <pre>
 * AgentResult.confidence >= threshold
 *   AUTONOMOUS  → execute immediately
 *   SUPERVISED  → raise HumanApprovalRequest, wait for approval
 *   MANUAL      → always raise HumanApprovalRequest, never auto-execute
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Configures human oversight level for agent decisions
 * @doc.layer framework
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle reason
 */
public enum AutonomyLevel {

    /**
     * The agent executes its decision immediately when confidence meets the threshold.
     * Use for well-tested, deterministic, or low-risk agents.
     */
    AUTONOMOUS,

    /**
     * The agent raises a {@code HumanApprovalRequest} when confidence is below the threshold,
     * and executes automatically only when confidence is sufficient.
     * Use for LLM-backed agents performing consequential actions.
     */
    SUPERVISED,

    /**
     * The agent always raises a {@code HumanApprovalRequest} regardless of confidence.
     * Use for high-risk, irreversible, or compliance-sensitive operations.
     */
    MANUAL;

    /**
     * Returns {@code true} when the agent can act without human review at the given
     * confidence level.
     *
     * @param confidence     the result confidence [0.0, 1.0]
     * @param threshold      the minimum confidence threshold for autonomous execution
     * @return {@code true} if the agent may proceed without human approval
     */
    public boolean canActAutonomously(double confidence, double threshold) {
        return switch (this) {
            case AUTONOMOUS -> confidence >= threshold;
            case SUPERVISED -> confidence >= threshold;
            case MANUAL     -> false;
        };
    }
}
