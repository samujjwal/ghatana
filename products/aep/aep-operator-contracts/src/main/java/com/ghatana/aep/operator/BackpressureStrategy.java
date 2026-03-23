package com.ghatana.aep.operator;

/**
 * Backpressure strategy for AEP operator pipelines.
 *
 * @doc.type enum
 * @doc.purpose Backpressure mode selection for operator buffers
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum BackpressureStrategy {
    /** Block the producer until the consumer catches up. */
    BLOCK,
    /** Drop the oldest buffered items when the buffer is full. */
    DROP_OLDEST,
    /** Drop the newest (incoming) items when the buffer is full. */
    DROP_LATEST,
    /** Overflow excess items to a dead-letter queue. */
    OVERFLOW_TO_DLQ
}
