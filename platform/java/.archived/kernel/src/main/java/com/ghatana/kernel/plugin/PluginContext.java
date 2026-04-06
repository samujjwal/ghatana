package com.ghatana.kernel.plugin;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.context.KernelContext;

import java.util.Optional;

/**
 * Plugin context provides access to kernel services.
 *
 * <p>This interface gives plugins access to kernel capabilities and services
 * through typed accessor methods ({@link #getCapability}, {@link #registerService}, etc.)
 * without exposing internal kernel implementation details.</p>
 *
 * @doc.type interface
 * @doc.purpose Plugin context for accessing kernel capabilities and services
 * @doc.layer core
 * @doc.pattern Context
 */
public interface PluginContext {

    /**
     * Get the underlying kernel context.
     */
    KernelContext getKernelContext();

    /**
     * Get a specific kernel capability instance.
     *
     * @param capability the capability to get
     * @param type the expected type
     * @return the capability instance
     * @throws IllegalArgumentException if capability is not available
     */
    <T> T getCapability(KernelCapability capability, Class<T> type);

    /**
     * Get an optional kernel capability instance.
     *
     * @param capability the capability to get
     * @param type the expected type
     * @return optional capability instance
     */
    <T> Optional<T> getOptionalCapability(KernelCapability capability, Class<T> type);

    /**
     * Register a service with the kernel.
     *
     * @param serviceId the service identifier
     * @param service the service implementation
     */
    void registerService(String serviceId, Object service);

    /**
     * Register an operator with the kernel.
     *
     * @param operator the operator to register
     */
    void registerOperator(KernelOperator operator);
}
