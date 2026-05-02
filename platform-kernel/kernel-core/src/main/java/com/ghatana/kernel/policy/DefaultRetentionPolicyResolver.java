package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Map;
import java.util.Objects;

/**
 * Default retention policy resolver using classification metadata.
 *
 * <p>Resolves retention from the classification's domain and compliance tags rather
 * than from product id strings. This preserves the existing retention behavior
 * (regulatory → 10 years, healthcare → 25 years, default → 7 years) while removing
 * the product-aware branching from kernel code.</p>
 *
 * <p>Domain packs or deployment configurations register their classification
 * descriptors with appropriate compliance tags, and this resolver maps those
 * tags to retention periods.</p>
 *
 * @doc.type class
 * @doc.purpose Default retention resolver using compliance tags from classification metadata
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class DefaultRetentionPolicyResolver implements RetentionPolicyResolver {

    /** Default retention when no specific policy applies. */
    private static final int DEFAULT_RETENTION_YEARS = 7;

    private final Map<String, Integer> complianceTagRetention;

    /**
     * Creates a resolver with the given compliance-tag-to-retention mappings.
     *
     * @param complianceTagRetention map from compliance tag to retention years
     */
    public DefaultRetentionPolicyResolver(Map<String, Integer> complianceTagRetention) {
        this.complianceTagRetention = Objects.requireNonNull(complianceTagRetention);
    }

    /**
     * Creates a resolver with an empty compliance-tag-to-retention map.
     *
     * <p>The default retention of 7 years applies when no compliance tags match.
     * Products supply their own compliance tags and register retention durations
     * via {@link #DefaultRetentionPolicyResolver(java.util.Map)} in their pack configuration.
     * Regulatory-framework tags belong in product packs, not in the platform kernel.</p>
     */
    public static DefaultRetentionPolicyResolver withStandardMappings() {
        return new DefaultRetentionPolicyResolver(Map.of());
    }

    @Override
    public int resolveRetentionYears(ScopeDescriptor sourceScope, ScopeDescriptor targetScope,
                                     ClassificationDescriptor classification) {
        // Resolve from compliance tags: pick the longest retention requirement
        int maxRetention = DEFAULT_RETENTION_YEARS;
        for (String tag : classification.getComplianceTags()) {
            Integer tagRetention = complianceTagRetention.get(tag);
            if (tagRetention != null && tagRetention > maxRetention) {
                maxRetention = tagRetention;
            }
        }
        return maxRetention;
    }
}
