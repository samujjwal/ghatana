package com.ghatana.yappc.kernel;

import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.Map;

/**
 * Canonical {@link KernelCapability} constants contributed by the YAPPC kernel bridge.
 *
 * @doc.type class
 * @doc.purpose Typed KernelCapability constants for YAPPC evidence bridge capabilities
 * @doc.layer adapter
 * @doc.pattern Constants
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class YappcBridgeCapabilities {

    public static final KernelCapability YAPPC_PRODUCT_UNIT_INTENTS = new KernelCapability(
        "yappc.product-unit-intents",
        "YAPPC ProductUnitIntent Provider",
        "YAPPC ProductUnitIntent candidates accessible through a narrow provider port",
        KernelCapability.CapabilityType.INTEGRATION,
        Map.of("is_shared", "true", "scope", "platform")
    );

    public static final KernelCapability YAPPC_ARTIFACT_INTELLIGENCE = new KernelCapability(
        "yappc.artifact-intelligence",
        "YAPPC Artifact Intelligence",
        "YAPPC semantic artifact evidence exposed through stable provider ports",
        KernelCapability.CapabilityType.BUSINESS_LOGIC,
        Map.of("is_shared", "true", "scope", "platform")
    );

    private YappcBridgeCapabilities() {
        // constants only
    }
}
