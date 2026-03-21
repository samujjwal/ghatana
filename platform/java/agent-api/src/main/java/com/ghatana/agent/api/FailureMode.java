/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

/**
 * How an agent behaves when processing fails.
 *
 * @doc.type enum
 * @doc.purpose Failure mode classification
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum FailureMode {

    /** Failure propagates immediately. No retry, no fallback. */
    FAIL_FAST,

    /** Retry with configurable backoff before giving up. */
    RETRY,

    /** Fall back to a pre-configured default output. */
    FALLBACK,

    /** Skip this agent's contribution and continue. */
    SKIP,

    /** Route the event to a dead-letter-queue for later inspection. */
    DEAD_LETTER,

    /** After N consecutive failures, open the circuit. */
    CIRCUIT_BREAKER
}
