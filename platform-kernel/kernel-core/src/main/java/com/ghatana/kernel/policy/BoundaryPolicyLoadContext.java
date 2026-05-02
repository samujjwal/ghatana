package com.ghatana.kernel.policy;

import java.util.Map;
import java.util.Objects;

/**
 * Context used when loading boundary policy rules from a {@link BoundaryPolicyStore}.
 *
 * <p>The load context carries runtime information that allows policy stores to apply
 * tenant- and region-specific overrides to their base rule set without the resolver
 * needing to know the storage mechanism.</p>
 *
 * @doc.type class
 * @doc.purpose Context for parameterized rule loading from a BoundaryPolicyStore
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class BoundaryPolicyLoadContext {

    private final String tenantId;
    private final String region;
    private final Map<String, String> attributes;

    private BoundaryPolicyLoadContext(Builder builder) {
        this.tenantId = builder.tenantId != null ? builder.tenantId : "default";
        this.region = builder.region != null ? builder.region : "GLOBAL";
        this.attributes = builder.attributes != null ? Map.copyOf(builder.attributes) : Map.of();
    }

    /** The tenant for which rules are being loaded. */
    public String getTenantId() { return tenantId; }

    /** The deployment region (e.g. "US", "EU", "GLOBAL"). */
    public String getRegion() { return region; }

    /** Additional contextual attributes for store-specific filtering. */
    public Map<String, String> getAttributes() { return attributes; }

    /** Creates a global context with no tenant or region override. */
    public static BoundaryPolicyLoadContext global() {
        return new Builder().build();
    }

    /** Creates a context for a specific tenant and region. */
    public static BoundaryPolicyLoadContext of(String tenantId, String region) {
        return new Builder().tenantId(tenantId).region(region).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundaryPolicyLoadContext that = (BoundaryPolicyLoadContext) o;
        return Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(region, that.region) &&
               Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, region, attributes); }

    @Override
    public String toString() {
        return "BoundaryPolicyLoadContext{tenantId='" + tenantId + "', region='" + region + "'}";
    }

    public static Builder builder() { return new Builder(); }

    /** Fluent builder for {@link BoundaryPolicyLoadContext}. */
    public static final class Builder {
        private String tenantId;
        private String region;
        private Map<String, String> attributes;

        private Builder() {}

        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder attributes(Map<String, String> attributes) { this.attributes = attributes; return this; }

        public BoundaryPolicyLoadContext build() { return new BoundaryPolicyLoadContext(this); }
    }
}
