package com.ghatana.core.state;

/**
 * Strategy for synchronizing state between local and central stores.
 *
 * @doc.type enum
 * @doc.purpose Strategies for synchronizing local and central state
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum SyncStrategy {
    /** Synchronize immediately on each write. */
    IMMEDIATE,
    /** Batch synchronization at intervals. */
    BATCHED,
    /** No synchronization - local only. */
    NONE
}
