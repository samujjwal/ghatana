package com.ghatana.kernel.plugin;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;

import java.util.Set;

/**
 * Dynamically loadable module with install/uninstall/reload semantics.
 *
 * <p>Kernel plugins extend modules with additional lifecycle operations for
 * dynamic loading, unloading, and reloading. Plugins can be installed and
 * removed at runtime without restarting the kernel.</p>
 *
 * @doc.type interface
 * @doc.purpose Runtime-loadable plugin extending KernelModule with hot-swap lifecycle
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelPlugin extends KernelModule {

    /**
     * Returns the plugin manifest.
     *
     * <p>The manifest contains metadata about the plugin including its
     * identifier, version, capabilities, and requirements.</p>
     *
     * @return the plugin manifest
     */
    PluginManifest getManifest();

    /**
     * Returns the contracts exported by this plugin.
     *
     * <p>Exported contracts are service interfaces that other plugins
     * can depend on and consume.</p>
     *
     * @return set of exported contract class names
     */
    Set<String> getExportedContracts();

    /**
     * Returns the contracts required by this plugin.
     *
     * <p>Required contracts are service interfaces that this plugin
     * depends on from other plugins or the kernel.</p>
     *
     * @return set of required contract class names
     */
    Set<String> getRequiredContracts();

    // ==================== Plugin Lifecycle ====================

    /**
     * Installs the plugin.
     *
     * <p>This method is called when the plugin is first installed.
     * It should perform any one-time setup such as:
     * <ul>
     *   <li>Database schema migrations</li>
     *   <li>Initial configuration</li>
     *   <li>Resource allocation</li>
     * </ul></p>
     *
     * <p>IMPORTANT: This method MUST return an ActiveJ Promise.</p>
     *
     * @return Promise that completes when installation is finished
     */
    Promise<Void> install();

    /**
     * Uninstalls the plugin.
     *
     * <p>This method is called when the plugin is being removed.
     * It should perform cleanup such as:
     * <ul>
     *   <li>Removing database tables (optional)</li>
     *   <li>Releasing resources</li>
     *   <li>Cleaning up configuration</li>
     * </ul></p>
     *
     * <p>IMPORTANT: This method MUST return an ActiveJ Promise.</p>
     *
     * @return Promise that completes when uninstallation is finished
     */
    Promise<Void> uninstall();

    /**
     * Reloads the plugin.
     *
     * <p>This method is called when the plugin configuration has changed
     * or when an explicit reload is requested. The plugin should:
     * <ul>
     *   <li>Reload configuration</li>
     *   <li>Refresh caches</li>
     *   <li>Restart internal services if needed</li>
     * </ul></p>
     *
     * <p>The default implementation stops and restarts the plugin.</p>
     *
     * <p>IMPORTANT: This method MUST return an ActiveJ Promise.</p>
     *
     * @return Promise that completes when reload is finished
     */
    default Promise<Void> reload() {
        return stop().then($ -> start());
    }

    // ==================== Default Implementations ====================

    /**
     * Default implementation delegates to manifest.
     */
    @Override
    default String getModuleId() {
        return getManifest().getPluginId();
    }

    /**
     * Default implementation delegates to manifest.
     */
    @Override
    default String getVersion() {
        return getManifest().getVersion();
    }

    /**
     * Default implementation delegates to manifest.
     */
    @Override
    default Set<com.ghatana.kernel.descriptor.KernelCapability> getCapabilities() {
        return getManifest().getCapabilities();
    }

    /**
     * Default implementation delegates to manifest.
     */
    @Override
    default Set<com.ghatana.kernel.descriptor.KernelDependency> getDependencies() {
        return getManifest().getDependencies();
    }

    /**
     * Default implementation - no-op.
     */
    @Override
    default void initialize(KernelContext context) {
        // No-op by default
    }

    /**
     * Default implementation - immediate completion.
     */
    @Override
    default Promise<Void> start() {
        return Promise.complete();
    }

    /**
     * Default implementation - immediate completion.
     */
    @Override
    default Promise<Void> stop() {
        return Promise.complete();
    }

    /**
     * Default implementation - always healthy.
     */
    @Override
    default HealthStatus getHealthStatus() {
        return HealthStatus.healthy();
    }
}
