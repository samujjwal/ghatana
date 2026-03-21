package com.ghatana.kernel.registry;

import com.ghatana.kernel.annotation.KernelInternal;
import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.List;
import java.util.Optional;

/**
 * Registry for discovering and managing kernel capabilities.
 *
 * <p><b>Internal helper registry.</b> Per KERNEL_CANONICALIZATION_DECISIONS.md (Decision D4),
 * {@link KernelRegistry} is the only public root registry contract. This registry is an
 * internal implementation facet behind KernelRegistry. External consumers MUST use
 * KernelRegistry for capability discovery.</p>
 *
 * <p>Migrated from transitional {@code capability.KernelCapability} to canonical
 * {@code descriptor.KernelCapability} per Day 3 of the convergence roadmap.</p>
 *
 * @doc.type interface
 * @doc.purpose Internal capability sub-registry behind KernelRegistry
 * @doc.layer core
 * @doc.pattern Registry
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@KernelInternal("Use KernelRegistry for capability discovery")
public interface CapabilityRegistry {

    /**
     * Register a capability implementation.
     *
     * @param capability the capability type
     * @param implementation the implementation instance
     */
    void registerCapability(KernelCapability capability, Object implementation);

    /**
     * Get a capability implementation by type.
     *
     * @param capability the capability type
     * @param type the expected implementation type
     * @return optional implementation
     */
    <T> Optional<T> getCapability(KernelCapability capability, Class<T> type);

    /**
     * Check if a capability is available.
     *
     * @param capability the capability type
     * @return true if the capability is registered
     */
    boolean hasCapability(KernelCapability capability);

    /**
     * Register a capability type (marks it as registered without an implementation).
     *
     * @param capability the capability type to register
     */
    default void registerCapability(KernelCapability capability) {
        registerCapability(capability, null);
    }

    /**
     * Check if a capability is available by its String ID.
     *
     * @param capabilityId the capability identifier string
     * @return optional containing the matching capability if found
     */
    default java.util.Optional<KernelCapability> getCapability(String capabilityId) {
        return getCapabilities().stream()
                .filter(c -> c.getCapabilityId().equalsIgnoreCase(capabilityId) || c.getName().equalsIgnoreCase(capabilityId))
                .findFirst();
    }

    /**
     * Get all registered capabilities.
     *
     * @return list of registered capability types
     */
    List<KernelCapability> getCapabilities();

    /**
     * Get a capability implementation by type (alias for {@link #getCapability}).
     *
     * @param capability the capability type
     * @param type the expected implementation type
     * @return the implementation, or throws if not found
     */
    default <T> T getCapabilityInstance(KernelCapability capability, Class<T> type) {
        return getCapability(capability, type)
                .orElseThrow(() -> new IllegalArgumentException("Capability not available: " + capability));
    }
}
