/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.handoff;

/**
 * Reason codes for initiating an agent handoff.
 *
 * @doc.type enum
 * @doc.purpose Handoff reason taxonomy for inter-agent transfer protocol
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum HandoffReason {
    /** The task is outside the current agent's area of competence. */
    OUT_OF_SCOPE,
    /** A specialist agent is better suited for this task. */
    SPECIALIST_REQUIRED,
    /** The agent's resource budget or turn limit is exhausted. */
    BUDGET_EXHAUSTED,
    /** The agent has been shut down or is being hotswapped. */
    AGENT_SHUTDOWN,
    /** The user explicitly requested a different agent. */
    USER_REQUESTED,
    /** The current agent encountered an unrecoverable error. */
    ERROR_RECOVERY,
    /** Policy requires escalation to a supervising agent. */
    SUPERVISION_ESCALATION
}
