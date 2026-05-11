package com.ghatana.platform.observability.idempotency;

/**
 * Route-level idempotency requirement.
 *
 * <p>P0-07: Defines the idempotency behavior for a mutating route.
 * Each POST, PUT, DELETE, and state-changing route must declare its idempotency requirement.
 *
 * @doc.type enum
 * @doc.purpose Route-level idempotency requirement contract
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum IdempotencyRequirement {

    /**
     * Idempotency is required for this route.
     *
     * <p>Clients must provide an X-Idempotency-Key header. The system will cache
     * responses and return the cached response on retries with the same key.
     * Conflicting reuse of the same key with a different payload returns 409.
     *
     * <p>Use for operations where retries could cause duplicate work or side effects.
     */
    IDEMPOTENT_REQUIRED,

    /**
     * Route is naturally idempotent without requiring an idempotency key.
     *
     * <p>Multiple calls with the same parameters have the same effect as a single call.
     * The system does not require X-Idempotency-Key but may still cache responses.
     *
     * <p>Use for idempotent operations like PUT (replace) or DELETE by ID.
     */
    NATURALLY_IDEMPOTENT,

    /**
     * Route is explicitly non-idempotent.
     *
     * <p>Each call has a distinct effect. The system does not support idempotency for this route.
     * Clients should implement their own deduplication if needed.
     *
     * <p>Use for operations that are inherently non-idempotent like incrementing a counter.
     */
    NON_IDEMPOTENT_EXPLICIT
}
