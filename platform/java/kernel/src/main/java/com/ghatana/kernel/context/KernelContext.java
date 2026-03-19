package com.ghatana.kernel.context;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.event.EventHandler;
import io.activej.eventloop.Eventloop;

import java.util.Optional;
import java.util.Set;

/**
 * Runtime context passed to every module during initialization.
 *
 * <p>Provides dependency lookup, event-handler registration, tenant context,
 * and access to the ActiveJ {@link Eventloop}. All modules receive this
 * via {@link com.ghatana.kernel.module.KernelModule#initialize(KernelContext)}.</p>
 *
 * <p>The context is the primary interface between kernel modules and the kernel
 * runtime. It provides access to:</p>
 * <ul>
 *   <li>Dependency lookup and resolution</li>
 *   <li>Event system registration</li>
 *   <li>Tenant context and isolation</li>
 *   <li>The ActiveJ event loop for async operations</li>
 *   <li>Available capability discovery</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Runtime context for dependency lookup, event wiring, and tenant access
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelContext {

    // ==================== Dependency Lookup ====================

    /**
     * Gets a required dependency by type.
     *
     * <p>The dependency must be registered with the kernel. If not found,
     * an exception is thrown.</p>
     *
     * @param type the dependency type class
     * @param <T> the dependency type
     * @return the dependency instance
     * @throws IllegalStateException if dependency not found
     */
    <T> T getDependency(Class<T> type);

    /**
     * Gets an optional dependency by type.
     *
     * <p>Returns empty if the dependency is not registered.</p>
     *
     * @param type the dependency type class
     * @param <T> the dependency type
     * @return optional containing the dependency if found
     */
    <T> Optional<T> getOptionalDependency(Class<T> type);

    /**
     * Checks if a dependency is available.
     *
     * @param type the dependency type to check
     * @return true if the dependency is registered
     */
    <T> boolean hasDependency(Class<T> type);

    /**
     * Gets a dependency by name.
     *
     * @param name the dependency name
     * @param type the expected type
     * @param <T> the dependency type
     * @return the dependency instance
     * @throws IllegalStateException if not found
     */
    <T> T getDependency(String name, Class<T> type);

    // ==================== Event System ====================

    /**
     * Registers an event handler for a specific event type.
     *
     * <p>The handler will be invoked for all events of the specified type
     * published through the kernel event system.</p>
     *
     * @param eventType the event type class
     * @param handler the event handler
     * @param <E> the event type
     */
    <E> void registerEventHandler(Class<E> eventType, EventHandler<E> handler);

    /**
     * Unregisters an event handler for a specific event type.
     *
     * @param eventType the event type class
     * @param handler the event handler to unregister
     * @param <E> the event type
     */
    <E> void unregisterEventHandler(Class<E> eventType, EventHandler<E> handler);

    /**
     * Publishes an event to the kernel event system.
     *
     * @param event the event to publish
     * @param <E> the event type
     */
    <E> void publishEvent(E event);

    // ==================== Tenant & Runtime ====================

    /**
     * Gets the current tenant context.
     *
     * <p>The tenant context provides tenant-specific configuration, feature flags,
     * and security context.</p>
     *
     * @return the current tenant context
     */
    KernelTenantContext getTenantContext();

    /**
     * Gets the kernel tenant context for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return the tenant context
     * @throws IllegalArgumentException if tenant not found
     */
    KernelTenantContext getTenantContext(String tenantId);

    /**
     * Gets the ActiveJ event loop.
     *
     * <p>All async operations in the kernel must use this event loop.</p>
     *
     * @return the event loop
     */
    Eventloop getEventloop();

    /**
     * Gets all available capabilities in the kernel.
     *
     * @return set of available capabilities
     */
    Set<KernelCapability> getAvailableCapabilities();

    /**
     * Checks if a specific capability is available.
     *
     * @param capability the capability to check
     * @return true if the capability is available
     */
    boolean hasCapability(KernelCapability capability);

    /**
     * Gets the kernel configuration value.
     *
     * @param key the configuration key
     * @param type the expected type
     * @param <T> the value type
     * @return the configuration value
     * @throws IllegalArgumentException if not found
     */
    <T> T getConfig(String key, Class<T> type);

    /**
     * Gets an optional kernel configuration value.
     *
     * @param key the configuration key
     * @param type the expected type
     * @param <T> the value type
     * @return optional containing the value if present
     */
    <T> Optional<T> getOptionalConfig(String key, Class<T> type);

    /**
     * Gets the kernel version.
     *
     * @return the kernel version string
     */
    String getKernelVersion();

    /**
     * Gets the kernel environment.
     *
     * @return the environment name (e.g., "development", "production")
     */
    String getEnvironment();
}
