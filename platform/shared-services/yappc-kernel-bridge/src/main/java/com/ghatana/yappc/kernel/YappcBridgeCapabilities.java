package com.ghatana.yappc.kernel;

import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.Map;

/**
 * Canonical {@link KernelCapability} constants contributed by the YAPPC kernel bridge.
 *
 * @doc.type class
 * @doc.purpose Typed KernelCapability constants for YAPPC plugin system capabilities
 * @doc.layer adapter
 * @doc.pattern Constants
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class YappcBridgeCapabilities {

    /**
     * Capability indicating that YAPPC's PluginRegistry is available in the kernel context.
     *
     * <p>When present, callers may safely call:
     * {@code context.getDependency(PluginRegistry.class)}</p>
     */
    public static final KernelCapability YAPPC_PLUGIN_REGISTRY = new KernelCapability(
        "yappc.plugin-registry",
        "YAPPC Plugin Registry",
        "YAPPC in-process plugin registry accessible through the kernel context",
        KernelCapability.CapabilityType.INTEGRATION,
        Map.of("is_shared", "true", "scope", "platform")
    );

    /**
     * Capability indicating that YAPPC code-generation validators are available.
     */
    public static final KernelCapability YAPPC_CODE_VALIDATORS = new KernelCapability(
        "yappc.code-validators",
        "YAPPC Code Validators",
        "YAPPC ValidatorPlugin implementations accessible via PluginRegistry",
        KernelCapability.CapabilityType.BUSINESS_LOGIC,
        Map.of("is_shared", "true", "scope", "platform")
    );

    private YappcBridgeCapabilities() {
        // constants only
    }
}
