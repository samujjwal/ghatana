package com.ghatana.appplatform.config.adapter;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigSchema;
import com.ghatana.appplatform.config.domain.ConfigValue;
import com.ghatana.appplatform.config.merge.ConfigMerger;
import com.ghatana.appplatform.config.port.ConfigStore;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL implementation of {@link ConfigStore}.
 *
 * <p>Wraps all JDBC operations in {@link Promise#ofBlocking} to stay non-blocking on
 * the ActiveJ eventloop. Uses two tables:
 * <ul>
 *   <li>{@code config_schemas} — versioned JSON Schema registry
 *   <li>{@code config_entries} — hierarchical config values
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL-backed hierarchical config store
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresConfigStore implements ConfigStore {

    private final DataSource   dataSource;
    private final Executor     executor;
    private final ConfigMerger merger;

    public PostgresConfigStore(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor   = Objects.requireNonNull(executor, "executor");
        this.merger     = new ConfigMerger();
    }

    // -------------------------------------------------------------------------
    // Schema registry
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> registerSchema(ConfigSchema schema) {
        Objects.requireNonNull(schema, "schema");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO config_schemas (namespace, version, json_schema, description, defaults)
                VALUES (?, ?, ?::jsonb, ?, ?::jsonb)
                ON CONFLICT (namespace, version) DO UPDATE
                    SET json_schema  = EXCLUDED.json_schema,
                        description  = EXCLUDED.description,
                        defaults     = EXCLUDED.defaults,
                        updated_at   = NOW()
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema.namespace());
                ps.setString(2, schema.version());
                ps.setString(3, schema.jsonSchema());
                ps.setString(4, schema.description());
                ps.setString(5, schema.defaults());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Optional<ConfigSchema>> getSchema(String namespace, String version) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(version, "version");
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT namespace, version, json_schema::text, description, defaults::text
                  FROM config_schemas
                 WHERE namespace = ? AND version = ?
                """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, namespace);
                ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new ConfigSchema(
                            rs.getString("namespace"),
                            rs.getString("version"),
                            rs.getString("json_schema"),
                            rs.getString("description"),
                            rs.getString("defaults")));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Entry management
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> setEntry(ConfigEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return Promise.ofBlocking(executor, () -> {
            String upsertSql = """
                INSERT INTO config_entries
                    (namespace, key, value, level, level_id, schema_namespace)
                VALUES (?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (namespace, key, level, level_id) DO UPDATE
                    SET value            = EXCLUDED.value,
                        schema_namespace = EXCLUDED.schema_namespace,
                        updated_at       = NOW()
                """;
            String historySql = """
                INSERT INTO config_version_history
                    (namespace, config_key, hierarchy_level, level_id, config_value, changed_by)
                VALUES (?, ?, ?, ?, ?, 'system')
                """;
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    ps.setString(1, entry.namespace());
                    ps.setString(2, entry.key());
                    ps.setString(3, entry.value());
                    ps.setString(4, entry.level().name());
                    ps.setString(5, entry.levelId());
                    ps.setString(6, entry.schemaNamespace());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(historySql)) {
                    ps.setString(1, entry.namespace());
                    ps.setString(2, entry.key());
                    ps.setString(3, entry.level().name());
                    ps.setString(4, entry.levelId());
                    ps.setString(5, entry.value());
                    ps.executeUpdate();
                }
                conn.commit();
            }
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    @Override
    public Promise<Map<String, ConfigValue>> resolve(
            String namespace,
            String tenantId,
            String userId,
            String sessionId,
            String jurisdiction) {
        Objects.requireNonNull(namespace, "namespace");
        return Promise.ofBlocking(executor, () -> {
            List<ConfigEntry> entries = loadApplicableEntries(
                namespace, tenantId, userId, sessionId, jurisdiction);
            return merger.merge(entries);
        });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<ConfigEntry> loadApplicableEntries(
            String namespace,
            String tenantId,
            String userId,
            String sessionId,
            String jurisdiction) throws SQLException {

        // Build WHERE clause: always include GLOBAL; include other levels when IDs are provided
        StringBuilder sql = new StringBuilder("""
            SELECT namespace, key, value::text, level, level_id, schema_namespace
              FROM config_entries
             WHERE namespace = ?
               AND (
                     (level = 'GLOBAL' AND level_id = 'global')
            """);

        List<Object> params = new ArrayList<>();
        params.add(namespace);

        if (jurisdiction != null && !jurisdiction.isBlank()) {
            sql.append("  OR (level = 'JURISDICTION' AND level_id = ?) ");
            params.add(jurisdiction);
        }
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append("  OR (level = 'TENANT' AND level_id = ?) ");
            params.add(tenantId);
        }
        if (userId != null && !userId.isBlank()) {
            sql.append("  OR (level = 'USER' AND level_id = ?) ");
            params.add(userId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            sql.append("  OR (level = 'SESSION' AND level_id = ?) ");
            params.add(sessionId);
        }
        sql.append(") ORDER BY level ASC");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, (String) params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<ConfigEntry> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new ConfigEntry(
                        rs.getString("namespace"),
                        rs.getString("key"),
                        rs.getString("value"),
                        ConfigHierarchyLevel.valueOf(rs.getString("level")),
                        rs.getString("level_id"),
                        rs.getString("schema_namespace")));
                }
                return result;
            }
        }
    }
}
