package com.ghatana.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import io.activej.promise.Promise;

import java.util.Set;

/**
 * Core interface every kernel module MUST implement.
 *
 * <p>IMPORTANT — ActiveJ mandate: {@code start()} and {@code stop()} return
 * {@link Promise}{@code <Void>}, NOT {@code CompletableFuture}.
 * CompletableFuture, Spring Reactor (Mono/Flux) and RxJava are BANNED
 * in kernel code. At adapter boundaries use {@code Promise.ofFuture(cf)}.</p>
 *
 * <p>This interface defines the contract for all kernel modules including:
 * <ul>
 *   <li>Identity and versioning</li>
 *   <li>Capability declaration</li>
 *   <li>Dependency specification</li>
 *   <li>Lifecycle management (initialize, start, stop)</li>
 *   <li>Health status reporting</li>
 * </ul></p>
 *
 * @doc.type interface
 * @doc.purpose Canonical contract for all kernel modules — lifecycle, capabilities, health
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelModule {

    /**
     * Returns the unique module identifier.
     *
     * <p>The module ID must be unique across the kernel and follow the naming
     * convention: lowercase letters, numbers, and hyphens only.</p>
     *
     * @return the module identifier (e.g., "data-storage", "auth-service")
     */
    String getModuleId();

    /**
     * Returns the module version.
     *
     * <p>Version must follow semantic versioning format: MAJOR.MINOR.PATCH</p>
     *
     * @return the module version (e.g., "1.0.0")
     */
    String getVersion();

    /**
     * Returns the set of capabilities provided by this module.
     *
     * <p>Capabilities declare what functionality this module provides to other
     * modules and the kernel. The kernel uses this information for dependency
     * resolution and capability discovery.</p>
     *
     * @return set of provided capabilities (never null, may be empty)
     */
    Set<KernelCapability> getCapabilities();

    /**
     * Returns the set of dependencies required by this module.
     *
     * <p>Dependencies declare what other modules, capabilities, or external
     * services this module requires to function. The kernel validates these
     * dependencies during module initialization.</p>
     *
     * @return set of required dependencies (never null, may be empty)
     */
    Set<KernelDependency> getDependencies();

    /**
     * Initializes the module with the kernel context.
     *
     * <p>This method is called once during module registration. The module
     * should use the provided context to access dependencies, register event
     * handlers, and perform any one-time setup.</p>
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>This is NOT an async method - use synchronous initialization</li>
     *   <li>Do NOT start background tasks here - use start() instead</li>
     *   <li>Throw exceptions for fatal initialization errors</li>
     * </ul></p>
     *
     * @param context the kernel context providing access to dependencies and services
     * @throws IllegalStateException if initialization fails
     */
    void initialize(KernelContext context);

    /**
     * Starts the module and all its services.
     *
     * <p>This method is called after all modules have been initialized and
     * dependencies resolved. The module should start all its background tasks,
     * accept incoming requests, and begin normal operation.</p>
     *
     * <p>IMPORTANT: This method MUST return an ActiveJ Promise, NOT CompletableFuture.
     * Use Promise.complete() for synchronous completion or Promises.all() for
     * multiple async operations.</p>
     *
     * @return Promise that completes when the module is fully started
     */
    Promise<Void> start();

    /**
     * Stops the module and all its services.
     *
     * <p>This method is called during kernel shutdown or when the module is
     * being deactivated. The module should gracefully stop all background tasks,
     * close connections, and release resources.</p>
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Must complete within a reasonable timeout (default: 30 seconds)</li>
     *   <li>Should be idempotent - safe to call multiple times</li>
     *   <li>Should gracefully handle in-flight requests</li>
     * </ul></p>
     *
     * <p>IMPORTANT: This method MUST return an ActiveJ Promise, NOT CompletableFuture.</p>
     *
     * @return Promise that completes when the module is fully stopped
     */
    Promise<Void> stop();

    /**
     * Returns the current health status of the module.
     *
     * <p>The kernel periodically calls this method to check module health.
     * Modules should perform internal health checks and return appropriate
     * status based on their operational state.</p>
     *
     * @return the current health status (never null)
     */
    HealthStatus getHealthStatus();

    /**
     * Checks if this module provides a specific capability.
     *
     * <p>Convenience method that checks if the module's capabilities set
     * contains the specified capability.</p>
     *
     * @param capability the capability to check
     * @return true if the module provides the capability
     */
    default boolean hasCapability(KernelCapability capability) {
        return getCapabilities().contains(capability);
    }

    /**
     * Checks if this module has a specific dependency.
     *
     * <p>Convenience method that checks if the module's dependencies set
     * contains a dependency on the specified module or capability.</p>
     *
     * @param dependencyId the dependency ID to check
     * @return true if the module has the dependency
     */
    default boolean hasDependency(String dependencyId) {
        return getDependencies().stream()
            .anyMatch(d -> d.getDependencyId().equals(dependencyId));
    }
}
