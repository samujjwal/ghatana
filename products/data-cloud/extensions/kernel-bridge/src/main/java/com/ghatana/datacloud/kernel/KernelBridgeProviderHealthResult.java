package com.ghatana.datacloud.kernel;

import java.util.List;

/**
 * Health result for Data Cloud-backed Kernel bridge providers.
 *
 * @doc.type record
 * @doc.purpose Expose provider health without leaking Data Cloud internals into Kernel packages
 * @doc.layer adapter
 * @doc.pattern DTO
 */
public record KernelBridgeProviderHealthResult(
    String providerId,
    KernelBridgeProviderMode mode,
    KernelBridgeProviderStatus status,
    String reason,
    long latencyMillis,
    String lastSuccessAt,
    String lastFailureAt,
    List<String> evidenceRefs
) {}
