package com.ghatana.kernel.registry;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.plugin.KernelPlugin;

import java.util.List;
import java.util.Optional;

/**
 * Central registry for module/plugin discovery and dependency resolution.
 *
 * <p><b>This is the ONLY public root registry contract.</b> Per Decision D4
 * (KERNEL_CANONICALIZATION_DECISIONS.md), all external consumers (products,
 * plugins, adapters) MUST use this interface for registration, discovery,
 * and lifecycle management.</p>
 *
 * <p>Internal sub-registries ({@code CapabilityRegistry}, {@code ServiceRegistry},
 * {@code PluginRegistry}) are annotated with {@code @KernelInternal} and must not
 * be referenced by code outside the kernel implementation.</p>
 *
 * @doc.type interface
 * @doc.purpose Canonical public registry contract for module/plugin/capability discovery (D4)
 * @doc.layer core
 * @doc.pattern Service, Registry
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelRegistry {

    // ==================== Registration ====================

    /**
     * Registers a kernel module.
     *
     * <p>The module is validated and its dependencies are checked. If validation
     * fails, an exception is thrown and the module is not registered.</p>
     *
     * @param module the module to register
     * @throws IllegalStateException if registration fails
     */
    void registerModule(KernelModule module);

    /**
     * Registers a kernel plugin.
     *
     * <p>Plugins are loadable modules with additional lifecycle (install, uninstall, reload).</p>
     *
     * @param plugin the plugin to register
     * @throws IllegalStateException if registration fails
     */
    void registerPlugin(KernelPlugin plugin);

    /**
     * Registers a capability.
     *
     * <p>Capabilities are registered globally and can be discovered by modules.</p>
     *
     * @param capability the capability to register
     */
    void registerCapability(KernelCapability capability);

    /**
     * Unregisters a module.
     *
     * <p>The module is stopped before unregistration if it's running.</p>
     *
     * @param moduleId the module identifier
     * @return true if unregistered, false if not found
     */
    boolean unregisterModule(String moduleId);

    // ==================== Discovery ====================

    /**
     * Gets a module by ID.
     *
     * @param moduleId the module identifier
     * @return optional containing the module if found
     */
    Optional<KernelModule> getModule(String moduleId);

    /**
     * Gets a plugin by ID.
     *
     * @param pluginId the plugin identifier
     * @return optional containing the plugin if found
     */
    Optional<KernelPlugin> getPlugin(String pluginId);

    /**
     * Gets all registered modules.
     *
     * @return list of all modules
     */
    List<KernelModule> getAllModules();

    /**
     * Gets all registered plugins.
     *
     * @return list of all plugins
     */
    List<KernelPlugin> getAllPlugins();

    /**
     * Gets all registered capabilities.
     *
     * @return list of all capabilities
     */
    List<KernelCapability> getAllCapabilities();

    /**
     * Gets plugins that provide a specific capability.
     *
     * @param capability the capability to search for
     * @return list of plugins providing the capability
     */
    List<KernelPlugin> getPluginsByCapability(KernelCapability capability);

    /**
     * Gets modules that provide a specific capability.
     *
     * @param capability the capability to search for
     * @return list of modules providing the capability
     */
    List<KernelModule> getModulesByCapability(KernelCapability capability);

    /**
     * Gets modules that depend on a specific module.
     *
     * @param moduleId the module identifier
     * @return list of dependent modules
     */
    List<KernelModule> getDependentModules(String moduleId);

    // ==================== Dependency Resolution ====================

    /**
     * Resolves dependencies for a module.
     *
     * <p>Returns the ordered list of modules that need to be started before
     * the given module, based on dependency graph analysis.</p>
     *
     * @param module the module to resolve dependencies for
     * @return ordered list of dependencies (includes transitive dependencies)
     */
    List<KernelModule> resolveDependencies(KernelModule module);

    /**
     * Validates module dependencies.
     *
     * <p>Checks that all declared dependencies are satisfied by registered modules.</p>
     *
     * @param module the module to validate
     * @return true if all dependencies are satisfied
     */
    boolean validateDependencies(KernelModule module);

    /**
     * Gets validation errors for module dependencies.
     *
     * @param module the module to check
     * @return list of error messages for missing dependencies
     */
    List<String> getDependencyValidationErrors(KernelModule module);

    // ==================== Lifecycle ====================

    /**
     * Starts all registered modules in dependency order.
     *
     * @return promise that completes when all modules are started
     */
    io.activej.promise.Promise<Void> startAllModules();

    /**
     * Stops all registered modules in reverse dependency order.
     *
     * @return promise that completes when all modules are stopped
     */
    io.activej.promise.Promise<Void> stopAllModules();

    /**
     * Checks if a module is registered.
     *
     * @param moduleId the module identifier
     * @return true if registered
     */
    boolean isModuleRegistered(String moduleId);

    /**
     * Checks if a capability is available.
     *
     * @param capabilityId the capability identifier
     * @return true if available
     */
    boolean isCapabilityAvailable(String capabilityId);
}
