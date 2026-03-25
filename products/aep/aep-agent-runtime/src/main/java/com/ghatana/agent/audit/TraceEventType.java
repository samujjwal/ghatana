/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

/**
 * Classifies the type of event recorded in the agent trace ledger.
 *
 * <p>Each entry in the ledger is typed to enable efficient querying, filtering,
 * and separate retention policies per category.
 *
 * @doc.type enum
 * @doc.purpose Trace event type classification for evidence plane
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public enum TraceEventType {

    /** Agent action executed (tool call, API call, file write, etc.). */
    ACTION_EXECUTED,

    /** Action was blocked by policy or governance. */
    ACTION_DENIED,

    /** Approval requested for a governed action. */
    APPROVAL_REQUESTED,

    /** Approval granted by human or automated reviewer. */
    APPROVAL_GRANTED,

    /** Approval rejected. */
    APPROVAL_REJECTED,

    /** Policy evaluation completed (records the decision). */
    POLICY_EVALUATED,

    /** Memory item created or mutated. */
    MEMORY_MUTATION,

    /** Delegation from one agent to another. */
    DELEGATION,

    /** Invariant check passed. */
    INVARIANT_PASSED,

    /** Invariant violation detected. */
    INVARIANT_VIOLATED,

    /** Agent turn started. */
    TURN_STARTED,

    /** Agent turn completed. */
    TURN_COMPLETED,

    /** Kill switch activated. */
    KILL_SWITCH_ACTIVATED,

    /** Cost or budget threshold crossed. */
    BUDGET_ALERT
}
