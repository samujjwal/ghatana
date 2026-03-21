package com.ghatana.kernel.registry;

import com.ghatana.kernel.annotation.KernelInternal;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.plugin.KernelOperator;

import java.util.Optional;
import java.util.Set;

/**
 * Registry for discovering and managing kernel services.
 *
 * <p><b>Internal helper registry.</b> Per KERNEL_CANONICALIZATION_DECISIONS.md (Decision D4),
 * {@link KernelRegistry} is the only public root registry contract. This registry is an
 * internal implementation facet behind KernelRegistry. External consumers MUST use
 * KernelRegistry for service discovery.</p>
 *
 * @doc.type interface
 * @doc.purpose Internal service sub-registry behind KernelRegistry
 * @doc.layer core
 * @doc.pattern Registry
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@KernelInternal("Use KernelRegistry for service and module discovery")
public interface ServiceRegistry {

    /**
     * Register a service with the given identifier.
     *
     * @param serviceId the service identifier
     * @param service the service implementation
     */
    void registerService(String serviceId, Object service);

    /**
     * Get a service by identifier and type.
     *
     * @param serviceId the service identifier
     * @param type the expected service type
     * @return optional service instance
     */
    <T> Optional<T> getService(String serviceId, Class<T> type);

    /**
     * Check if a service is registered.
     *
     * @param serviceId the service identifier
     * @return true if the service is registered
     */
    boolean hasService(String serviceId);

    /**
     * Get all registered service identifiers.
     *
     * @return set of registered service IDs
     */
    Set<String> getServiceIds();

    /**
     * Unregister a service.
     *
     * @param serviceId the service identifier
     * @return true if removed, false if not found
     */
    boolean unregisterService(String serviceId);

    /**
     * Get the kernel context.
     *
     * @return the kernel context
     */
    com.ghatana.kernel.context.KernelContext getKernelContext();

    /**
     * Register a kernel extension.
     *
     * @param extension the extension to register
     */
    void registerExtension(KernelExtension extension);

    /**
     * Register a kernel operator.
     *
     * @param operator the operator to register
     */
    void registerOperator(KernelOperator operator);
}
