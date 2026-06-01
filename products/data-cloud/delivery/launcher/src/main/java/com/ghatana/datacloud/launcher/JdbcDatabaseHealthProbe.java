package com.ghatana.datacloud.launcher;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * WS5: Extended to map to runtime truth probes by checking entity store and
 * event store table availability where applicable.
 *
 * @doc.type class
 * @doc.purpose Produce standalone database health snapshots from a JDBC DataSource
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class JdbcDatabaseHealthProbe implements Supplier<Map<String, Object>> {

    private final DataSource dataSource;
    private final int validationTimeoutSeconds;
    private final boolean checkEntityStore;
    private final boolean checkEventStore;

    public JdbcDatabaseHealthProbe(DataSource dataSource, int validationTimeoutSeconds) {
        this(dataSource, validationTimeoutSeconds, true, true);
    }

    public JdbcDatabaseHealthProbe(DataSource dataSource, int validationTimeoutSeconds,
                                    boolean checkEntityStore, boolean checkEventStore) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.validationTimeoutSeconds = Math.max(1, validationTimeoutSeconds);
        this.checkEntityStore = checkEntityStore;
        this.checkEventStore = checkEventStore;
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

            // WS5: Check entity store table availability
            if (checkEntityStore) {
                Map<String, Object> entityStoreCheck = checkEntityStoreTable(connection);
                snapshot.put("entityStore", entityStoreCheck.get("status"));
                if (entityStoreCheck.containsKey("message")) {
                    snapshot.put("entityStoreMessage", entityStoreCheck.get("message"));
                }
            } else {
                snapshot.put("entityStore", "SKIPPED");
            }

            // WS5: Check event store table availability
            if (checkEventStore) {
                Map<String, Object> eventStoreCheck = checkEventStoreTable(connection);
                snapshot.put("eventStore", eventStoreCheck.get("status"));
                if (eventStoreCheck.containsKey("message")) {
                    snapshot.put("eventStoreMessage", eventStoreCheck.get("message"));
                }
            } else {
                snapshot.put("eventStore", "SKIPPED");
            }

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

    /**
     * WS5: Check if entity store tables exist and are accessible.
     */
    private Map<String, Object> checkEntityStoreTable(Connection connection) {
        try {
            // Check for typical entity store table names
            String[] possibleTables = {"entities", "entity_store", "dc_entities"};
            boolean found = false;
            for (String tableName : possibleTables) {
                if (tableExists(connection, tableName)) {
                    found = true;
                    break;
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", found ? "UP" : "DOWN");
            if (!found) {
                result.put("message", "Entity store table not found");
            }
            return result;
        } catch (SQLException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "DOWN");
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            return result;
        }
    }

    /**
     * WS5: Check if event store tables exist and are accessible.
     */
    private Map<String, Object> checkEventStoreTable(Connection connection) {
        try {
            // Check for typical event store table names
            String[] possibleTables = {"events", "event_log", "event_store", "dc_events"};
            boolean found = false;
            for (String tableName : possibleTables) {
                if (tableExists(connection, tableName)) {
                    found = true;
                    break;
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", found ? "UP" : "DOWN");
            if (!found) {
                result.put("message", "Event store table not found");
            }
            return result;
        } catch (SQLException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "DOWN");
            result.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            return result;
        }
    }

    /**
     * Check if a table exists in the database.
     */
    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(
                null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
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
