package com.ghatana.kernel.policy;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Describes the classification of data or actions for policy resolution.
 *
 * <p>ClassificationDescriptor carries domain, sensitivity, and compliance metadata so
 * that kernel services can resolve retention, access, audit, and boundary policies
 * without hardcoding product-specific rules.</p>
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md §5.1, this replaces the hardcoded
 * product-id branching in services with policy-driven resolution.</p>
 *
 * <p>ClassificationDescriptor is a value object that encapsulates the domain, sensitivity level, 
 * compliance tags, and metadata of classified data or actions.</p>
 *
 * @doc.type class
 * @doc.purpose Classification metadata for policy-driven kernel services
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ClassificationDescriptor {

    private final String domain;
    private final SensitivityLevel sensitivityLevel;
    private final Set<String> complianceTags;
    private final Map<String, String> metadata;

    /**
     * Creates a classification descriptor.
     *
     * @param domain           the business domain (e.g., "healthcare", "regulatory", "general")
     * @param sensitivityLevel the data sensitivity level
     * @param complianceTags   compliance/regulatory tags (e.g., "nepal-2081", "sebon", "gdpr")
     * @param metadata         additional policy metadata
     */
    public ClassificationDescriptor(String domain, SensitivityLevel sensitivityLevel,
                                    Set<String> complianceTags, Map<String, String> metadata) {
        this.domain = Objects.requireNonNull(domain, "domain cannot be null");
        this.sensitivityLevel = Objects.requireNonNull(sensitivityLevel, "sensitivityLevel cannot be null");
        this.complianceTags = complianceTags != null ? Set.copyOf(complianceTags) : Set.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a classification for a known domain with regulatory tags.
     */
    public static ClassificationDescriptor of(String domain, SensitivityLevel sensitivity,
                                              String... complianceTags) {
        return new ClassificationDescriptor(domain, sensitivity, Set.of(complianceTags), Map.of());
    }

    public String getDomain() { return domain; }

    public SensitivityLevel getSensitivityLevel() { return sensitivityLevel; }

    public Set<String> getComplianceTags() { return complianceTags; }

    public Map<String, String> getMetadata() { return metadata; }

    public boolean hasComplianceTag(String tag) {
        return complianceTags.contains(tag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassificationDescriptor that = (ClassificationDescriptor) o;
        return domain.equals(that.domain) && sensitivityLevel == that.sensitivityLevel
                && complianceTags.equals(that.complianceTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, sensitivityLevel, complianceTags);
    }

    @Override
    public String toString() {
        return String.format("Classification{domain='%s', sensitivity=%s, compliance=%s}",
                domain, sensitivityLevel, complianceTags);
    }

    /**
     * Sensitivity levels for classified data.
     */
    public enum SensitivityLevel {
        /** Publicly available data. */
        PUBLIC,
        /** Internal-use data, not publicly shared. */
        INTERNAL,
        /** Confidential data requiring access controls. */
        CONFIDENTIAL,
        /** Highly restricted data under regulatory obligations. */
        RESTRICTED
    }
}
