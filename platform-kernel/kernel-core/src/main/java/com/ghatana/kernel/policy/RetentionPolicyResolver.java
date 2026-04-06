package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

/**
 * Resolves retention policies for audit events from scope and classification metadata.
 *
 * <p>Replaces the hardcoded retention logic that previously branched on product ids
 * (e.g., regulatory domain → 10 years, healthcare domain → 25 years). Implementations resolve retention
 * from policy metadata carried by {@link ScopeDescriptor} and {@link ClassificationDescriptor}
 * rather than from string-matching product names.</p>
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md §5.1: retention is resolved from policy
 * metadata, not from product string checks.</p>
 *
 * @doc.type interface
 * @doc.purpose Policy-driven retention resolution for audit events
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface RetentionPolicyResolver {

    /**
     * Resolves a retention period for an audit event based on scope and classification.
     *
     * @param sourceScope    the originating scope
     * @param targetScope    the target scope (may equal source for single-scope events)
     * @param classification data classification for the event
     * @return retention period in years
     */
    int resolveRetentionYears(ScopeDescriptor sourceScope, ScopeDescriptor targetScope,
                              ClassificationDescriptor classification);
}
