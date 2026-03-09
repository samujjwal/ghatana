package com.ghatana.platform.database.connection;

import com.ghatana.platform.core.util.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection pool wrapper using HikariCP.
 * 
 * Provides a simplified interface for database connection management.
 *
 * @doc.type class
 * @doc.purpose HikariCP connection pool wrapper for database connection management
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class ConnectionPool implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);
    
    private final HikariDataSource dataSource;
    private final DataSourceConfig config;
    
    private ConnectionPool(@NotNull DataSourceConfig config) {
        this.config = Preconditions.requireNonNull(config, "config");
        this.dataSource = createDataSource(config);
        log.info("Connection pool '{}' initialized with {} max connections", 
                config.poolName(), config.maximumPoolSize());
    }
    
    /**
     * Create a new connection pool with the given configuration.
     */
    public static ConnectionPool create(@NotNull DataSourceConfig config) {
        return new ConnectionPool(config);
    }
    
    /**
     * Get a connection from the pool.
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    /**
     * Get the underlying DataSource.
     */
    public DataSource getDataSource() {
        return dataSource;
    }
    
    /**
     * Get the configuration used to create this pool.
     */
    public DataSourceConfig getConfig() {
        return config;
    }
    
    /**
     * Get the number of active connections.
     */
    public int getActiveConnections() {
        return dataSource.getHikariPoolMXBean().getActiveConnections();
    }
    
    /**
     * Get the number of idle connections.
     */
    public int getIdleConnections() {
        return dataSource.getHikariPoolMXBean().getIdleConnections();
    }
    
    /**
     * Get the total number of connections.
     */
    public int getTotalConnections() {
        return dataSource.getHikariPoolMXBean().getTotalConnections();
    }
    
    /**
     * Check if the pool is running.
     */
    public boolean isRunning() {
        return dataSource.isRunning();
    }
    
    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            log.info("Closing connection pool '{}'", config.poolName());
            dataSource.close();
        }
    }
    
    private static HikariDataSource createDataSource(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setDriverClassName(config.driverClassName());
        
        hikariConfig.setMinimumIdle(config.minimumIdle());
        hikariConfig.setMaximumPoolSize(config.maximumPoolSize());
        hikariConfig.setConnectionTimeout(config.connectionTimeout().toMillis());
        hikariConfig.setIdleTimeout(config.idleTimeout().toMillis());
        hikariConfig.setMaxLifetime(config.maxLifetime().toMillis());
        hikariConfig.setPoolName(config.poolName());
        
        // Performance optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(hikariConfig);
    }
}
