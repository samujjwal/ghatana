package com.ghatana.agent.memory.model.working;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for working memory bounds and eviction policy.
 *
 * @doc.type value-object
 * @doc.purpose Working memory configuration
 * @doc.layer agent-memory
 */
@Value
@Builder
public class WorkingMemoryConfig {

    /** Maximum number of entries. Default: 1000. */
    @Builder.Default
    int maxEntries = 1000;

    /** Maximum total size in bytes. Default: 10 MB. */
    @Builder.Default
    long maxBytes = 10L * 1024 * 1024;

    /** Eviction policy when capacity is exceeded. */
    @NotNull
    @Builder.Default
    EvictionPolicy evictionPolicy = EvictionPolicy.LRU;

    public enum EvictionPolicy {
        /** Least Recently Used. */
        LRU,
        /** Least Frequently Used. */
        LFU,
        /** Priority-based (lowest priority evicted first). */
        PRIORITY
    }
}
