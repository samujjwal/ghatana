package com.ghatana.kernel.interaction;

/**
 * Product interaction outcome statuses aligned with Kernel ProductUnit contracts.
 *
 * @doc.type enum
 * @doc.purpose Product interaction outcome status
 * @doc.layer kernel
 * @doc.pattern Enum
 */
public enum ProductInteractionStatus {
    ALLOWED,
    DENIED,
    BLOCKED,
    FAILED,
    DEGRADED,
    SUCCEEDED
}
