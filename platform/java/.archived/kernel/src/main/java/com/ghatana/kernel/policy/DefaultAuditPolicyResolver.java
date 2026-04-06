package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Map;

/**
 * Default audit policy resolver composing retention and audit configuration.
 *
 * <p>Delegates retention to a {@link RetentionPolicyResolver} and determines
 * signature/storage requirements from classification sensitivity level.</p>
 *
 * @doc.type class
 * @doc.purpose Default audit policy resolver combining retention and audit config
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class DefaultAuditPolicyResolver implements AuditPolicyResolver {

    private final RetentionPolicyResolver retentionResolver;

    public DefaultAuditPolicyResolver(RetentionPolicyResolver retentionResolver) {
        this.retentionResolver = retentionResolver;
    }

    /**
     * Creates a resolver with standard regulatory mappings.
     */
    public static DefaultAuditPolicyResolver withStandardMappings() {
        return new DefaultAuditPolicyResolver(DefaultRetentionPolicyResolver.withStandardMappings());
    }

    @Override
    public AuditPolicy resolve(ScopeDescriptor sourceScope, ScopeDescriptor targetScope,
                               ClassificationDescriptor classification) {
        int retentionYears = retentionResolver.resolveRetentionYears(
                sourceScope, targetScope, classification);

        boolean signatureRequired = classification.getSensitivityLevel()
                .compareTo(ClassificationDescriptor.SensitivityLevel.CONFIDENTIAL) >= 0;

        String storageTier = retentionYears >= 20 ? "archive" :
                             retentionYears >= 10 ? "compliance" : "standard";

        return new AuditPolicy(
                retentionYears,
                signatureRequired,
                true, // all audit records are immutable
                storageTier,
                Map.of(
                        "source_scope", sourceScope.toString(),
                        "target_scope", targetScope.toString(),
                        "domain", classification.getDomain()
                )
        );
    }
}
