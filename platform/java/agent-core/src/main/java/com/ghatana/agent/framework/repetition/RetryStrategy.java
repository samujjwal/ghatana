/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.repetition;

/**
 * Strategies controlling how an agent retries failed turns.
 *
 * @doc.type enum
 * @doc.purpose Retry behaviour taxonomy for agent repetition policy
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum RetryStrategy {
    /** Retry immediately without any delay. */
    IMMEDIATE,
    /** Retry with a fixed delay between attempts. */
    FIXED_DELAY,
    /** Retry with exponentially increasing delay. */
    EXPONENTIAL_BACKOFF,
    /** No retries; fail immediately on first error. */
    NONE
}
