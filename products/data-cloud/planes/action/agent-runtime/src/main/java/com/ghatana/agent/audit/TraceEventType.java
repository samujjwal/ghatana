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

    /** Memory retrieval started. */
    MEMORY_RETRIEVAL_STARTED,

    /** Memory retrieval completed. */
    MEMORY_RETRIEVAL_COMPLETED,

    /** Memory item rejected during retrieval (e.g., obsolete, maintenance mismatch). */
    MEMORY_ITEM_REJECTED,

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
    BUDGET_ALERT,

    // ── Mastery and Learning Events ───────────────────────────────────────

    /** Mastery state transition occurred for a skill. */
    MASTERY_STATE_CHANGED,

    /** Mastery transition proposed for review. */
    MASTERY_TRANSITION_PROPOSED,

    /** Mastery transition applied to mastery item. */
    MASTERY_TRANSITION_APPLIED,

    /** Learning delta proposed for review and promotion. */
    LEARNING_DELTA_PROPOSED,

    /** Learning delta evaluated by promotion engine. */
    LEARNING_DELTA_EVALUATED,

    /** Learning delta promoted to active knowledge. */
    LEARNING_DELTA_PROMOTED,

    /** Learning delta rejected by governance or evaluation. */
    LEARNING_DELTA_REJECTED,

    /** Skill benchmark evaluation completed. */
    SKILL_BENCHMARK_COMPLETED,

    /** Evaluation pack execution started. */
    EVALUATION_PACK_STARTED,

    /** Evaluation pack execution completed. */
    EVALUATION_PACK_COMPLETED,

    /** Obsolescence detected for a mastery item. */
    OBSOLESCENCE_DETECTED,

    /** Obsolescence event routed to transition. */
    OBSOLESCENCE_ROUTED,

    // ── Governance Dispatch Events ────────────────────────────────────────

    /** Version context was resolved for this dispatch. */
    VERSION_CONTEXT_RESOLVED,

    /** Mastery decision was made for the skill/agent. */
    MASTERY_DECISION_MADE,

    /** Execution mode was selected for this dispatch. */
    MODE_SELECTED,

    /** Approval gate was checked. */
    APPROVAL_CHECKED,

    /** Verification gate was checked. */
    VERIFICATION_CHECKED,

    /** Dispatch was explicitly allowed after all governance checks passed. */
    DISPATCH_ALLOWED
}
