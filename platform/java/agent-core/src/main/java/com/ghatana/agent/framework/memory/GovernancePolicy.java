package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Memory governance policy.
 *
 * @doc.type interface
 * @doc.purpose Memory governance policy
 * @doc.layer framework
 * @doc.pattern Strategy
 */
public interface GovernancePolicy {

    /**
     * Gets retention period for different memory types.
     * @return Retention period
     */
    @NotNull
    Duration getRetentionPeriod();

    /**
     * Gets fields that should be redacted.
     * @return List of field patterns to redact
     */
    @NotNull
    List<String> getRedactionPatterns();

    /**
     * Checks if a memory record should be deleted based on this policy.
     *
     * @param record Memory record to check
     * @return true if should be deleted
     */
    boolean shouldDelete(@NotNull Object record);

    /**
     * Checks if a memory record should be redacted.
     *
     * @param record Memory record to check
     * @return true if should be redacted
     */
    boolean shouldRedact(@NotNull Object record);

    /**
     * Returns a no-op policy that retains everything, redacts nothing, and
     * never deletes any record. Suitable for development and testing.
     *
     * @return no-op policy singleton
     * @since 2.4.0
     */
    @NotNull
    static GovernancePolicy noOp() {
        return new GovernancePolicy() {
            @Override
            public @NotNull Duration getRetentionPeriod() { return Duration.ofDays(365); }
            @Override
            public @NotNull List<String> getRedactionPatterns() { return List.of(); }
            @Override
            public boolean shouldDelete(@NotNull Object record) { return false; }
            @Override
            public boolean shouldRedact(@NotNull Object record) { return false; }
        };
    }
}
