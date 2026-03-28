package com.ghatana.platform.core.client;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Interface for managing client connections and connection pooling.
 * 
 * <p>Provides common connection management patterns including connection pooling,
 * health monitoring, and resource cleanup. Implementations should handle connection
 * lifecycle, pool sizing, and connection validation.</p>
 * 
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li><b>Pooled</b>: Efficient connection reuse</li>
 *   <li><b>Monitored</b>: Connection health tracking</li>
 *   <li><b>Configurable</b>: Pool size and timeout configuration</li>
 *   <li><b>Thread-safe</b>: Concurrent access support</li>
 * </ul>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * ConnectionManager<Connection> manager = ...;
 * 
 * // Get connection from pool
 * manager.getConnection()
 *     .then(connection -> {
 *         // Use connection
 *         return doWork(connection)
 *             .whenComplete(() -> manager.releaseConnection(connection));
 *     });
 * }</pre>
 *
 * @param <T> the connection type
 * @doc.type interface
 * @doc.purpose Connection pooling and lifecycle management
 * @doc.layer core
 * @doc.pattern Connection Pool
 * 
 * @since 1.0.0
 */
public interface ConnectionManager<T> {

    /**
     * Get a connection from the pool.
     * 
     * <p>Returns a connection from the pool, creating a new one if necessary.
     * The connection should be released back to the pool after use.</p>
     * 
     * @return Promise resolving to a connection
     */
    @NotNull
    Promise<T> getConnection();

    /**
     * Get a connection from the pool with timeout.
     * 
     * <p>Returns a connection from the pool, creating a new one if necessary.
     * If no connection is available within the timeout, the promise fails.</p>
     * 
     * @param timeout maximum time to wait for a connection
     * @return Promise resolving to a connection
     */
    @NotNull
    default Promise<T> getConnection(@NotNull Duration timeout) {
        return getConnection();
    }

    /**
     * Release a connection back to the pool.
     * 
     * <p>Returns the connection to the pool for reuse. The connection should
     * not be used after calling this method.</p>
     * 
     * @param connection the connection to release
     * @return Promise that completes when the connection is released
     */
    @NotNull
    Promise<Void> releaseConnection(@NotNull T connection);

    /**
     * Validate a connection.
     * 
     * <p>Checks if the connection is still valid and usable. Invalid connections
     * should be discarded and not returned to the pool.</p>
     * 
     * @param connection the connection to validate
     * @return Promise resolving to true if valid, false otherwise
     */
    @NotNull
    default Promise<Boolean> validateConnection(@NotNull T connection) {
        return Promise.of(true);
    }

    /**
     * Get the number of active connections.
     * 
     * @return the number of connections currently in use
     */
    default int getActiveConnections() {
        return 0;
    }

    /**
     * Get the number of idle connections.
     * 
     * @return the number of connections available in the pool
     */
    default int getIdleConnections() {
        return 0;
    }

    /**
     * Get the total number of connections.
     * 
     * @return the total number of connections (active + idle)
     */
    default int getTotalConnections() {
        return getActiveConnections() + getIdleConnections();
    }

    /**
     * Close all connections and shutdown the pool.
     * 
     * @return Promise that completes when all connections are closed
     */
    @NotNull
    Promise<Void> shutdown();
}
