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
}
