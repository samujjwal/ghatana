/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

/**
 * Saga compensation strategy for workflow failure handling.
 *
 * @doc.type enum
 * @doc.purpose Saga compensation policy for workflows
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum SagaPolicy {

    /** No compensation — failure means the workflow stays in FAILED state. */
    NONE,

    /** Attempt to complete remaining steps despite partial failure (best-effort). */
    FORWARD_RECOVERY,

    /** Roll back completed steps in reverse order via compensation actions. */
    BACKWARD_COMPENSATION
}
