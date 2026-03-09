package com.ghatana.pipeline.registry.config;

import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.database.routing.ReplicaLagMonitor;
import com.ghatana.platform.database.routing.RoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.config.Config;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.activej.config.converter.ConfigConverters.ofInteger;
import static io.activej.config.converter.ConfigConverters.ofString;

/**
 * ActiveJ module for database read replica configuration.
 *
 * <p>Purpose: Configures read replica support with automatic routing,
 * replica lag monitoring, and circuit breaker patterns. Enables horizontal
 * scaling of read operations through intelligent query routing.</p>
 *
 * @doc.type class
 * @doc.purpose Configures database read replicas with lag monitoring
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 2.0.0
 */
public class ReadReplicaConfig extends AbstractModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(ReadReplicaConfig.class);
    
    @Provides
    DataSource routingDataSource(Config config, MetricsRegistry metricsRegistry) {
        // Create primary data source
        DataSource primaryDataSource = createPrimaryDataSource(config);
        
        // Create replica data sources
        Map<String, DataSource> replicaDataSources = createReplicaDataSources(config);
        
        // Create routing data source
        long circuitBreakerTimeout = config.get(ofInteger(), "db.replica.circuit_breaker.timeout_ms", 60000);
        RoutingDataSource routingDataSource = new RoutingDataSource(primaryDataSource, replicaDataSources, circuitBreakerTimeout);
        
        // Create replica lag monitor
        if (!replicaDataSources.isEmpty()) {
            long lagThresholdBytes = config.get(ofInteger(), "db.replica.lag.threshold_bytes", 10_000_000); // 10MB default
            ReplicaLagMonitor lagMonitor = new ReplicaLagMonitor(
                routingDataSource,
                primaryDataSource,
                replicaDataSources,
                metricsRegistry,
                lagThresholdBytes
            );
            
            // Start monitoring
            long initialDelay = config.get(ofInteger(), "db.replica.lag.check.initial_delay_seconds", 10);
            long period = config.get(ofInteger(), "db.replica.lag.check.period_seconds", 30);
            lagMonitor.start(initialDelay, period, TimeUnit.SECONDS);
            
            LOG.info("Replica lag monitor started with threshold: {} bytes, period: {} seconds", 
                      lagThresholdBytes, period);
        }
        
        return routingDataSource;
    }
    
    /**
     * Create the primary data source.
     */
    private DataSource createPrimaryDataSource(Config config) {
        String host = config.get(ofString(), "db.primary.host", "localhost");
        int port = config.get(ofInteger(), "db.primary.port", 5432);
        String db = config.get(ofString(), "db.name", "pipeline_registry");
        String user = config.get(ofString(), "db.user", "postgres");
        String pass = config.get(ofString(), "db.password", "postgres");
        int maxPool = config.get(ofInteger(), "db.primary.pool.max.size", 10);
        int minIdle = config.get(ofInteger(), "db.primary.pool.min.idle", 2);
        long connectionTimeout = config.get(ofInteger(), "db.primary.connection.timeout.ms", 30000);
        long idleTimeout = config.get(ofInteger(), "db.primary.idle.timeout.ms", 600000);
        long maxLifetime = config.get(ofInteger(), "db.primary.max.lifetime.ms", 1800000);

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, db);
        LOG.info("Configuring primary HikariCP DataSource for {}", jdbcUrl);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(connectionTimeout);
        hc.setIdleTimeout(idleTimeout);
        hc.setMaxLifetime(maxLifetime);
        hc.setPoolName("pipeline-registry-primary-pool");
        hc.setAutoCommit(true);
        
        // Enhanced configuration for production
        hc.setLeakDetectionThreshold(60000); // 1 minute
        hc.setConnectionTestQuery("SELECT 1");
        hc.setValidationTimeout(5000);
        
        // Connection pool properties for better performance
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hc.addDataSourceProperty("useServerPrepStmts", "true");
        hc.addDataSourceProperty("useLocalSessionState", "true");
        hc.addDataSourceProperty("rewriteBatchedStatements", "true");
        hc.addDataSourceProperty("cacheResultSetMetadata", "true");
        hc.addDataSourceProperty("cacheServerConfiguration", "true");
        hc.addDataSourceProperty("elideSetAutoCommits", "true");
        hc.addDataSourceProperty("maintainTimeStats", "false");

        return new HikariDataSource(hc);
    }
    
    /**
     * Create replica data sources.
     */
    private Map<String, DataSource> createReplicaDataSources(Config config) {
        Map<String, DataSource> replicas = new HashMap<>();
        
        // Check if replicas are enabled
        boolean replicasEnabled = config.get(ofString(), "db.replica.enabled", "false").equalsIgnoreCase("true");
        if (!replicasEnabled) {
            LOG.info("Read replicas are disabled");
            return replicas;
        }
        
        // Get replica count
        int replicaCount = config.get(ofInteger(), "db.replica.count", 0);
        if (replicaCount <= 0) {
            LOG.info("No read replicas configured");
            return replicas;
        }
        
        // Create data sources for each replica
        for (int i = 1; i <= replicaCount; i++) {
            String replicaName = "replica-" + i;
            String host = config.get(ofString(), "db.replica." + i + ".host", "localhost");
            int port = config.get(ofInteger(), "db.replica." + i + ".port", 5432);
            String db = config.get(ofString(), "db.name", "pipeline_registry");
            String user = config.get(ofString(), "db.user", "postgres");
            String pass = config.get(ofString(), "db.password", "postgres");
            int maxPool = config.get(ofInteger(), "db.replica.pool.max.size", 5);
            int minIdle = config.get(ofInteger(), "db.replica.pool.min.idle", 1);
            
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, db);
            LOG.info("Configuring replica HikariCP DataSource for {}: {}", replicaName, jdbcUrl);
            
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(jdbcUrl);
            hc.setUsername(user);
            hc.setPassword(pass);
            hc.setMaximumPoolSize(maxPool);
            hc.setMinimumIdle(minIdle);
            hc.setConnectionTimeout(30000);
            hc.setIdleTimeout(300000);
            hc.setMaxLifetime(900000);
            hc.setPoolName("pipeline-registry-" + replicaName + "-pool");
            hc.setAutoCommit(true);
            hc.setReadOnly(true); // Important: replicas are read-only
            
            // Enhanced configuration for production
            hc.setLeakDetectionThreshold(60000); // 1 minute
            hc.setConnectionTestQuery("SELECT 1");
            hc.setValidationTimeout(5000);
            
            // Connection pool properties for better performance
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            replicas.put(replicaName, new HikariDataSource(hc));
        }
        
        LOG.info("Configured {} read replicas", replicas.size());
        return replicas;
    }
    
}
