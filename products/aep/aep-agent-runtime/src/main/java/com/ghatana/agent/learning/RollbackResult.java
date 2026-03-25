package com.ghatana.agent.learning;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Result of rolling back a promoted skill.
 *
 * @doc.type class
 * @doc.purpose Skill rollback result
 * @doc.layer agent-learning
 */
@Value
@Builder
public class RollbackResult {

    /** Skill that was rolled back. */
    @NotNull String skillId;

    /** Version that was active before rollback. */
    @NotNull String rolledBackVersion;

    /** Version that is now active after rollback. */
    @NotNull String restoredVersion;

    /** Whether the rollback succeeded. */
    boolean success;

    /** Reason for rolling back. */
    @NotNull String reason;

    /** Optional explanation of what went wrong. */
    @Nullable String explanation;

    /** When the rollback occurred. */
    @Builder.Default
    @NotNull Instant rolledBackAt = Instant.now();
}
