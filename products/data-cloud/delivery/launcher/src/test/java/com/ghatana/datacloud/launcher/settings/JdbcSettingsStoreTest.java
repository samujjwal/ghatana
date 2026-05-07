/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JdbcSettingsStore — persistent settings backend (DC-S14)")
class JdbcSettingsStoreTest {

    private JdbcSettingsStore store;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = createDataSource("jdbc:h2:mem:test_settings;DB_CLOSE_DELAY=-1");
        ObjectMapper objectMapper = new ObjectMapper();
        store = new JdbcSettingsStore(dataSource, objectMapper);
        
        // Clean up any existing data from previous tests
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Delete from the actual table used by JdbcSettingsStore
            try {
                stmt.execute("DELETE FROM dc_settings");
            } catch (Exception e) {
                // Table doesn't exist yet, ignore
            }
        }
    }

    @Test
    void storageMode_isJdbc() {
        assertThat(store.getStorageMode()).isEqualTo("jdbc");
    }

    @Test
    void getGeneralSettings_emptyWhenNotSet() {
        Map<String, Object> settings = store.getGeneralSettings("tenant-a");
        assertThat(settings).isEmpty();
    }

    @Test
    void updateGeneralSettings_returnsStoredValues() {
        Map<String, Object> input = Map.of("language", "en", "theme", "dark");
        Map<String, Object> returned = store.updateGeneralSettings("tenant-a", input);
        assertThat(returned).containsEntry("language", "en").containsEntry("theme", "dark");

        Map<String, Object> fetched = store.getGeneralSettings("tenant-a");
        assertThat(fetched).containsEntry("language", "en").containsEntry("theme", "dark");
    }

    @Test
    void getSecuritySettings_emptyWhenNotSet() {
        Map<String, Object> settings = store.getSecuritySettings("tenant-b");
        assertThat(settings).isEmpty();
    }

    @Test
    void updateSecuritySettings_persisted() {
        Map<String, Object> input = Map.of("passwordPolicy", Map.of("minLength", 12));
        store.updateSecuritySettings("tenant-b", input);
        assertThat(store.getSecuritySettings("tenant-b"))
            .containsKey("passwordPolicy");
    }

    @Test
    void listApiKeys_emptyWhenNotSet() {
        assertThat(store.listApiKeys("tenant-c")).isEmpty();
    }

    @Test
    void createApiKey_appendedToList() {
        Map<String, Object> key1 = Map.of("id", "key-1", "name", "prod-key", "secret", "s1");
        Map<String, Object> key2 = Map.of("id", "key-2", "name", "dev-key", "secret", "s2");

        store.createApiKey("tenant-c", key1);
        store.createApiKey("tenant-c", key2);

        assertThat(store.listApiKeys("tenant-c")).hasSize(2);
        assertThat(store.getApiKey("tenant-c", "key-1")).isPresent();
        assertThat(store.getApiKey("tenant-c", "key-2")).isPresent();
    }

    @Test
    void revokeApiKey_removesKey() {
        store.createApiKey("tenant-d", Map.of("id", "key-1", "name", "k1"));
        store.createApiKey("tenant-d", Map.of("id", "key-2", "name", "k2"));

        Optional<Map<String, Object>> removed = store.revokeApiKey("tenant-d", "key-1");
        assertThat(removed).isPresent();
        assertThat(removed.get()).containsEntry("id", "key-1");

        assertThat(store.listApiKeys("tenant-d")).hasSize(1);
        assertThat(store.getApiKey("tenant-d", "key-1")).isEmpty();
    }

    @Test
    void rotateApiKey_updatesSecret() {
        store.createApiKey("tenant-e", Map.of("id", "key-1", "name", "k1", "secret", "old-secret"));

        Optional<Map<String, Object>> rotated = store.rotateApiKey("tenant-e", "key-1", "new-secret");
        assertThat(rotated).isPresent();

        Map<String, Object> key = store.getApiKey("tenant-e", "key-1").orElseThrow();
        assertThat(key).containsEntry("secret", "new-secret");
        assertThat(key).containsKey("rotatedAt");
    }

    @Test
    void getApiKey_missing_returnsEmpty() {
        assertThat(store.getApiKey("tenant-f", "no-such-key")).isEmpty();
    }

    @Test
    void profile_persisted() {
        Map<String, Object> profile = Map.of("name", "Alice", "role", "admin");
        store.updateProfile("tenant-g", profile);
        assertThat(store.getProfile("tenant-g")).containsEntry("name", "Alice");
    }

    @Test
    void preferences_persisted() {
        Map<String, Object> prefs = Map.of("notifications", true);
        store.updatePreferences("tenant-h", prefs);
        assertThat(store.getPreferences("tenant-h")).containsEntry("notifications", true);
    }

    @Test
    void notificationPreferences_persisted() {
        Map<String, Object> prefs = Map.of("email", "on");
        store.updateNotificationPreferences("tenant-i", prefs);
        assertThat(store.getNotificationPreferences("tenant-i")).containsEntry("email", "on");
    }

    @Test
    void settingsPersistAcrossStoreReinitialization() throws Exception {
        DataSource dataSource = createDataSource("jdbc:h2:mem:test_settings_restart;DB_CLOSE_DELAY=-1");
        ObjectMapper objectMapper = new ObjectMapper();

        JdbcSettingsStore firstBootStore = new JdbcSettingsStore(dataSource, objectMapper);
        firstBootStore.updateGeneralSettings("tenant-restart", Map.of("theme", "dark", "language", "en"));
        firstBootStore.updateSecuritySettings("tenant-restart", Map.of("mfa", true));

        JdbcSettingsStore restartedStore = new JdbcSettingsStore(dataSource, objectMapper);

        assertThat(restartedStore.getGeneralSettings("tenant-restart"))
            .containsEntry("theme", "dark")
            .containsEntry("language", "en");
        assertThat(restartedStore.getSecuritySettings("tenant-restart"))
            .containsEntry("mfa", true);

        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    @Test
    void tenantIsolation() {
        store.updateGeneralSettings("tenant-x", Map.of("language", "de"));
        store.updateGeneralSettings("tenant-y", Map.of("language", "fr"));

        assertThat(store.getGeneralSettings("tenant-x")).containsEntry("language", "de");
        assertThat(store.getGeneralSettings("tenant-y")).containsEntry("language", "fr");
    }

    @Test
    void nullDataSource_throws() {
        assertThatThrownBy(() -> new JdbcSettingsStore(null, new ObjectMapper()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("dataSource");
    }

    @Test
    void nullObjectMapper_throws() {
        javax.sql.DataSource ds = new com.zaxxer.hikari.HikariDataSource();
        assertThatThrownBy(() -> new JdbcSettingsStore(ds, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("objectMapper");
    }

    private static DataSource createDataSource(String jdbcUrl) throws Exception {
        DataSource dataSource = com.zaxxer.hikari.HikariDataSource.class
            .getConstructor()
            .newInstance();
        ((com.zaxxer.hikari.HikariDataSource) dataSource).setJdbcUrl(jdbcUrl);
        ((com.zaxxer.hikari.HikariDataSource) dataSource).setUsername("sa");
        ((com.zaxxer.hikari.HikariDataSource) dataSource).setPassword("");
        return dataSource;
    }
}
