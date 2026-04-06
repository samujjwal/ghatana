package com.ghatana.kernel.extension;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.module.KernelModule;

import java.util.Set;

/**
 * Extension point for product-specific enrichments that are NOT full modules.
 *
 * <p>Extensions contribute additional capabilities to existing kernel modules without
 * introducing new modules. Examples: DualCalendarKernelExtension, HealthcareConsentKernelExtension.</p>
 *
 * <p>Extensions are invoked by the hosting KernelModule during lifecycle transitions:</p>
 * <ul>
 *   <li>{@link #onModuleInitialized(KernelContext)} - Called after module initialization</li>
 *   <li>{@link #onModuleStarted(KernelContext)} - Called after module start completes</li>
 *   <li>{@link #onModuleStopped(KernelContext)} - Called before module stop completes</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Optional enrichment contract for product-specific kernel extensions
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface KernelExtension {

    /**
     * Returns the stable extension identifier.
     *
     * <p>Must be unique within the kernel and follow the naming convention:
     * lowercase letters, numbers, and hyphens only (e.g., "dual-calendar", "healthcare-consent").</p>
     *
     * @return the extension identifier
     */
    String getExtensionId();

    /**
     * Returns the human-readable name.
     *
     * @return the extension name
     */
    String getName();

    /**
     * Returns the extension version following semantic versioning.
     *
     * @return the version string (e.g., "1.0.0")
     */
    String getVersion();

    /**
     * Returns the descriptor exposing version, capabilities, and metadata.
     *
     * @return the kernel descriptor for this extension
     */
    KernelDescriptor getDescriptor();

    /**
     * Returns the capabilities this extension contributes.
     *
     * <p>These capabilities are merged into the hosting module's capability set
     * when the extension is loaded. Other modules can depend on these capabilities.</p>
     *
     * @return set of contributed capabilities (never null, may be empty)
     */
    Set<KernelCapability> getContributedCapabilities();

    /**
     * Called when the hosting module is initialized.
     *
     * <p>The extension should use this callback to register event handlers,
     * initialize internal state, and perform any setup that requires the
     * kernel context.</p>
     *
     * @param context the kernel context providing access to dependencies
     */
    void onModuleInitialized(KernelContext context);

    /**
     * Called when the hosting module is started.
     *
     * <p>The extension should start any background tasks or services in this
     * callback. This is called after all modules have been initialized.</p>
     *
     * @param context the kernel context
     */
    void onModuleStarted(KernelContext context);

    /**
     * Called when the hosting module is stopped.
     *
     * <p>The extension should gracefully shutdown any background tasks or
     * services in this callback. This is called before the module's stop()
     * method returns.</p>
     *
     * @param context the kernel context
     */
    void onModuleStopped(KernelContext context);

    /**
     * Checks compatibility with the hosting module.
     *
     * <p>The extension should verify that the hosting module has the required
     * capabilities and is compatible with this extension's requirements.</p>
     *
     * @param hostModule the hosting module to check compatibility with
     * @return true if compatible and can be loaded
     */
    boolean isCompatible(KernelModule hostModule);

    /**
     * Returns the priority of this extension.
     *
     * <p>Higher priority extensions are loaded first. Extensions with the same
     * priority are loaded in registration order.</p>
     *
     * @return the extension priority (default: 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Returns whether this extension is enabled by default.
     *
     * @return true if enabled by default
     */
    default boolean isEnabledByDefault() {
        return true;
    }
}
