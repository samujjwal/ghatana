/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

/**
 * Classifies a workflow by its execution and durability characteristics.
 *
 * <ul>
 *   <li>{@link #EPHEMERAL} — In-memory, short-lived execution (operator pipelines, DAG runs)</li>
 *   <li>{@link #DURABLE} — Persisted, long-lived execution (FSM, sagas, multi-step processes)</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Workflow durability classification
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum WorkflowKind {

    /** In-memory execution that completes within a single process lifetime. */
    EPHEMERAL,

    /** Persisted execution that survives process restarts and may run for days or weeks. */
    DURABLE
}
