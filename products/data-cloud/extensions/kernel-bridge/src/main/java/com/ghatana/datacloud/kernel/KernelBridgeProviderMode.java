package com.ghatana.datacloud.kernel;

/**
 * Kernel provider mode used by bridge health reporting.
 *
 * @doc.type enum
 * @doc.purpose Distinguishes bootstrap vs platform modes for kernel bridge provider health reporting
 * @doc.layer product
 * @doc.pattern Type Object
 */
public enum KernelBridgeProviderMode {
    BOOTSTRAP,
    PLATFORM
}
