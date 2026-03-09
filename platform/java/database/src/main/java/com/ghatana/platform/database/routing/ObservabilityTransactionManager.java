package com.ghatana.platform.database.routing;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Transaction manager for database operations.
 * Supports read-only and read-write transactions with routing data source integration.
 *
 * <p>Provides Promise-based transaction management with automatic routing to primary/replica
 * databases via {@link RoutingDataSource}. Handles connection lifecycle, commit/rollback,
 * and cleanup automatically.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Read-only transactions route to replicas (via RoutingDataSource)</li>
 *   <li>Read-write transactions route to primary</li>
 *   <li>Automatic commit on success, rollback on error</li>
 *   <li>Connection pool management (acquire/release)</li>
 * @doc.type class
 * @doc.purpose Promise-based transaction manager with primary/replica routing via RoutingDataSource
 * @doc.layer core
 * @doc.pattern Transaction Manager, Adapter
 *   <li>ThreadLocal context for read/write intent</li>
 *   <li>Promise-based async API</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * ObservabilityTransactionManager txManager = new ObservabilityTransactionManager(dataSource);
 * 
 * // Read-only transaction (routes to replica)
 * txManager.executeReadOnly(conn -> {
 *     try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users")) {
 *         ResultSet rs = stmt.executeQuery();
 *         return Promise.of(mapResultSet(rs));
 *     }
 * }).whenResult(users -> logger.info("Found {} users", users.size()));
 * 
 * // Read-write transaction (routes to primary)
 * txManager.executeReadWrite(conn -> {
 *     try (PreparedStatement stmt = conn.prepareStatement(
 *         "INSERT INTO users (name, email) VALUES (?, ?)")) {
 *         stmt.setString(1, "Alice");
 *         stmt.setString(2, "alice@example.com");
 *         stmt.executeUpdate();
 *         return Promise.of(1);
 *     }
 * }).whenResult(count -> logger.info("Inserted {} rows", count));
 * 
 * // Batch operation
 * txManager.executeBatch(conn -> {
 *     try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO events ...")) {
 *         for (Event e : events) {
 *             stmt.setString(1, e.getId());
 *             stmt.addBatch();
 *         }
 *         int[] counts = stmt.executeBatch();
 *         return Promise.of(counts.length);
 *     }
 * });
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. Each transaction operates in isolation with its own connection.
 * ThreadLocal context ensures routing decisions are per-thread.
 *
 * <h2>Transaction Lifecycle:</h2>
 * <pre>
 * Read-Only:
 *   1. Set ThreadLocal (READ_ONLY = true)
 *   2. Acquire connection from pool (routed to replica)
 *   3. setReadOnly(true)
 *   4. Execute operation
 *   5. Close connection
 *   6. Clear ThreadLocal
 * 
 * Read-Write:
 *   1. Set ThreadLocal (READ_ONLY = false)
 *   2. Acquire connection (routed to primary)
 *   3. setAutoCommit(false)
 *   4. Execute operation
 *   5. Commit (or rollback on error)
 *   6. Restore autoCommit, close connection
 *   7. Clear ThreadLocal
 * </pre>
 *
 * <h2>Error Handling:</h2>
 * - All exceptions are wrapped in RuntimeException
 * - Automatic rollback on error (read-write only)
 * - Connections always released back to pool
 * - ThreadLocal always cleared (prevents context leakage)
 *
 * @since 1.0.0
 */
public class ObservabilityTransactionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ObservabilityTransactionManager.class);
    
    private final DataSource dataSource;
    
    /**
     * Create a new TransactionManager with the specified data source.
     */
    public ObservabilityTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Execute a read-only transaction.
     */
    public <T> Promise<T> executeReadOnly(Function<Connection, Promise<T>> operation) {
        RoutingDataSource.setReadOnly(true);
        return Promise.ofCallback(cb -> {
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                connection.setReadOnly(true);
                final Connection c = connection;
                operation.apply(c)
                        .whenResult(res -> {
                            closeQuietly(c);
                            RoutingDataSource.clearReadOnly();
                            cb.accept(res, null);
                        })
                        .whenException(e -> {
                            closeQuietly(c);
                            RoutingDataSource.clearReadOnly();
                            logger.error("Error executing read-only transaction", e);
                            cb.accept(null, new RuntimeException("Error executing read-only transaction", e));
                        });
            } catch (Exception e) {
                closeQuietly(connection);
                RoutingDataSource.clearReadOnly();
                logger.error("Error acquiring connection for read-only transaction", e);
                cb.accept(null, new RuntimeException("Error executing read-only transaction", e));
            }
        });
    }
    
    /**
     * Execute a read-write transaction.
     */
    public <T> Promise<T> executeReadWrite(Function<Connection, Promise<T>> operation) {
        RoutingDataSource.setReadOnly(false);
        return Promise.ofCallback(cb -> {
            Connection connection = null;
            boolean originalAutoCommit = true;
            try {
                connection = dataSource.getConnection();
                originalAutoCommit = connection.getAutoCommit();
                connection.setReadOnly(false);
                connection.setAutoCommit(false);

                final Connection c = connection;
                final boolean orig = originalAutoCommit;
                operation.apply(c)
                        .whenResult(res -> {
                            try {
                                c.commit();
                            } catch (SQLException e) {
                                logger.error("Commit failed", e);
                                cb.accept(null, new RuntimeException("Commit failed", e));
                                closeFinally(c, orig);
                                RoutingDataSource.clearReadOnly();
                                return;
                            }
                            closeFinally(c, orig);
                            RoutingDataSource.clearReadOnly();
                            cb.accept(res, null);
                        })
                        .whenException(e -> {
                            tryRollback(c);
                            closeFinally(c, orig);
                            RoutingDataSource.clearReadOnly();
                            logger.error("Error executing read-write transaction", e);
                            cb.accept(null, new RuntimeException("Error executing read-write transaction", e));
                        });
            } catch (Exception e) {
                tryRollback(connection);
                closeFinally(connection, originalAutoCommit);
                RoutingDataSource.clearReadOnly();
                logger.error("Error acquiring connection for read-write transaction", e);
                cb.accept(null, new RuntimeException("Error executing read-write transaction", e));
            }
        });
    }
    
    /**
     * Execute a batch operation in a transaction.
     */
    public <T> Promise<T> executeBatch(Function<Connection, Promise<T>> operation) {
        // Batch operations are always read-write
        return executeReadWrite(operation);
    }
    
    /**
     * Get the underlying data source.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    private static void tryRollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                // log and ignore
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private static void closeFinally(Connection connection, boolean originalAutoCommit) {
        if (connection != null) {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
            closeQuietly(connection);
        }
    }
}
