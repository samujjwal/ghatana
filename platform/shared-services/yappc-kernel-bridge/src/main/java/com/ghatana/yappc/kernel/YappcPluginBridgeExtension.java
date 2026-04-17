package com.ghatana.yappc.kernel;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.AbstractKernelExtension;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.yappc.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * KernelExtension that exposes YAPPC's in-process {@link PluginRegistry} into the kernel context.
 *
 * <h2>Architectural role</h2>
 * <p>YAPPC maintains its own in-process plugin registry ({@link PluginRegistry}) for
 * code-generation validators, generators, and other product-specific plugins. This bridge
 * registers that registry as a kernel service so that cross-product consumers can discover
 * and invoke YAPPC plugins without coupling directly to YAPPC's internal wiring.</p>
 *
 * <h2>Dependency direction</h2>
 * <pre>
 *   kernel-core  &lt;─── yappc-kernel-bridge  &lt;─── yappc product (provides PluginRegistry)
 * </pre>
 *
 * <h2>Usage in the YAPPC launcher / service entry-point</h2>
 * <pre>{@code
 * PluginRegistry registry = PluginRegistry.create(pluginContext);
 * YappcPluginBridgeExtension extension = new YappcPluginBridgeExtension(registry);
 * kernelModule.registerExtension(extension);
 * }</pre>
 *
 * <p>After module initialisation, kernel consumers can retrieve the registry:</p>
 * <pre>{@code
 * PluginRegistry plugins = context.getDependency(PluginRegistry.class);
 * List&lt;ValidatorPlugin&gt; validators = plugins.getValidators();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Bridge extension that exposes YAPPC PluginRegistry via the kernel context
 * @doc.layer adapter
 * @doc.pattern Extension, Bridge
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class YappcPluginBridgeExtension extends AbstractKernelExtension {

    private static final Logger LOG = LoggerFactory.getLogger(YappcPluginBridgeExtension.class);

    private static final String EXTENSION_ID = "yappc-plugin-bridge";
    private static final String EXTENSION_NAME = "YAPPC Plugin Bridge";
    private static final String EXTENSION_VERSION = "1.0.0";

    private final PluginRegistry pluginRegistry;

    /**
     * Creates the bridge extension backed by the provided YAPPC plugin registry.
     *
     * @param pluginRegistry the initialized YAPPC plugin registry — must not be {@code null}
     */
    public YappcPluginBridgeExtension(PluginRegistry pluginRegistry) {
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "PluginRegistry must not be null");
    }

    // ==================== KernelExtension identity ====================

    @Override
    public String getExtensionId() {
        return EXTENSION_ID;
    }

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getVersion() {
        return EXTENSION_VERSION;
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return new KernelDescriptor.Builder()
            .withDescriptorId(EXTENSION_ID)
            .withName(EXTENSION_NAME)
            .withVersion(EXTENSION_VERSION)
            .withType(KernelDescriptor.DescriptorType.EXTENSION)
            .withDescription("Registers YAPPC's PluginRegistry into the kernel context. " +
                             "YAPPC provides this extension; the kernel receives code-generation capabilities.")
            .build();
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of(
            YappcBridgeCapabilities.YAPPC_PLUGIN_REGISTRY,
            YappcBridgeCapabilities.YAPPC_CODE_VALIDATORS
        );
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return hostModule != null;
    }

    // ==================== Lifecycle hooks ====================

    @Override
    protected void onInitialize(KernelContext context) {
        LOG.info("[YappcPluginBridgeExtension] Initializing: registering PluginRegistry into context ({} plugins)",
            pluginRegistry.getPluginCount());

        context.registerService(PluginRegistry.class, pluginRegistry);

        LOG.info("[YappcPluginBridgeExtension] PluginRegistry registered successfully");
    }

    @Override
    protected void onStart(KernelContext context) {
        LOG.info("[YappcPluginBridgeExtension] Started — YAPPC plugin bridge active ({} plugins)",
            pluginRegistry.getPluginCount());
    }

    @Override
    protected void onStop(KernelContext context) {
        LOG.info("[YappcPluginBridgeExtension] Stopping — YAPPC plugin bridge deactivated");
    }
}
