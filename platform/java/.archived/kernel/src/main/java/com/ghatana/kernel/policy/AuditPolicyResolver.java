package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Map;

/**
 * Resolves audit policies (including retention, signature requirements, and storage tier)
 * from scope and classification metadata.
 *
 * <p>This is the single entry point for audit policy resolution, composing retention
 * and other audit-specific policy dimensions. Implementations must be stateless and
 * safe for concurrent use.</p>
 *
 * @doc.type interface
 * @doc.purpose Policy-driven audit configuration resolution
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface AuditPolicyResolver {

    /**
     * Resolves the full audit policy for an event.
     *
     * @param sourceScope    the originating scope
     * @param targetScope    the target scope
     * @param classification data classification for the event
     * @return the resolved audit policy
     */
    AuditPolicy resolve(ScopeDescriptor sourceScope, ScopeDescriptor targetScope,
                        ClassificationDescriptor classification);

    /**
     * Audit policy resolved for a specific event.
     */
    record AuditPolicy(
            int retentionYears,
            boolean signatureRequired,
            boolean immutable,
            String storageTier,
            Map<String, String> policyMetadata
    ) {
        /**
         * Default audit policy: 7-year retention, signed, immutable, standard tier.
         */
        public static AuditPolicy defaults() {
            return new AuditPolicy(7, true, true, "standard", Map.of());
        }
    }
}
