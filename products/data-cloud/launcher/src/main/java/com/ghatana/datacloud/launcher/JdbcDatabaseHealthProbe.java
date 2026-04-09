package com.ghatana.datacloud.launcher;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * JDBC-backed standalone database health snapshot supplier.
 *
 * <p>Provides a lightweight readiness-style check for launcher health endpoints
 * when only a plain {@link DataSource} is available instead of a JPA-bound
 * {@code EntityManager}. The resulting snapshot is compatible with
 * {@code DataCloudHttpServer.withHealthSubsystem(...)}.
 *
 * @doc.type class
 * @doc.purpose Produce standalone database health snapshots from a JDBC DataSource
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class JdbcDatabaseHealthProbe implements Supplier<Map<String, Object>> {

    private final DataSource dataSource;
    private final int validationTimeoutSeconds;

    public JdbcDatabaseHealthProbe(DataSource dataSource, int validationTimeoutSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.validationTimeoutSeconds = Math.max(1, validationTimeoutSeconds);
    }

    @Override
    public Map<String, Object> get() {
        long startedAt = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(validationTimeoutSeconds);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            if (!valid) {
                return failureSnapshot("Connection validation failed", durationMs);
            }

            DatabaseMetaData metadata = connection.getMetaData();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", "UP");
            snapshot.put("latencyMs", durationMs);
            snapshot.put("validationTimeoutSeconds", validationTimeoutSeconds);
            snapshot.put("databaseProduct", metadata.getDatabaseProductName());
            snapshot.put("databaseVersion", metadata.getDatabaseProductVersion());
            snapshot.put("driver", metadata.getDriverName());

            if (dataSource instanceof HikariDataSource hikariDataSource
                    && hikariDataSource.getHikariPoolMXBean() != null) {
                snapshot.put("poolName", hikariDataSource.getPoolName());
                snapshot.put("activeConnections", hikariDataSource.getHikariPoolMXBean().getActiveConnections());
                snapshot.put("idleConnections", hikariDataSource.getHikariPoolMXBean().getIdleConnections());
                snapshot.put("totalConnections", hikariDataSource.getHikariPoolMXBean().getTotalConnections());
            }

            return snapshot;
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            return failureSnapshot(e.getClass().getSimpleName() + ": " + e.getMessage(), durationMs);
        }
    }

    private Map<String, Object> failureSnapshot(String message, long durationMs) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "DOWN");
        snapshot.put("latencyMs", durationMs);
        snapshot.put("validationTimeoutSeconds", validationTimeoutSeconds);
        snapshot.put("message", message);
        return snapshot;
    }
}
