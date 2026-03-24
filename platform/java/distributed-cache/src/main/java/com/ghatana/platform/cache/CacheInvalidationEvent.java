package com.ghatana.platform.cache;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable event payload for cross-node cache invalidation.
 *
 * <p>Published via {@code KernelInterScopeBus.publishEvent()} so that all nodes
 * in a horizontally-scaled deployment receive and act on invalidation signals.
 * Consumers should call {@link DistributedCachePort#invalidate(Object)} (or
 * {@code invalidateAll()} if {@link #invalidateAll()} is {@code true}) upon
 * receipt.</p>
 *
 * <pre>{@code
 * // Publishing:
 * bus.publishEvent(CrossScopeEvent.builder()
 *     .sourceScope(ScopeDescriptor.product("finance"))
 *     .targetScope(ScopeDescriptor.product("finance"))
 *     .eventType(CacheInvalidationEvent.EVENT_TYPE)
 *     .payload(new CacheInvalidationEvent("finance.risk", "trader-123", false, Instant.now()))
 *     .build());
 *
 * // Consuming:
 * context.registerEventHandler(CrossScopeEvent.class, evt -> {
 *     if (CacheInvalidationEvent.EVENT_TYPE.equals(evt.getEventType())) {
 *         CacheInvalidationEvent inv = (CacheInvalidationEvent) evt.getPayload();
 *         if ("finance.risk".equals(inv.namespace())) {
 *             riskCache.invalidate(inv.key());
 *         }
 *     }
 * });
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Cross-node cache invalidation event for DistributedCachePort consumers
 * @doc.layer platform
 * @doc.pattern EventMessage
 * @since 1.0.0
 */
public record CacheInvalidationEvent(

    /** Logical namespace of the cache to invalidate (e.g., {@code "finance.risk"}). */
    String namespace,

    /**
     * Key to invalidate. If {@link #invalidateAll} is {@code true} this field is ignored
     * and callers should flush the entire namespace.
     */
    String key,

    /** When {@code true}, the entire namespace should be flushed rather than a single key. */
    boolean invalidateAll,

    /** Wall-clock time at which the invalidation was triggered on the originating node. */
    Instant triggeredAt

) {
    /** Stable event type string for filter-based dispatch. */
    public static final String EVENT_TYPE = "platform.cache.invalidation.v1";

    public CacheInvalidationEvent {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    }

    /** Factory for a single-key invalidation event. */
    public static CacheInvalidationEvent ofKey(String namespace, String key) {
        return new CacheInvalidationEvent(namespace, Objects.requireNonNull(key), false, Instant.now());
    }

    /** Factory for a full-namespace invalidation event. */
    public static CacheInvalidationEvent ofNamespace(String namespace) {
        return new CacheInvalidationEvent(namespace, null, true, Instant.now());
    }
}
