package com.ghatana.statestore.hybrid;

/**
 * Synchronization strategy for HybridStateStore.
 * 
 * Determines when local state is synced to the centralized backing store.
 * 
 * Per WORLD_CLASS_DESIGN_MASTER.md Section 5.5 (Hybrid State Management):
 * - IMMEDIATE: Every write synced (safest, slowest)
 * - BATCHED: Batch syncs every N operations (balanced, recommended)
 * - PERIODIC: Sync every T milliseconds (fastest, eventual)
 * - ON_CHECKPOINT: Only during checkpoint (maximum performance)
 */
public enum SyncStrategy {
    
    /**
     * Synchronous sync on every write operation.
     * Safest but slowest - guarantees immediate consistency.
     * Use for critical data that must never be lost.
     */
    IMMEDIATE,
    
    /**
     * Batch multiple writes and sync periodically.
     * Balanced approach - good performance with reasonable consistency.
     * Recommended for most use cases.
     */
    BATCHED,
    
    /**
     * Sync on a fixed time interval.
     * Fastest writes - eventual consistency (typically <100ms).
     * Use for high-throughput scenarios where slight lag is acceptable.
     */
    PERIODIC,
    
    /**
     * Only sync during explicit checkpoints.
     * Maximum performance - manual control over consistency.
     * Use for temporary/ephemeral state or when checkpoints are frequent.
     */
    ON_CHECKPOINT
}
