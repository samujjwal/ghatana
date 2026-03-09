package com.ghatana.platform.database.routing;

import com.ghatana.platform.observability.MetricsRegistry;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitor for PostgreSQL replication lag.
 * Tracks lag between primary and replicas and provides metrics.
 *
 * <p>Continuously monitors PostgreSQL replication lag (in bytes) between primary and
 * replica databases using WAL LSN (Log Sequence Number) comparison. Automatically marks
 * @doc.type class
 * @doc.purpose Monitor PostgreSQL replication lag between primary and replicas
 * @doc.layer core
 * @doc.pattern Monitor, Metrics Collector
 * replicas as unavailable when lag exceeds threshold, integrating with {@link RoutingDataSource}
 * for circuit breaker behavior.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Periodic lag monitoring via scheduled executor</li>
 *   <li>PostgreSQL-specific LSN queries (pg_current_wal_lsn, pg_last_wal_replay_lsn)</li>
 *   <li>Byte-level lag calculation (pg_wal_lsn_diff)</li>
 *   <li>Micrometer Gauge metrics per replica</li>
 *   <li>Circuit breaker integration (mark replica unavailable if lag > threshold)</li>
 *   <li>Daemon thread for monitoring</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create monitor with 10MB threshold
 * ReplicaLagMonitor monitor = new ReplicaLagMonitor(
 *     routingDataSource,
 *     primaryDataSource,
 *     Map.of("replica-1", replica1DataSource, "replica-2", replica2DataSource),
 *     metricsRegistry,
 *     10 * 1024 * 1024 // 10 MB threshold
 * );
 * 
 * // Start monitoring every 5 seconds
 * monitor.start(0, 5, TimeUnit.SECONDS);
 * 
 * // Check lag for specific replica
 * long lag = monitor.getReplicaLag("replica-1");
 * logger.info("Replica-1 lag: {} bytes", lag);
 * 
 * // Shutdown
 * monitor.stop();
 * }</pre>
 *
 * <h2>PostgreSQL Requirements:</h2>
 * - Primary must be streaming replication master
 * - Replicas must be in recovery mode
 * - User must have permission to query LSN functions
 *
 * <h2>Lag Calculation:</h2>
 * <pre>
 * 1. Query primary: SELECT pg_current_wal_lsn()       → e.g., "0/3000000"
 * 2. Query replica: SELECT pg_last_wal_replay_lsn()   → e.g., "0/2F00000"
 * 3. Calculate diff: SELECT pg_wal_lsn_diff(primary, replica) → 1048576 bytes
 * 4. If lag > threshold: Mark replica unavailable
 * </pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. Uses concurrent data structures and daemon thread for monitoring.
 *
 * <h2>Performance Considerations:</h2>
 * - Monitoring period should balance freshness vs. database load (5-30 seconds typical)
 * - LSN queries are lightweight (no table scans)
 * - Threshold should account for workload (write-heavy: higher threshold)
 *
 * @since 1.0.0
 */
public class ReplicaLagMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(ReplicaLagMonitor.class);
    
    // SQL queries for replication lag
    private static final String PRIMARY_CURRENT_LSN_QUERY = "SELECT pg_current_wal_lsn()";
    private static final String REPLICA_RECEIVED_LSN_QUERY = "SELECT pg_last_wal_receive_lsn()";
    private static final String REPLICA_APPLIED_LSN_QUERY = "SELECT pg_last_wal_replay_lsn()";
    private static final String REPLICA_LAG_BYTES_QUERY = "SELECT pg_wal_lsn_diff(?, ?)";
    
    private final DataSource primaryDataSource;
    private final Map<String, DataSource> replicaDataSources;
    private final MetricsRegistry metricsRegistry;
    private final RoutingDataSource routingDataSource;
    private final Map<String, AtomicLong> replicaLags = new ConcurrentHashMap<>();
    private final long lagThresholdBytes;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Gauge> lagGauges = new ConcurrentHashMap<>();
    
    /**
     * Create a new ReplicaLagMonitor.
     */
    public ReplicaLagMonitor(RoutingDataSource routingDataSource, DataSource primaryDataSource, 
                          Map<String, DataSource> replicaDataSources, MetricsRegistry metricsRegistry,
                          long lagThresholdBytes) {
        this.routingDataSource = routingDataSource;
        this.primaryDataSource = primaryDataSource;
        this.replicaDataSources = replicaDataSources;
        this.metricsRegistry = metricsRegistry;
        this.lagThresholdBytes = lagThresholdBytes;
        
        // Initialize lag values
        replicaDataSources.keySet().forEach(name -> {
            replicaLags.put(name, new AtomicLong(0));
            
            // Register gauge for replica lag
            if (metricsRegistry != null) {
                lagGauges.put(name, Gauge.builder("db.replica.lag.bytes", () -> replicaLags.get(name).get())
                    .description("Replication lag in bytes")
                    .tag("replica", name)
                    .register(metricsRegistry.getMeterRegistry()));
            }
        });
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "replica-lag-monitor");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("ReplicaLagMonitor initialized with {} replicas", replicaDataSources.size());
    }
    
    /**
     * Start monitoring replication lag.
     */
    public void start(long initialDelay, long period, TimeUnit unit) {
        scheduler.scheduleAtFixedRate(this::checkReplicationLag, initialDelay, period, unit);
        logger.info("ReplicaLagMonitor started with period: {} {}", period, unit);
    }
    
    /**
     * Stop monitoring replication lag.
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        logger.info("ReplicaLagMonitor stopped");
    }
    
    /**
     * Check replication lag for all replicas.
     */
    protected void checkReplicationLag() {
        try {
            // Get primary LSN
            String primaryLsn = getPrimaryLsn();
            if (primaryLsn == null) {
                logger.warn("Failed to get primary LSN");
                return;
            }
            
            // Check each replica
            replicaDataSources.forEach((name, dataSource) -> {
                try {
                    // Get replica LSN
                    String replicaLsn = getReplicaAppliedLsn(dataSource);
                    if (replicaLsn == null) {
                        logger.warn("Failed to get LSN for replica: {}", name);
                        return;
                    }
                    
                    // Calculate lag
                    long lagBytes = calculateLagBytes(primaryLsn, replicaLsn, dataSource);
                    replicaLags.get(name).set(lagBytes);
                    
                    // Check lag threshold
                    if (lagBytes > lagThresholdBytes) {
                        logger.warn("Replica lag exceeds threshold: {} bytes for {}", lagBytes, name);
                        
                        // Mark replica as unavailable if lag is too high
                        if (routingDataSource != null) {
                            routingDataSource.markReplicaUnavailable(name);
                        }
                    } else {
                        // Mark replica as available if lag is acceptable
                        if (routingDataSource != null) {
                            routingDataSource.markReplicaAvailable(name);
                        }
                    }
                    
                    logger.debug("Replica lag for {}: {} bytes", name, lagBytes);
                    
                } catch (Exception e) {
                    logger.error("Error checking lag for replica: {}", name, e);
                    
                    // Mark replica as unavailable on error
                    if (routingDataSource != null) {
                        routingDataSource.markReplicaUnavailable(name);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error checking replication lag", e);
        }
    }
    
    /**
     * Get the current LSN from the primary.
     */
    protected String getPrimaryLsn() {
        try (Connection conn = primaryDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(PRIMARY_CURRENT_LSN_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getString(1);
            }
            
        } catch (SQLException e) {
            logger.error("Error getting primary LSN", e);
        }
        
        return null;
    }
    
    /**
     * Get the last applied LSN from a replica.
     */
    protected String getReplicaAppliedLsn(DataSource replicaDataSource) {
        try (Connection conn = replicaDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(REPLICA_APPLIED_LSN_QUERY);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getString(1);
            }
            
        } catch (SQLException e) {
            logger.error("Error getting replica applied LSN", e);
        }
        
        return null;
    }
    
    /**
     * Calculate the lag in bytes between primary and replica.
     */
    protected long calculateLagBytes(String primaryLsn, String replicaLsn, DataSource dataSource) {
        if (primaryLsn == null || replicaLsn == null) {
            return -1;
        }
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(REPLICA_LAG_BYTES_QUERY)) {
            
            stmt.setString(1, primaryLsn);
            stmt.setString(2, replicaLsn);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error calculating lag bytes", e);
        }
        
        return -1;
    }
    
    /**
     * Get the current lag for a replica.
     */
    public long getReplicaLag(String replicaName) {
        AtomicLong lag = replicaLags.get(replicaName);
        return lag != null ? lag.get() : -1;
    }
    
    /**
     * Get the lag for all replicas.
     */
    public Map<String, Long> getAllReplicaLags() {
        Map<String, Long> lags = new ConcurrentHashMap<>();
        replicaLags.forEach((name, lag) -> lags.put(name, lag.get()));
        return lags;
    }
}
