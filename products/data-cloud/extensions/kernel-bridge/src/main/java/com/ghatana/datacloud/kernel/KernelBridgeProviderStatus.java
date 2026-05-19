package com.ghatana.datacloud.kernel;

/**
 * Provider health status used by Data Cloud Kernel bridge providers.
 *
 * @doc.type enum
 * @doc.purpose Represents the health state of a kernel bridge provider for diagnostics and readiness checks
 * @doc.layer product
 * @doc.pattern Type Object
 */
public enum KernelBridgeProviderStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
}
