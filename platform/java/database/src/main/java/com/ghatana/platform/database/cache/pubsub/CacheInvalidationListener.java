package com.ghatana.platform.database.cache.pubsub;

/**
 * Listener interface for cache invalidation events.
 *
 * <p><b>Purpose</b><br>
 * Callback interface invoked when cache invalidation messages are received
 * via Redis pub/sub. Implementations handle actual cache invalidation logic.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CacheInvalidationListener listener = message -> {
 *     switch (message.getOperation()) {
 *         case INVALIDATE_KEYS:
 *             message.getKeys().forEach(cache::remove);
 *             break;
 *         case INVALIDATE_PATTERN:
 *             cache.clearPattern(message.getPattern());
 *             break;
 *         case CLEAR_NAMESPACE:
 *             cache.clear();
 *             break;
 *     }
 *     logger.info("Invalidated cache: {}", message);
 * };
 *
 * pubSubManager.subscribe(listener);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations MUST be thread-safe. Called from Redis pub/sub listener thread.
 *
 * @doc.type interface
 * @doc.purpose Cache invalidation event listener
 * @doc.layer core
 * @doc.pattern Observer
 */
@FunctionalInterface
public interface CacheInvalidationListener {
    
    /**
     * Handle cache invalidation message
     *
     * <p>Called when invalidation message received from Redis pub/sub.
     * Implementation should be fast and non-blocking. Long operations
     * should be delegated to background threads.
     *
     * @param message Cache invalidation message
     */
    void onInvalidation(CacheInvalidationMessage message);
}
