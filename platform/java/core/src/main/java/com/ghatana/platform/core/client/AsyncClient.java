package com.ghatana.platform.core.client;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for asynchronous client operations.
 * 
 * <p>Provides common patterns for async clients including lifecycle management,
 * health checks, and connection management. All operations return ActiveJ Promises
 * for non-blocking execution.</p>
 * 
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li><b>Non-blocking</b>: All operations return ActiveJ Promises</li>
 *   <li><b>Lifecycle-aware</b>: Explicit start/stop/close methods</li>
 *   <li><b>Health-checkable</b>: Built-in health check support</li>
 *   <li><b>Resource-managed</b>: Proper connection pooling and cleanup</li>
 * </ul>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * AsyncClient client = ...;
 * 
 * // Initialize and start
 * client.start()
 *     .then($ -> client.healthCheck())
 *     .then(healthy -> {
 *         if (healthy) {
 *             // Use client
 *         }
 *         return Promise.complete();
 *     })
 *     .whenComplete(() -> client.close());
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Base interface for async client operations with lifecycle management
 * @doc.layer core
 * @doc.pattern Client
 * 
 * @see io.activej.promise.Promise
 * @since 1.0.0
 */
public interface AsyncClient extends AutoCloseable {

    /**
     * Start the client and initialize connections.
     * 
     * <p>This method should be called before using the client. It initializes
     * connection pools, establishes connections, and prepares the client for use.</p>
     * 
     * @return Promise that completes when the client is ready
     */
    @NotNull
    Promise<Void> start();

    /**
     * Stop the client and release resources.
     * 
     * <p>This method gracefully shuts down the client, closing connections and
     * releasing resources. The client should not be used after calling stop.</p>
     * 
     * @return Promise that completes when the client is stopped
     */
    @NotNull
    Promise<Void> stop();

    /**
     * Check if the client is healthy and ready to use.
     * 
     * <p>Returns true if the client is properly initialized, connected, and
     * ready to handle requests. This can be used for health checks and
     * readiness probes.</p>
     * 
     * @return Promise resolving to true if healthy, false otherwise
     */
    @NotNull
    default Promise<Boolean> healthCheck() {
        return Promise.of(true);
    }

    /**
     * Check if the client is currently running.
     * 
     * <p>Returns true if the client has been started and not yet stopped.</p>
     * 
     * @return true if the client is running
     */
    default boolean isRunning() {
        return true;
    }

    /**
     * Close the client and release all resources.
     * 
     * <p>This method implements AutoCloseable for use in try-with-resources.
     * It delegates to stop() for graceful shutdown.</p>
     */
    @Override
    default void close() {
        stop();
    }
}
