package com.ghatana.pipeline.registry.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ActiveJ module for PostgreSQL database configuration.
 *
 * <p>Purpose: Provides dependency injection bindings for HikariCP connection
 * pooling and Flyway database migrations. Configures optimal connection
 * pool settings for the Pipeline Registry service.</p>
 *
 * @doc.type class
 * @doc.purpose Configures PostgreSQL connection pooling and migrations
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 2.0.0
 */
public class PostgresConfig extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(PostgresConfig.class);
    
    private final Properties properties;
    
    public PostgresConfig(Properties properties) {
        this.properties = properties;
    }
    
    @Provides
    DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Required settings
        config.setJdbcUrl(properties.getProperty("db.url", "jdbc:postgresql://localhost:5432/pipeline_registry"));
        config.setUsername(properties.getProperty("db.username", "postgres"));
        config.setPassword(properties.getProperty("db.password", "postgres"));
        
        // Connection pool settings
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("db.pool.maxSize", "10")));
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("db.pool.minIdle", "2")));
        config.setConnectionTimeout(Long.parseLong(properties.getProperty("db.pool.connectionTimeout", "30000")));
        config.setIdleTimeout(Long.parseLong(properties.getProperty("db.pool.idleTimeout", "600000")));
        config.setMaxLifetime(Long.parseLong(properties.getProperty("db.pool.maxLifetime", "1800000")));
        
        // Connection test settings
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);
        
        // Performance settings
        config.addDataSourceProperty("applicationName", "pipeline-registry");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("socketTimeout", "30");
        
        LOG.info("Initializing database connection pool for: {}", 
                config.getJdbcUrl().replaceAll("//[^@]*@", "//***:***@"));
                
        return new HikariDataSource(config);
    }
    
    @Provides
    Executor jdbcExecutor() {
        int poolSize = Integer.parseInt(properties.getProperty("db.jdbc.poolSize", "10"));
        return Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "jdbc-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    @Provides
    Flyway flyway(DataSource dataSource) {
        LOG.info("Running database migrations...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
                
        flyway.migrate();
        LOG.info("Database migrations completed successfully");
        
        return flyway;
    }
}
