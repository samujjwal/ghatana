package com.ghatana.agent.learning.retention;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Result of a retention pass.
 *
 * @doc.type class
 * @doc.purpose Retention operation result
 * @doc.layer agent-learning
 */
@Value
@Builder
public class RetentionResult {

    @NotNull String agentId;

    int kept;
    int decayed;
    int evicted;

    @Builder.Default
    @NotNull Instant completedAt = Instant.now();

    public int total() {
        return kept + decayed + evicted;
    }
}
