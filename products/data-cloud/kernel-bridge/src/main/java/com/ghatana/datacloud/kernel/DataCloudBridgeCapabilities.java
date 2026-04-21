package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.descriptor.KernelCapability;

import java.util.Map;

/**
 * Canonical {@link KernelCapability} constants contributed by the Data-Cloud kernel bridge.
 *
 * <p>Products and modules can use these constants to declare or check for Data-Cloud
 * storage capabilities in the kernel context, without hard-coding string IDs.</p>
 *
 * @doc.type class
 * @doc.purpose Typed KernelCapability constants for Data-Cloud storage bridge
 * @doc.layer adapter
 * @doc.pattern Constants
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public final class DataCloudBridgeCapabilities {

    /**
     * Capability indicating that a live Data-Cloud adapter is registered in the kernel context.
     *
     * <p>When this capability is present, callers may safely call:
     * {@code context.getDependency(DataCloudKernelAdapter.class)}</p>
     */
    public static final KernelCapability DATA_CLOUD_STORAGE = new KernelCapability(
        "data-cloud.storage",
        "Data-Cloud Storage",
        "Live Data-Cloud storage adapter registered in kernel context",
        KernelCapability.CapabilityType.DATA_MANAGEMENT,
        Map.of("is_shared", "true", "scope", "platform")
    );

    /**
     * Capability indicating that Data-Cloud transaction support is available.
     */
    public static final KernelCapability DATA_CLOUD_TRANSACTIONS = new KernelCapability(
        "data-cloud.transactions",
        "Data-Cloud Transactions",
        "ACID transaction support via Data-Cloud adapter",
        KernelCapability.CapabilityType.DATA_MANAGEMENT,
        Map.of("is_shared", "true", "scope", "platform")
    );

    /**
     * Capability indicating that Data-Cloud streaming reads/writes are available.
     */
    public static final KernelCapability DATA_CLOUD_STREAMING = new KernelCapability(
        "data-cloud.streaming",
        "Data-Cloud Streaming",
        "Large-dataset streaming via Data-Cloud adapter",
        KernelCapability.CapabilityType.DATA_MANAGEMENT,
        Map.of("is_shared", "true", "scope", "platform")
    );

    private DataCloudBridgeCapabilities() {
        // constants only
    }
}
