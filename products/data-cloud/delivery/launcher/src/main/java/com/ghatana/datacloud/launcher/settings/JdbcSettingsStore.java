/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC-backed {@link SettingsStore} implementation.
 *
 * <p>Persists tenant-scoped settings into a relational table as JSON blobs,
 * enabling durable storage across restarts. Schema is auto-initialised on
 * first use so no external migration tool is required for the settings surface.
 *
 * <p>Each tenant/category pair stores one row. Categories:
 * {@code general}, {@code security}, {@code api_keys}, {@code profile},
 * {@code preferences}, {@code notifications}.
 *
 * @doc.type class
 * @doc.purpose Persistent JDBC implementation of SettingsStore for production deployments
 * @doc.layer product
 * @doc.pattern Repository, Strategy
 */
public class JdbcSettingsStore implements SettingsStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcSettingsStore.class);
    private static final List<Map<String, Object>> NO_SETTINGS_LIST = List.of();

    private static final String TABLE = "dc_settings";
    private static final String INIT_SQL =
        "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
        "  tenant_id VARCHAR(255) NOT NULL," +
        "  category  VARCHAR(50)  NOT NULL," +
        "  settings_json CLOB     NOT NULL," +
        "  updated_at TIMESTAMP NOT NULL," +
        "  PRIMARY KEY (tenant_id, category)" +
        ")";

    private static final String UPSERT_SQL =
        "MERGE INTO " + TABLE + " (tenant_id, category, settings_json, updated_at) " +
        "  KEY (tenant_id, category) " +
        "  VALUES (?, ?, ?, ?)";

    private static final String SELECT_SQL =
        "SELECT settings_json FROM " + TABLE + " WHERE tenant_id = ? AND category = ?";

    private static final String DELETE_SQL =
        "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND category = ?";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private volatile boolean initialised;

    /**
     * Creates a JDBC-backed settings store.
     *
     * @param dataSource   JDBC data source; must not be {@code null}
     * @param objectMapper Jackson mapper for JSON (de)serialization; must not be {@code null}
     */
    public JdbcSettingsStore(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public String getStorageMode() {
        return "jdbc";
    }

    @Override
    public Map<String, Object> getGeneralSettings(String tenantId) {
        return readMap(tenantId, "general");
    }

    @Override
    public Map<String, Object> updateGeneralSettings(String tenantId, Map<String, Object> settings) {
        write(tenantId, "general", settings);
        return settings;
    }

    @Override
    public Map<String, Object> getSecuritySettings(String tenantId) {
        return readMap(tenantId, "security");
    }

    @Override
    public Map<String, Object> updateSecuritySettings(String tenantId, Map<String, Object> settings) {
        write(tenantId, "security", settings);
        return settings;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listApiKeys(String tenantId) {
        return readList(tenantId, "api_keys");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> createApiKey(String tenantId, Map<String, Object> key) {
        List<Map<String, Object>> keys = new ArrayList<>(readList(tenantId, "api_keys"));
        keys.add(key);
        write(tenantId, "api_keys", keys);
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> revokeApiKey(String tenantId, String keyId) {
        List<Map<String, Object>> keys = new ArrayList<>(readList(tenantId, "api_keys"));
        Optional<Map<String, Object>> found = keys.stream()
            .filter(k -> keyId.equals(k.get("id")))
            .findFirst();
        if (found.isPresent()) {
            keys.removeIf(k -> keyId.equals(k.get("id")));
            write(tenantId, "api_keys", keys);
        }
        return found;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> rotateApiKey(String tenantId, String keyId, String newSecret) {
        List<Map<String, Object>> keys = new ArrayList<>(readList(tenantId, "api_keys"));
        Optional<Map<String, Object>> found = keys.stream()
            .filter(k -> keyId.equals(k.get("id")))
            .findFirst();
        if (found.isPresent()) {
            Map<String, Object> key = found.get();
            key.put("secret", newSecret);
            key.put("rotatedAt", Instant.now().toString());
            write(tenantId, "api_keys", keys);
        }
        return found;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getApiKey(String tenantId, String keyId) {
        return readList(tenantId, "api_keys").stream()
            .filter(k -> keyId.equals(k.get("id")))
            .findFirst();
    }

    @Override
    public Map<String, Object> getProfile(String tenantId) {
        return readMap(tenantId, "profile");
    }

    @Override
    public Map<String, Object> updateProfile(String tenantId, Map<String, Object> profile) {
        write(tenantId, "profile", profile);
        return profile;
    }

    @Override
    public Map<String, Object> getPreferences(String tenantId) {
        return readMap(tenantId, "preferences");
    }

    @Override
    public Map<String, Object> updatePreferences(String tenantId, Map<String, Object> preferences) {
        write(tenantId, "preferences", preferences);
        return preferences;
    }

    @Override
    public Map<String, Object> getNotificationPreferences(String tenantId) {
        return readMap(tenantId, "notifications");
    }

    @Override
    public Map<String, Object> updateNotificationPreferences(String tenantId, Map<String, Object> preferences) {
        write(tenantId, "notifications", preferences);
        return preferences;
    }

    // ───────────────────────────── internal helpers ─────────────────────────────

    private void ensureSchema() {
        if (initialised) {
            return;
        }
        synchronized (this) {
            if (initialised) {
                return;
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INIT_SQL)) {
                ps.executeUpdate();
                initialised = true;
                log.info("[SETTINGS] JdbcSettingsStore schema initialised on {}",
                    conn.getMetaData().getURL());
            } catch (SQLException e) {
                throw new SettingsStoreException("Failed to initialise settings schema", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String tenantId, String category) {
        ensureSchema();
        String json = readJson(tenantId, category);
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[SETTINGS] Failed to deserialise {} for tenant {} — returning empty map", category, tenantId, e);
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String tenantId, String category) {
        ensureSchema();
        String json = readJson(tenantId, category);
        if (json == null || json.isBlank()) {
            return NO_SETTINGS_LIST;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("[SETTINGS] Failed to deserialise {} for tenant {} — returning empty list", category, tenantId, e);
            return NO_SETTINGS_LIST;
        }
    }

    private String readJson(String tenantId, String category) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, tenantId);
            ps.setString(2, category);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("settings_json");
                }
            }
            return null;
        } catch (SQLException e) {
            throw new SettingsStoreException(
                "Failed to read settings [tenant=" + tenantId + ", category=" + category + "]", e);
        }
    }

    private void write(String tenantId, String category, Object value) {
        ensureSchema();
        try {
            String json = objectMapper.writeValueAsString(value);
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
                ps.setString(1, tenantId);
                ps.setString(2, category);
                ps.setString(3, json);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new SettingsStoreException(
                "Failed to write settings [tenant=" + tenantId + ", category=" + category + "]", e);
        } catch (Exception e) {
            throw new SettingsStoreException(
                "Failed to serialise settings [tenant=" + tenantId + ", category=" + category + "]", e);
        }
    }
}
