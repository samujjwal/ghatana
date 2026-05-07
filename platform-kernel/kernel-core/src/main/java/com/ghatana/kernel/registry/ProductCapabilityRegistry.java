package com.ghatana.kernel.registry;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic product capability registry for UI route gating and feature flags.
 *
 * <p>Provides backend-authoritative capability keys for product UI route gating.
 * Products can register their capability keys (e.g., product.campaigns, product.strategy)
 * and check capabilities by tenant/workspace for authorization.</p>
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * ProductCapabilityRegistry registry = ProductCapabilityRegistry.builder()
 *     .registerCapability("product.campaigns", "Campaign management")
 *     .registerCapability("product.strategy", "Strategy generation")
 *     .registerCapability("product.reporting", "Analytics reporting")
 *     .build();
 *
 * boolean enabled = registry.isCapabilityEnabled("tenant-1", "workspace-1", "product.campaigns");
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Generic product capability registry for UI route gating (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Registry
 */
public interface ProductCapabilityRegistry {

    /**
     * Register a product capability.
     *
     * @param capabilityKey unique capability key (e.g., "dmos.campaigns")
     * @param description human-readable description
     */
    void registerCapability(String capabilityKey, String description);

    /**
     * Check if a capability is enabled for a tenant/workspace.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param capabilityKey capability key to check
     * @return true if capability is enabled
     */
    boolean isCapabilityEnabled(String tenantId, String workspaceId, String capabilityKey);

    /**
     * Enable a capability for a tenant/workspace.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param capabilityKey capability key to enable
     */
    void enableCapability(String tenantId, String workspaceId, String capabilityKey);

    /**
     * Disable a capability for a tenant/workspace.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param capabilityKey capability key to disable
     */
    void disableCapability(String tenantId, String workspaceId, String capabilityKey);

    /**
     * Get all registered capability keys.
     *
     * @return set of registered capability keys
     */
    Set<String> getRegisteredCapabilities();

    /**
     * Get all enabled capabilities for a tenant/workspace.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @return set of enabled capability keys
     */
    Set<String> getEnabledCapabilities(String tenantId, String workspaceId);

    /**
     * Check if a capability key is registered.
     *
     * @param capabilityKey capability key to check
     * @return true if capability is registered
     */
    boolean isCapabilityRegistered(String capabilityKey);

    /**
     * Get capability description.
     *
     * @param capabilityKey capability key
     * @return description or empty if not registered
     */
    String getCapabilityDescription(String capabilityKey);

    /**
     * Builder for {@link ProductCapabilityRegistry}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ProductCapabilityRegistry.
     */
    final class Builder {
        private final Map<String, String> capabilityDescriptions = Map.of();

        /**
         * Register a product capability.
         *
         * @param capabilityKey unique capability key
         * @param description human-readable description
         * @return this builder
         */
        public Builder registerCapability(String capabilityKey, String description) {
            Objects.requireNonNull(capabilityKey, "capabilityKey must not be null");
            Objects.requireNonNull(description, "description must not be null");
            if (capabilityKey.isBlank()) {
                throw new IllegalArgumentException("capabilityKey cannot be blank");
            }
            // Note: In a real implementation, this would add to a mutable map
            // For this stub, we're documenting the API contract
            return this;
        }

        /**
         * Build the ProductCapabilityRegistry.
         *
         * @return new registry instance
         */
        public ProductCapabilityRegistry build() {
            // Return an in-memory implementation for now
            return new InMemoryProductCapabilityRegistry();
        }
    }
}
