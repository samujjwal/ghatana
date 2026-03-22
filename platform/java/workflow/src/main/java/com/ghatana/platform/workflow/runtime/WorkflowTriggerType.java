/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

/**
 * Describes how a workflow can be started.
 *
 * @doc.type enum
 * @doc.purpose Classifies workflow trigger sources
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum WorkflowTriggerType {

    /** Triggered by an API call. */
    API,

    /** Triggered by a domain event matched against a filter expression. */
    EVENT,

    /** Triggered on a schedule (cron). */
    SCHEDULE,

    /** Sub-workflow invoked by a parent workflow. */
    SUB_WORKFLOW,

    /** Triggered manually by an operator or admin. */
    MANUAL
}
