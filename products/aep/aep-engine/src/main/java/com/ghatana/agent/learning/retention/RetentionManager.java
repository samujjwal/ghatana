package com.ghatana.agent.learning.retention;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Manages memory retention — decides which items to keep, decay, or evict.
 *
 * @doc.type interface
 * @doc.purpose Retention management SPI
 * @doc.layer agent-learning
 */
public interface RetentionManager {

    /**
     * Applies retention policy to all memory items.
     *
     * @param agentId Agent whose memory to manage
     * @param config Retention configuration
     * @return Result with counts of kept, decayed, and evicted items
     */
    @NotNull Promise<RetentionResult> applyRetention(@NotNull String agentId, @NotNull RetentionConfig config);
}
