/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.database.connection;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Factory for creating lifecycle-managed {@link DataSource} instances.
 *
 * <p>Returns standard {@code javax.sql.DataSource} so consumers are not coupled
 * to HikariCP. All pools created through this factory are tracked and can be
 * shut down together via {@link #closeAll()}.
 *
 * <pre>{@code
 * DataSourceConfig config = DataSourceConfig.builder()
 *     .jdbcUrl("jdbc:postgresql://localhost:5432/mydb")
 *     .username("user")
 *     .password("pass")
 *     .driverClassName("org.postgresql.Driver")
 *     .build();
 *
 * DataSource ds = DataSourceFactory.create(config);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for lifecycle-managed JDBC DataSource instances
 * @doc.layer platform
 * @doc.pattern Factory
 */
public final class DataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFactory.class);
    private static final List<ConnectionPool> pools = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DataSourceFactory::closeAll, "datasource-shutdown"));
    }

    private DataSourceFactory() {}

    /**
     * Creates a new {@link DataSource} backed by a HikariCP connection pool.
     *
     * <p>The pool is tracked for orderly shutdown when the JVM exits or
     * when {@link #closeAll()} is called explicitly.
     *
     * @param config connection pool configuration
     * @return a managed {@code DataSource}
     */
    public static @NotNull DataSource create(@NotNull DataSourceConfig config) {
        Objects.requireNonNull(config, "config");
        ConnectionPool pool = ConnectionPool.create(config);
        pools.add(pool);
        log.info("Created managed DataSource pool '{}'", config.poolName());
        return pool.getDataSource();
    }

    /**
     * Closes all connection pools created through this factory.
     *
     * <p>Called automatically on JVM shutdown. Can also be called explicitly
     * during integration tests or application lifecycle management.
     */
    public static void closeAll() {
        for (ConnectionPool pool : pools) {
            try {
                pool.close();
            } catch (Exception e) {
                log.warn("Error closing connection pool: {}", e.getMessage());
            }
        }
        pools.clear();
    }

    /**
     * Returns the number of active managed pools.
     */
    public static int poolCount() {
        return pools.size();
    }
}
