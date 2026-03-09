package com.ghatana.platform.database.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routing data source for read/write splitting.
 * Routes read queries to replicas and write queries to the primary.
 *
 * <p>Implements the DataSource interface with intelligent routing based on ThreadLocal
 * transaction context. Read-only transactions are load-balanced across available replicas
 * using round-robin; write transactions always use the primary. Includes circuit breaker
 * pattern to handle replica failures.
 * 
 * @doc.type class
 * @doc.purpose DataSource router for read/write splitting with replica load-balancing
 * @doc.layer core
 * @doc.pattern Adapter, Load Balancer
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>ThreadLocal-based routing (read-only vs. read-write)</li>
 *   <li>Round-robin load balancing across replicas</li>
 *   <li>Circuit breaker per replica (auto-recovery after timeout)</li>
 *   <li>Automatic fallback to primary when no replicas available</li>
 *   <li>Connection pool delegation to underlying data sources</li>
 *   <li>Integration with {@link ObservabilityTransactionManager}</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Setup routing data source
 * DataSource primary = createPrimaryDataSource();
 * Map<String, DataSource> replicas = Map.of(
 *     "replica-1", createReplicaDataSource("replica-1"),
 *     "replica-2", createReplicaDataSource("replica-2")
 * );
 * 
 * RoutingDataSource routingDS = new RoutingDataSource(
 *     primary, replicas, 60_000 // 1 minute circuit breaker timeout
 * );
 * 
 * // Set read-only context (typically done by TransactionManager)
 * RoutingDataSource.setReadOnly(true);
 * try (Connection conn = routingDS.getConnection()) {
 *     // This connection comes from a replica (round-robin)
 *     executeReadQuery(conn);
 * } finally {
 *     RoutingDataSource.clearReadOnly();
 * }
 * 
 * // Write transaction (no need to set context)
 * try (Connection conn = routingDS.getConnection()) {
 *     // This connection comes from primary
 *     executeWriteQuery(conn);
 * }
 * 
 * // Circuit breaker
 * routingDS.markReplicaUnavailable("replica-1"); // Manual circuit open
 * // After timeout, replica-1 will be retried automatically
 * }</pre>
 *
 * <h2>Routing Decision Flow:</h2>
 * <pre>
 * getConnection() called
 *   → Check ThreadLocal: isReadOnly()?
 *     → NO: Return primary DataSource
 *     → YES: Check available replicas
 *       → No replicas? Return primary DataSource
 *       → Round-robin select from available replicas
 *       → Check circuit breaker
 *         → Open (failed recently)? Skip, try next
 *         → Closed or timeout elapsed? Return replica DataSource
 * </pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. ThreadLocal ensures per-thread routing context. ConcurrentHashMap
 * for replica availability state.
 *
 * <h2>Circuit Breaker:</h2>
 * - Replica marked unavailable → circuit OPEN
 * - Circuit remains OPEN for {@code circuitBreakerResetTimeoutMs}
 * - After timeout → circuit HALF-OPEN (retry)
 * - Successful query → circuit CLOSED (available)
 * - Failed query → circuit OPEN again
 *
 * <h2>Performance Considerations:</h2>
 * - Routing decision is O(1) (ThreadLocal check + map lookup)
 * - Round-robin has no synchronization overhead (atomic counter)
 * - Circuit breaker check is O(1) (atomic boolean + timestamp)
 *
 * @since 1.0.0
 */
public class RoutingDataSource implements DataSource {
    
    private static final Logger logger = LoggerFactory.getLogger(RoutingDataSource.class);
    
    // Transaction context for determining read/write intent
    private static final ThreadLocal<Boolean> READ_ONLY_CONTEXT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    
    // Data sources
    private final DataSource primaryDataSource;
    private final Map<String, DataSource> replicaDataSources = new ConcurrentHashMap<>();
    private final AtomicInteger nextReplicaIndex = new AtomicInteger(0);
    
    // Circuit breaker state
    private final Map<String, AtomicBoolean> replicaAvailability = new ConcurrentHashMap<>();
    private final Map<String, Long> replicaFailureTimestamps = new ConcurrentHashMap<>();
    private final long circuitBreakerResetTimeoutMs;
    
    /**
     * Create a new RoutingDataSource with a primary and replicas.
     */
    public RoutingDataSource(DataSource primaryDataSource, Map<String, DataSource> replicaDataSources, 
                          long circuitBreakerResetTimeoutMs) {
        this.primaryDataSource = primaryDataSource;
        
        if (replicaDataSources != null) {
            this.replicaDataSources.putAll(replicaDataSources);
            
            // Initialize circuit breaker state
            replicaDataSources.keySet().forEach(name -> {
                replicaAvailability.put(name, new AtomicBoolean(true));
                replicaFailureTimestamps.put(name, 0L);
            });
        }
        
        this.circuitBreakerResetTimeoutMs = circuitBreakerResetTimeoutMs;
        
        logger.info("RoutingDataSource initialized with primary and {} replicas", this.replicaDataSources.size());
    }
    
    /**
     * Create a new RoutingDataSource with a primary and replicas.
     */
    public RoutingDataSource(DataSource primaryDataSource, Map<String, DataSource> replicaDataSources) {
        this(primaryDataSource, replicaDataSources, 60000); // Default 1 minute timeout
    }
    
    /**
     * Set the current thread's transaction context to read-only.
     */
    public static void setReadOnly(boolean readOnly) {
        READ_ONLY_CONTEXT.set(readOnly);
    }
    
    /**
     * Clear the current thread's transaction context.
     */
    public static void clearReadOnly() {
        READ_ONLY_CONTEXT.remove();
    }
    
    /**
     * Check if the current thread's transaction context is read-only.
     */
    public static boolean isReadOnly() {
        return READ_ONLY_CONTEXT.get();
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return determineTargetDataSource().getConnection();
    }
    
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return determineTargetDataSource().getConnection(username, password);
    }
    
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return primaryDataSource.getLogWriter();
    }
    
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        primaryDataSource.setLogWriter(out);
    }
    
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        primaryDataSource.setLoginTimeout(seconds);
    }
    
    @Override
    public int getLoginTimeout() throws SQLException {
        return primaryDataSource.getLoginTimeout();
    }
    
    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return primaryDataSource.getParentLogger();
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return primaryDataSource.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || primaryDataSource.isWrapperFor(iface);
    }
    
    /**
     * Determine the target data source based on read/write context.
     */
    protected DataSource determineTargetDataSource() {
        if (isReadOnly() && !replicaDataSources.isEmpty()) {
            return determineReplicaDataSource();
        }
        return primaryDataSource;
    }
    
    /**
     * Determine the replica data source to use.
     */
    protected DataSource determineReplicaDataSource() {
        // Get available replicas
        Map<String, DataSource> availableReplicas = new HashMap<>();
        replicaDataSources.forEach((name, dataSource) -> {
            // Check if replica is available or if circuit breaker timeout has elapsed
            if (isReplicaAvailable(name)) {
                availableReplicas.put(name, dataSource);
            }
        });
        
        // If no replicas are available, use primary
        if (availableReplicas.isEmpty()) {
            logger.warn("No replica data sources available, using primary");
            return primaryDataSource;
        }
        
        // Round-robin selection among available replicas
        String[] replicaNames = availableReplicas.keySet().toArray(new String[0]);
        int index = nextReplicaIndex.getAndIncrement() % replicaNames.length;
        String selectedReplica = replicaNames[index];
        
        logger.debug("Selected replica: {}", selectedReplica);
        return availableReplicas.get(selectedReplica);
    }
    
    /**
     * Check if a replica is available.
     */
    protected boolean isReplicaAvailable(String name) {
        AtomicBoolean available = replicaAvailability.get(name);
        if (available.get()) {
            return true;
        }
        
        // Check if circuit breaker timeout has elapsed
        long failureTimestamp = replicaFailureTimestamps.get(name);
        long now = System.currentTimeMillis();
        if (now - failureTimestamp > circuitBreakerResetTimeoutMs) {
            // Reset circuit breaker
            available.set(true);
            logger.info("Circuit breaker reset for replica: {}", name);
            return true;
        }
        
        return false;
    }
    
    /**
     * Mark a replica as unavailable.
     */
    public void markReplicaUnavailable(String name) {
        AtomicBoolean available = replicaAvailability.get(name);
        if (available != null && available.compareAndSet(true, false)) {
            replicaFailureTimestamps.put(name, System.currentTimeMillis());
            logger.warn("Marked replica as unavailable: {}", name);
        }
    }
    
    /**
     * Mark a replica as available.
     */
    public void markReplicaAvailable(String name) {
        AtomicBoolean available = replicaAvailability.get(name);
        if (available != null && available.compareAndSet(false, true)) {
            logger.info("Marked replica as available: {}", name);
        }
    }
    
    /**
     * Get the primary data source.
     */
    public DataSource getPrimaryDataSource() {
        return primaryDataSource;
    }
    
    /**
     * Get the replica data sources.
     */
    public Map<String, DataSource> getReplicaDataSources() {
        return new HashMap<>(replicaDataSources);
    }
    
    /**
     * Get the availability status of all replicas.
     */
    public Map<String, Boolean> getReplicaAvailabilityStatus() {
        Map<String, Boolean> status = new HashMap<>();
        replicaAvailability.forEach((name, available) -> status.put(name, available.get()));
        return status;
    }
}
