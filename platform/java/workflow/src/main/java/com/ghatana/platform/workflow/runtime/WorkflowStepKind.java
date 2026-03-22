/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

/**
 * Enumerates the kinds of steps that can appear in a workflow definition.
 *
 * @doc.type enum
 * @doc.purpose Classifies workflow step behaviors (action, decision, wait, etc.)
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum WorkflowStepKind {

    /** Standard executable step — runs a service call or operator. */
    ACTION,

    /** Conditional branching step — evaluates a CEL expression to decide the next step. */
    DECISION,

    /** Parallel fan-out — executes child steps concurrently and joins on a barrier. */
    PARALLEL,

    /** Wait / timer step — suspends execution until a signal or timeout. */
    WAIT,

    /** Sub-workflow invocation — delegates to another workflow definition. */
    SUB_WORKFLOW,

    /** Compensation step — executes rollback logic for a previously completed step. */
    COMPENSATION,

    /** Loop step — repeats child steps while a condition holds. */
    LOOP,

    /** Terminal step — marks the end of a branch or the workflow itself. */
    END
}
