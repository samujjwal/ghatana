package com.ghatana.appplatform.config.temporal;

import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigValue;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Provides point-in-time config resolution using the version history table.
 *
 * <p>Use this when you need to answer "what were the config values at 2024-01-01 00:00:00?".
 * For current config resolution, use the standard {@link com.ghatana.appplatform.config.port.ConfigStore}.
 *
 * @doc.type class
 * @doc.purpose Temporal (point-in-time) config query service (STORY-K02-010)
 * @doc.layer product
 * @doc.pattern Service
 */
public class TemporalConfigStore {

    private static final Logger LOG = Logger.getLogger(TemporalConfigStore.class.getName());

    private final DataSource dataSource;

    public TemporalConfigStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Resolve all config values for a namespace as they existed at a specific point in time.
     *
     * @param namespace  Config namespace
     * @param levelId    Tenant / user / session scope identifier
     * @param asOf       Point-in-time to resolve against
     * @return map of key → ConfigValue as they were at {@code asOf}
     */
    public Map<String, ConfigValue> resolveAt(String namespace, String levelId, Instant asOf) {
        String sql = """
            SELECT DISTINCT ON (config_key) config_key, config_value
              FROM config_version_history
             WHERE namespace = ?
               AND level_id = ?
               AND effective_from <= ?
               AND (effective_to IS NULL OR effective_to > ?)
             ORDER BY config_key, effective_from DESC
            """;
        Map<String, ConfigValue> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, levelId);
            ps.setTimestamp(3, Timestamp.from(asOf));
            ps.setTimestamp(4, Timestamp.from(asOf));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("config_key");
                    String value = rs.getString("config_value");
                    result.put(key, new ConfigValue(key, value, ConfigHierarchyLevel.TENANT, levelId));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Temporal config resolution failed for namespace=" + namespace, e);
        }
        LOG.fine("[TemporalConfigStore] Resolved " + result.size() + " keys asOf=" + asOf);
        return result;
    }

    /**
     * Rollback a specific config key to its value as of the given timestamp.
     * Appends a new version history row (immutable audit trail preserved).
     *
     * @param namespace   Config namespace
     * @param configKey   Key to rollback
     * @param levelId     Scope identifier
     * @param hierarchyLevel Level name (GLOBAL/TENANT/USER/etc.)
     * @param asOf        The point in time to restore the value from
     * @param changedBy   Actor performing the rollback
     * @return the rolled-back value, or empty if no historical value found
     */
    public Optional<String> rollback(String namespace, String configKey, String levelId,
                                     String hierarchyLevel, Instant asOf, String changedBy) {
        Map<String, ConfigValue> historical = resolveAt(namespace, levelId, asOf);
        ConfigValue historicalValue = historical.get(configKey);
        if (historicalValue == null) return Optional.empty();

        // Write back as a new version history entry (append-only, preserves audit)
        String insertSql = """
            INSERT INTO config_version_history
              (namespace, config_key, hierarchy_level, level_id, config_value,
               changed_by, change_reason, effective_from)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, namespace);
            ps.setString(2, configKey);
            ps.setString(3, hierarchyLevel);
            ps.setString(4, levelId);
            ps.setString(5, historicalValue.value());
            ps.setString(6, changedBy);
            ps.setString(7, "Rollback to asOf=" + asOf);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Config rollback failed for key=" + configKey, e);
        }
        LOG.info("[TemporalConfigStore] Rolled back key=" + configKey + " to asOf=" + asOf);
        return Optional.of(historicalValue.value());
    }
}
