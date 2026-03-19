package com.ghatana.kernel.plugin;

/**
 * Kernel extension interface for ProductPlugin integration.
 *
 * <p>This is a simplified extension interface used by ProductPlugin to
 * declare extensions. For full kernel module extensions, use
 * {@link com.ghatana.kernel.extension.KernelExtension}.</p>
 *
 * @doc.type interface
 * @doc.purpose Simplified extension interface for product plugins
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelExtension {

    /**
     * Get the extension identifier.
     *
     * @return unique extension ID
     */
    String getExtensionId();

    /**
     * Get the extension version.
     *
     * @return semantic version string
     */
    String getVersion();

    /**
     * Get the extension description.
     *
     * @return human-readable description
     */
    String getDescription();

    /**
     * Get the capability this extension extends.
     *
     * @return target capability ID
     */
    String getTargetCapabilityId();

    /**
     * Initialize the extension.
     *
     * @param context the plugin context
     */
    void initialize(PluginContext context);

    /**
     * Start the extension.
     */
    void start();

    /**
     * Stop the extension.
     */
    void stop();

    /**
     * Shutdown the extension.
     */
    void shutdown();
}
