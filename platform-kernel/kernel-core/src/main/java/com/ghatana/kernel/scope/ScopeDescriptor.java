package com.ghatana.kernel.scope;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Describes a scope boundary in the kernel runtime.
 *
 * <p>ScopeDescriptor replaces the product-id-based framing used in earlier kernel services
 * with a generic, policy-friendly scope model. The same descriptor can represent a product, a tenant,
 * a domain pack, a workflow, or any other architectural scope.</p>
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md §4.1:</p>
 * <ul>
 *   <li>{@code scopeType} — one of the canonical {@link ScopeType} values</li>
 *   <li>{@code scopeId} — a stable identifier unique within its scope type</li>
 *   <li>{@code metadata} — optional classification/policy metadata for the scope</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Generic scope descriptor replacing product-first kernel framing
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class ScopeDescriptor {

    private final ScopeType scopeType;
    private final String scopeId;
    private final Map<String, String> metadata;

    /**
     * Creates a scope descriptor.
     *
     * @param scopeType the type of scope
     * @param scopeId   a stable identifier for the scope (e.g., "my-module", "tenant-123")
     * @param metadata  optional key-value metadata for policy resolution
     */
    public ScopeDescriptor(ScopeType scopeType, String scopeId, Map<String, String> metadata) {
        this.scopeType = Objects.requireNonNull(scopeType, "scopeType cannot be null");
        this.scopeId = Objects.requireNonNull(scopeId, "scopeId cannot be null");
        if (scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId cannot be blank");
        }
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Convenience factory for product scopes.
     */
    public static ScopeDescriptor product(String productId) {
        return new ScopeDescriptor(ScopeType.PRODUCT, productId, Map.of());
    }

    /**
     * Convenience factory for tenant scopes.
     */
    public static ScopeDescriptor tenant(String tenantId) {
        return new ScopeDescriptor(ScopeType.TENANT, tenantId, Map.of());
    }

    /**
     * Convenience factory for domain-pack scopes.
     */
    public static ScopeDescriptor domainPack(String packId) {
        return new ScopeDescriptor(ScopeType.DOMAIN_PACK, packId, Map.of());
    }

    /**
     * Convenience factory for workflow scopes.
     */
    public static ScopeDescriptor workflow(String workflowId) {
        return new ScopeDescriptor(ScopeType.WORKFLOW, workflowId, Map.of());
    }

    public ScopeType getScopeType() { return scopeType; }

    public String getScopeId() { return scopeId; }

    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Gets a metadata value, or the default if absent.
     */
    public String getMetadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScopeDescriptor that = (ScopeDescriptor) o;
        return scopeType == that.scopeType && scopeId.equals(that.scopeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeType, scopeId);
    }

    @Override
    public String toString() {
        return scopeType + ":" + scopeId;
    }
}
