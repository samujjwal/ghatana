package com.ghatana.digitalmarketing.domain.event;

/**
 * Lifecycle status for an outbox entry.
 *
 * @doc.type class
 * @doc.purpose DMOS outbox entry status for F2 transactional outbox pattern (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum DmOutboxStatus {

    /** Entry written to the outbox; not yet dispatched. */
    PENDING,

    /** Entry successfully dispatched to the event bus. */
    DISPATCHED,

    /** Dispatch failed; eligible for retry based on attempt count. */
    FAILED,

    /** Retry limit exceeded; entry moved to dead-letter queue. */
    DEAD
}
