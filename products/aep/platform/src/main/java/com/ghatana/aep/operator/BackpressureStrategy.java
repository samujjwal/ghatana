/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.aep.operator;

/**
 * Enumerates backpressure strategies for AEP agent operators.
 *
 * <p>When an agent cannot keep up with the incoming event rate, the
 * backpressure strategy determines how overflow is handled.
 *
 * @doc.type enum
 * @doc.purpose Backpressure strategy classification for AEP operators
 * @doc.layer product-aep
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public enum BackpressureStrategy {

    /** Drop the newest event when the buffer is full. */
    DROP_LATEST,

    /** Drop the oldest event in the buffer when full. */
    DROP_OLDEST,

    /** Block the producer until buffer space is available. */
    BLOCK,

    /** Buffer unboundedly (use with caution — OOM risk). */
    UNBOUNDED,

    /** Route overflow events to a dead-letter / overflow queue. */
    OVERFLOW_TO_DLQ
}
