/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.config.Config;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence Module configuration.
 *
 * <p>Provides JDBC DataSource via HikariCP connection pool.
 *
 * @doc.type class
 * @doc.purpose Database connection configuration
 * @doc.layer infrastructure
 
 * @doc.pattern Configuration
*/
public class DataSourceModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceModule.class);

    @Provides
    DataSource dataSource(Config config) {
        logger.info("Initializing DataSource...");
        HikariConfig hikariConfig = new HikariConfig();
        
        // Basic Connection
        hikariConfig.setDriverClassName(config.get("jdbc.driver", "org.postgresql.Driver"));
        hikariConfig.setJdbcUrl(config.get("jdbc.url", "jdbc:postgresql://localhost:5432/yappc"));
        hikariConfig.setUsername(config.get("jdbc.username", "ghatana"));
        hikariConfig.setPassword(config.get("jdbc.password", "ghatana123"));
        
        // Pool Settings
        hikariConfig.setMaximumPoolSize(Integer.parseInt(config.get("jdbc.pool.maximumPoolSize", "10")));
        hikariConfig.setMinimumIdle(Integer.parseInt(config.get("jdbc.pool.minimumIdle", "2")));
        hikariConfig.setIdleTimeout(Long.parseLong(config.get("jdbc.pool.idleTimeout", "30000")));
        hikariConfig.setConnectionTimeout(Long.parseLong(config.get("jdbc.pool.connectionTimeout", "20000")));
        hikariConfig.setMaxLifetime(Long.parseLong(config.get("jdbc.pool.maxLifetime", "1800000")));

        return new HikariDataSource(hikariConfig);
    }
}
