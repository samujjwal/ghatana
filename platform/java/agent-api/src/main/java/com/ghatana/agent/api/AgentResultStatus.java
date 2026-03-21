/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

/**
 * Processing outcome status for an {@link AgentResult}.
 *
 * @doc.type enum
 * @doc.purpose Agent result status
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum AgentResultStatus {

    /** Processing completed successfully with output. */
    SUCCESS,

    /** Processing completed but output is below the confidence threshold. */
    LOW_CONFIDENCE,

    /** Agent chose to skip (e.g., input didn't match preconditions). */
    SKIPPED,

    /** Processing failed due to an error. */
    FAILED,

    /** Processing exceeded the agent's latency SLA / timeout. */
    TIMEOUT,

    /** Agent is in a degraded state; result may be partial. */
    DEGRADED,

    /** Agent explicitly delegated processing to another agent. */
    DELEGATED
}
