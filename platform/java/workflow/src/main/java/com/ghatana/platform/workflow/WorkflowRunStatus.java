/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

/**
 * Unified FSM state for any workflow run instance.
 *
 * <h3>State transitions</h3>
 * <pre>{@code
 * PENDING → RUNNING → COMPLETED
 *                   → FAILED
 *                   → CANCELLED
 *         RUNNING ↔ WAITING (on signal/timer)
 *         RUNNING → SUSPENDED (manual pause)
 *         FAILED  → COMPENSATING → COMPENSATED
 * }</pre>
 *
 * @doc.type enum
 * @doc.purpose Workflow run lifecycle states
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum WorkflowRunStatus {

    /** Created but not yet started. */
    PENDING,

    /** Actively executing steps. */
    RUNNING,

    /** Paused, waiting for an external signal, timer, or manual approval. */
    WAITING,

    /** Manually paused by an operator; can be resumed. */
    SUSPENDED,

    /** All steps completed successfully. */
    COMPLETED,

    /** One or more steps failed and no compensation is configured. */
    FAILED,

    /** Cancelled by an operator or timeout before completion. */
    CANCELLED,

    /** Saga compensation is in progress (rolling back completed steps). */
    COMPENSATING,

    /** Saga compensation completed successfully. */
    COMPENSATED,

    /** Paused at a Human-in-the-Loop checkpoint awaiting an explicit human decision. */
    WAITING_FOR_HITL
}
