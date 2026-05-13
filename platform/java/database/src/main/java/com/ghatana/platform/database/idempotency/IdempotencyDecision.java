package com.ghatana.platform.database.idempotency;

/**
 * @doc.type enum
 * @doc.purpose Classifies Kernel idempotency decisions for conformance and audit evidence
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum IdempotencyDecision {
    MISS,
    COMPLETED,
    EXPIRED,
    CONFLICT
}
