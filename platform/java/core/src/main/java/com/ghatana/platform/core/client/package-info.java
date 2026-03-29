/**
 * Core client interfaces and utilities for async client implementations.
 * 
 * <p>This package provides base interfaces and patterns for building async clients
 * that interact with external services, databases, and APIs. All clients use ActiveJ
 * Promises for non-blocking operations.</p>
 * 
 * <h2>Key Interfaces</h2>
 * <ul>
 *   <li>{@link com.ghatana.platform.core.client.AsyncClient} - Base interface for async clients with lifecycle management</li>
 *   <li>{@link com.ghatana.platform.core.client.ConnectionManager} - Connection pooling and management</li>
 * </ul>
 * 
 * <h2>Design Patterns</h2>
 * <ul>
 *   <li><b>Lifecycle Management</b>: Explicit start/stop/close methods</li>
 *   <li><b>Connection Pooling</b>: Efficient resource reuse</li>
 *   <li><b>Health Monitoring</b>: Built-in health check support</li>
 *   <li><b>Non-blocking</b>: All operations return Promises</li>
 * </ul>
 * 
 * <h2>Usage Guidelines</h2>
 * <p>When implementing a new client:</p>
 * <ol>
 *   <li>Extend {@link com.ghatana.platform.core.client.AsyncClient} for lifecycle management</li>
 *   <li>Use {@link com.ghatana.platform.core.client.ConnectionManager} for connection pooling</li>
 *   <li>Implement health checks for monitoring</li>
 *   <li>Use try-with-resources for automatic cleanup</li>
 * </ol>
 * 
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * public class MyDatabaseClient extends ManagedAsyncClient {
 *     private final ConnectionManager<Connection> connectionManager;
 *     
 *     @Override
 *     public Promise<Void> start() {
 *         markStarted();
 *         return Promise.complete();
 *     }
 *     
 *     @Override
 *     public Promise<Void> stop() {
 *         markStopped();
 *         return connectionManager.shutdown();
 *     }
 *     
 *     public Promise<Result> query(String sql) {
 *         return connectionManager.getConnection()
 *             .then(conn -> executeQuery(conn, sql)
 *                 .whenComplete(() -> connectionManager.releaseConnection(conn)));
 *     }
 * }
 * }</pre>
 * 
 * @see com.ghatana.platform.core.client.AsyncClient
 * @see com.ghatana.platform.core.client.ConnectionManager
 * @since 1.0.0
 */
package com.ghatana.platform.core.client;
