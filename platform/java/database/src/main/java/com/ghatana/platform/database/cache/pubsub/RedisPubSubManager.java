package com.ghatana.platform.database.cache.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis pub/sub manager for cache invalidation events.
 *
 * <p><b>Purpose</b><br>
 * Manages Redis pub/sub connections for broadcasting and receiving cache
 * invalidation messages across distributed application instances.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * JedisPool pool = new JedisPool("localhost", 6379);
 * ObjectMapper mapper = new ObjectMapper();
 * MetricsCollector metrics = ...;
 *
 * RedisPubSubManager pubSub = new RedisPubSubManager(
 *     pool,
 *     mapper,
 *     "cache-invalidation",
 *     "instance-1",
 *     metrics
 * );
 *
 * // Start subscriber
 * pubSub.start().await();
 *
 * // Subscribe to invalidation events
 * pubSub.subscribe(message -> {
 *     cache.invalidate(message.getKeys());
 * });
 *
 * // Publish invalidation
 * pubSub.publish(CacheInvalidationMessage.invalidateKeys(
 *     Set.of("user:123"),
 *     "tenant-acme",
 *     "instance-1"
 * )).await();
 *
 * // Cleanup
 * pubSub.stop().await();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Redis pub/sub subscriber runs in background thread
 * - Publisher uses connection pool for each publish
 * - Listeners invoked in subscriber thread (must be fast)
 * - Automatic reconnection on connection loss
 * - Metrics tracked for publish/receive/error counts
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Listeners stored in CopyOnWriteArraySet.
 * Subscriber thread managed internally.
 *
 * @doc.type class
 * @doc.purpose Redis pub/sub manager for cache invalidation
 * @doc.layer core
 * @doc.pattern PubSub, Observer
 */
public class RedisPubSubManager {
    private static final Logger logger = LoggerFactory.getLogger(RedisPubSubManager.class);
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String channelName;
    private final String instanceId;
    private final MetricsCollector metrics;
    private final ExecutorService blockingExecutor;
    
    private final Set<CacheInvalidationListener> listeners = new CopyOnWriteArraySet<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong publishCount = new AtomicLong(0);
    private final AtomicLong receiveCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    private ExecutorService subscriberExecutor;
    private JedisPubSubHandler pubSubHandler;
    
    /**
     * Creates Redis pub/sub manager
     *
     * @param jedisPool Redis connection pool
     * @param objectMapper Jackson ObjectMapper for serialization
     * @param channelName Redis channel name
     * @param instanceId Unique instance identifier
     * @param metrics Metrics collector
     */
    public RedisPubSubManager(
            JedisPool jedisPool,
            ObjectMapper objectMapper,
            String channelName,
            String instanceId,
            MetricsCollector metrics) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.channelName = channelName;
        this.instanceId = instanceId;
        this.metrics = metrics;
        this.blockingExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "redis-pubsub-blocking-" + channelName);
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start pub/sub subscriber
     *
     * <p>Starts background thread to listen for invalidation messages.
     * Must be called before subscribing listeners.
     *
     * @return Promise that completes when subscriber started
     */
    public Promise<Void> start() {
        return Promise.ofBlocking(blockingExecutor, () -> {
            if (isRunning.compareAndSet(false, true)) {
                logger.info("[RedisPubSub] Starting subscriber for channel: {}", channelName);
                
                subscriberExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "redis-pubsub-" + channelName);
                    t.setDaemon(true);
                    return t;
                });
                
                pubSubHandler = new JedisPubSubHandler();
                
                // Start subscriber in background thread
                subscriberExecutor.submit(() -> {
                    while (isRunning.get()) {
                        try (Jedis jedis = jedisPool.getResource()) {
                            logger.info("[RedisPubSub] Subscribing to channel: {}", channelName);
                            jedis.subscribe(pubSubHandler, channelName);
                        } catch (Exception e) {
                            if (isRunning.get()) {
                                logger.error("[RedisPubSub] Subscriber error, reconnecting...", e);
                                errorCount.incrementAndGet();
                                metrics.incrementCounter("cache.pubsub.errors", 
                                    "channel", channelName,
                                    "type", "connection");
                                
                                try {
                                    Thread.sleep(5000); // Backoff before reconnect
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    }
                });
                
                logger.info("[RedisPubSub] Subscriber started for channel: {}", channelName);
            }
            return null;
        });
    }
    
    /**
     * Stop pub/sub subscriber
     *
     * <p>Stops background thread and unsubscribes from channel.
     *
     * @return Promise that completes when subscriber stopped
     */
    public Promise<Void> stop() {
        return Promise.ofBlocking(blockingExecutor, () -> {
            if (isRunning.compareAndSet(true, false)) {
                logger.info("[RedisPubSub] Stopping subscriber for channel: {}", channelName);
                
                if (pubSubHandler != null) {
                    pubSubHandler.unsubscribe();
                }
                
                if (subscriberExecutor != null) {
                    subscriberExecutor.shutdown();
                }
                
                logger.info("[RedisPubSub] Subscriber stopped for channel: {}", channelName);
            }
            return null;
        });
    }
    
    /**
     * Subscribe to cache invalidation events
     *
     * @param listener Listener to invoke on messages
     */
    public void subscribe(CacheInvalidationListener listener) {
        listeners.add(listener);
        logger.debug("[RedisPubSub] Listener subscribed, total: {}", listeners.size());
    }
    
    /**
     * Unsubscribe listener
     *
     * @param listener Listener to remove
     */
    public void unsubscribe(CacheInvalidationListener listener) {
        listeners.remove(listener);
        logger.debug("[RedisPubSub] Listener unsubscribed, total: {}", listeners.size());
    }
    
    /**
     * Publish cache invalidation message
     *
     * <p>Broadcasts message to all subscribers on the channel.
     * Does not invoke local listeners (they receive via pub/sub).
     *
     * @param message Cache invalidation message
     * @return Promise that completes when message published
     */
    public Promise<Void> publish(CacheInvalidationMessage message) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            try {
                String json = objectMapper.writeValueAsString(message);
                
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.publish(channelName, json);
                }
                
                publishCount.incrementAndGet();
                metrics.incrementCounter("cache.pubsub.published",
                    "channel", channelName,
                    "operation", message.getOperation().name());
                
                logger.debug("[RedisPubSub] Published message: {}", message);
                
            } catch (Exception e) {
                errorCount.incrementAndGet();
                metrics.incrementCounter("cache.pubsub.errors",
                    "channel", channelName,
                    "type", "publish");
                
                logger.error("[RedisPubSub] Failed to publish message: {}", message, e);
                throw new CachePubSubException("Failed to publish invalidation message", e);
            }
            return null;
        });
    }
    
    /**
     * Get pub/sub statistics
     *
     * @return Statistics object
     */
    public PubSubStats getStats() {
        return new PubSubStats(
            publishCount.get(),
            receiveCount.get(),
            errorCount.get(),
            listeners.size(),
            isRunning.get()
        );
    }
    
    // ========================================================================
    // Internal JedisPubSub Handler
    // ========================================================================
    
    private class JedisPubSubHandler extends JedisPubSub {
        @Override
        public void onMessage(String channel, String message) {
            try {
                CacheInvalidationMessage invalidationMessage = objectMapper.readValue(
                    message,
                    CacheInvalidationMessage.class
                );
                
                // Skip if from same instance (already handled locally)
                if (instanceId.equals(invalidationMessage.getSourceInstance())) {
                    logger.trace("[RedisPubSub] Skipping own message: {}", invalidationMessage);
                    return;
                }
                
                receiveCount.incrementAndGet();
                metrics.incrementCounter("cache.pubsub.received",
                    "channel", channelName,
                    "operation", invalidationMessage.getOperation().name());
                
                logger.debug("[RedisPubSub] Received message: {}", invalidationMessage);
                
                // Notify all listeners
                for (CacheInvalidationListener listener : listeners) {
                    try {
                        listener.onInvalidation(invalidationMessage);
                    } catch (Exception e) {
                        logger.error("[RedisPubSub] Listener error: {}", listener, e);
                        errorCount.incrementAndGet();
                        metrics.incrementCounter("cache.pubsub.errors",
                            "channel", channelName,
                            "type", "listener");
                    }
                }
                
            } catch (Exception e) {
                logger.error("[RedisPubSub] Failed to process message: {}", message, e);
                errorCount.incrementAndGet();
                metrics.incrementCounter("cache.pubsub.errors",
                    "channel", channelName,
                    "type", "deserialize");
            }
        }
    }
    
    // ========================================================================
    // Exception Class
    // ========================================================================
    
    /**
     * Exception thrown when pub/sub operation fails
     *
     * @doc.type exception
     * @doc.purpose Pub/sub operation error
     * @doc.layer core
     * @doc.pattern Exception
     */
    public static class CachePubSubException extends RuntimeException {
        public CachePubSubException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // ========================================================================
    // Statistics Class
    // ========================================================================
    
    /**
     * Pub/sub statistics
     *
     * @doc.type record
     * @doc.purpose Pub/sub metrics
     * @doc.layer core
     * @doc.pattern Value Object
     */
    public static class PubSubStats {
        private final long publishCount;
        private final long receiveCount;
        private final long errorCount;
        private final int listenerCount;
        private final boolean isRunning;
        
        public PubSubStats(long publishCount, long receiveCount, long errorCount,
                          int listenerCount, boolean isRunning) {
            this.publishCount = publishCount;
            this.receiveCount = receiveCount;
            this.errorCount = errorCount;
            this.listenerCount = listenerCount;
            this.isRunning = isRunning;
        }
        
        public long getPublishCount() { return publishCount; }
        public long getReceiveCount() { return receiveCount; }
        public long getErrorCount() { return errorCount; }
        public int getListenerCount() { return listenerCount; }
        public boolean isRunning() { return isRunning; }
        
        @Override
        public String toString() {
            return "PubSubStats{" +
                    "publishCount=" + publishCount +
                    ", receiveCount=" + receiveCount +
                    ", errorCount=" + errorCount +
                    ", listenerCount=" + listenerCount +
                    ", isRunning=" + isRunning +
                    '}';
        }
    }
}
