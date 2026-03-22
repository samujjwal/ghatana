package com.ghatana.core.state;

/**
 * Strategy for synchronizing local state to central store.
 *
 * <p><b>Purpose</b><br>
 * Defines when and how state changes are synchronized from local
 * storage to centralized storage in hybrid state stores.
 *
 * <p><b>Trade-offs</b><br>
 * <ul>
 *   <li><b>IMMEDIATE:</b> Safest, slowest (every write waits for central)</li>
 *   <li><b>BATCHED:</b> Balanced (batch sync every N seconds)</li>
 *   <li><b>PERIODIC:</b> Fastest, eventual consistency (full sync periodically)</li>
 *   <li><b>ON_CHECKPOINT:</b> Maximum performance (sync only on checkpoint)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * HybridStateStore<String, Object> store = HybridStateStore.builder()
 *     .syncStrategy(SyncStrategy.BATCHED)
 *     .syncInterval(Duration.ofSeconds(5))
 *     .build();
 * }</pre>
 *
 * @see HybridStateStore
 * @doc.type enum
 * @doc.purpose State sync strategy definition
 * @doc.layer core
 * @doc.pattern Strategy
 */
public enum SyncStrategy {

    /**
     * Synchronize every write immediately.
     *
     * <p><b>Characteristics:</b>
     * - Write latency: High (~100ms per write)
     * - Consistency: Strong (immediate sync)
     * - Data loss risk: Minimal
     * - Performance: Lowest
     *
     * <p><b>Use when:</b>
     * - Critical data that must not be lost
     * - Strong consistency required
     * - Write volume is low
     */
    IMMEDIATE,

    /**
     * Batch dirty keys and sync periodically.
     *
     * <p><b>Characteristics:</b>
     * - Write latency: Low (~1ms to local)
     * - Consistency: Eventually consistent (sync lag)
     * - Data loss risk: Low (recent writes only)
     * - Performance: Good
     *
     * <p><b>Use when:</b>
     * - Balanced performance and consistency needed
     * - Moderate write volume
     * - Can tolerate brief inconsistency
     *
     * <p><b>Recommended default</b>
     */
    BATCHED,

    /**
     * Full state sync on time interval.
     *
     * <p><b>Characteristics:</b>
     * - Write latency: Lowest (~1ms to local)
     * - Consistency: Eventually consistent (longer lag)
     * - Data loss risk: Medium (all writes since last sync)
     * - Performance: Best
     *
     * <p><b>Use when:</b>
     * - Maximum performance needed
     * - High write volume
     * - Can tolerate longer inconsistency
     */
    PERIODIC,

    /**
     * Sync only on explicit checkpoint.
     *
     * <p><b>Characteristics:</b>
     * - Write latency: Lowest (~1ms to local)
     * - Consistency: Checkpoint-based
     * - Data loss risk: High (all writes since checkpoint)
     * - Performance: Maximum
     *
     * <p><b>Use when:</b>
     * - Checkpoint-based recovery model
     * - Maximum performance critical
     * - Application controls checkpointing
     */
    ON_CHECKPOINT;

    /**
     * Check if strategy requires immediate sync.
     *
     * @return true if writes sync immediately
     */
    public boolean isImmediate() {
        return this == IMMEDIATE;
    }

    /**
     * Check if strategy uses batching.
     *
     * @return true if writes are batched
     */
    public boolean isBatched() {
        return this == BATCHED;
    }

    /**
     * Check if strategy is periodic.
     *
     * @return true if sync is periodic
     */
    public boolean isPeriodic() {
        return this == PERIODIC;
    }

    /**
     * Check if strategy is checkpoint-based.
     *
     * @return true if sync on checkpoint
     */
    public boolean isCheckpointBased() {
        return this == ON_CHECKPOINT;
    }
}

