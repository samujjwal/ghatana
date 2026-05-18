/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

/**
 * Product-development lifecycle action outcomes recorded in the agent trace ledger.
 *
 * @doc.type enum
 * @doc.purpose Normalized trace outcomes for Kernel-governed agent lifecycle actions
 * @doc.layer agent-runtime
 */
public enum AgentLifecycleActionOutcome {
    RECEIVED,
    POLICY_DENIED,
    REQUIRES_APPROVAL,
    ACCEPTED,
    FAILED,
    FALLBACK_RECORDED
}
