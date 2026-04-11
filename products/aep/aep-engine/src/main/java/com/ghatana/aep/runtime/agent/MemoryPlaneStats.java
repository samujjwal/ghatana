package com.ghatana.agent.memory.store;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Statistics for the memory plane across all tiers.
 *
 * @doc.type value-object
 * @doc.purpose Memory plane statistics
 * @doc.layer agent-memory
 */
@Value
@Builder
public class MemoryPlaneStats {

    long episodeCount;
    long factCount;
    long procedureCount;
    long preferenceCount;
    long taskStateCount;
    long workingCount;
    long artifactCount;
    long totalStorageBytes;
    @Builder.Default Map<String, Long> storageBytesPerTier = Map.of();
    @Nullable Instant lastConsolidation;
    @Builder.Default String indexHealth = "HEALTHY";
}
