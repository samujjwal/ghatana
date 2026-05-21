/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link ContextStore} for production Context Plane deployments.
 *
 * @doc.type class
 * @doc.purpose Persists tenant-scoped Context Plane entries in JDBC storage
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class JdbcContextStore implements ContextStore {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public JdbcContextStore(DataSource dataSource) {
        this(dataSource, JsonUtils.getDefaultMapper(), Executors.newVirtualThreadPerTaskExecutor());
    }

    public JdbcContextStore(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.executor = Objects.requireNonNull(executor, "executor");
        initializeSchema();
    }

    @Override
    public Promise<Map<String, Object>> getAllEntries(String tenantId) {
        return Promise.ofBlocking(executor, () -> readEntries(tenantId));
    }

    @Override
    public Promise<Optional<Object>> getEntry(String tenantId, String key) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT context_value FROM datacloud_context_entries WHERE tenant_id = ? AND context_key = ?")) {
                statement.setString(1, tenantId);
                statement.setString(2, key);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(readJsonValue(resultSet.getString("context_value")));
                }
            }
        });
    }

    @Override
    public Promise<Long> putEntries(String tenantId, Map<String, Object> entries) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection()) {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    long version = nextVersion(connection, tenantId);
                    Instant now = Instant.now();
                    for (Map.Entry<String, Object> entry : entries.entrySet()) {
                        upsertEntry(connection, tenantId, entry.getKey(), entry.getValue(), version, now);
                    }
                    connection.commit();
                    return version;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
            }
        });
    }

    @Override
    public Promise<Boolean> deleteEntry(String tenantId, String key) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM datacloud_context_entries WHERE tenant_id = ? AND context_key = ?")) {
                statement.setString(1, tenantId);
                statement.setString(2, key);
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public Promise<Integer> deleteAllEntries(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "DELETE FROM datacloud_context_entries WHERE tenant_id = ?")) {
                statement.setString(1, tenantId);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public Promise<ContextSnapshot> getSnapshot(String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Object> entries = readEntries(tenantId);
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COALESCE(MAX(version), 0) AS version, MIN(created_at) AS created_at, "
                                 + "MAX(updated_at) AS updated_at FROM datacloud_context_entries WHERE tenant_id = ?")) {
                statement.setString(1, tenantId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return new ContextSnapshot(
                                tenantId,
                                entries,
                                resultSet.getLong("version"),
                                toInstant(resultSet.getTimestamp("created_at")),
                                toInstant(resultSet.getTimestamp("updated_at")));
                    }
                }
            }
            Instant now = Instant.now();
            return new ContextSnapshot(tenantId, entries, 0L, now, now);
        });
    }

    private void initializeSchema() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS datacloud_context_entries ("
                             + "tenant_id VARCHAR(255) NOT NULL, "
                             + "context_key VARCHAR(512) NOT NULL, "
                             + "context_value TEXT NOT NULL, "
                             + "version BIGINT NOT NULL, "
                             + "created_at TIMESTAMP NOT NULL, "
                             + "updated_at TIMESTAMP NOT NULL, "
                             + "PRIMARY KEY (tenant_id, context_key))")) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize Context Plane JDBC schema", exception);
        }
    }

    private Map<String, Object> readEntries(String tenantId) throws Exception {
        Map<String, Object> entries = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT context_key, context_value FROM datacloud_context_entries "
                             + "WHERE tenant_id = ? ORDER BY context_key")) {
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.put(
                            resultSet.getString("context_key"),
                            readJsonValue(resultSet.getString("context_value")));
                }
            }
        }
        return Map.copyOf(entries);
    }

    private long nextVersion(Connection connection, String tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(version), 0) + 1 AS next_version "
                        + "FROM datacloud_context_entries WHERE tenant_id = ?")) {
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("next_version") : 1L;
            }
        }
    }

    private void upsertEntry(
            Connection connection,
            String tenantId,
            String key,
            Object value,
            long version,
            Instant now) throws Exception {
        int updated;
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE datacloud_context_entries SET context_value = ?, version = ?, updated_at = ? "
                        + "WHERE tenant_id = ? AND context_key = ?")) {
            update.setString(1, objectMapper.writeValueAsString(value));
            update.setLong(2, version);
            update.setTimestamp(3, Timestamp.from(now));
            update.setString(4, tenantId);
            update.setString(5, key);
            updated = update.executeUpdate();
        }
        if (updated > 0) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO datacloud_context_entries "
                        + "(tenant_id, context_key, context_value, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, tenantId);
            insert.setString(2, key);
            insert.setString(3, objectMapper.writeValueAsString(value));
            insert.setLong(4, version);
            insert.setTimestamp(5, Timestamp.from(now));
            insert.setTimestamp(6, Timestamp.from(now));
            insert.executeUpdate();
        }
    }

    private Object readJsonValue(String json) throws Exception {
        return objectMapper.readValue(json, Object.class);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : Instant.EPOCH;
    }
}
