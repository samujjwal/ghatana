/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for InMemorySettingsStore
 * @doc.layer product
 * @doc.pattern Test
 */
class InMemorySettingsStoreTest {

    @Test
    void shouldReturnInMemoryStorageMode() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        assertThat(store.getStorageMode()).isEqualTo("in-memory");
    }

    @Test
    void shouldGetDefaultGeneralSettingsForNewTenant() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> settings = store.getGeneralSettings("new-tenant");

        assertThat(settings).isNotEmpty();
        assertThat(settings.get("language")).isEqualTo("en");
        assertThat(settings.get("timezone")).isEqualTo("UTC");
        assertThat(settings.get("dateFormat")).isEqualTo("YYYY-MM-DD");
        assertThat(settings.get("theme")).isEqualTo("light");
    }

    @Test
    void shouldUpdateGeneralSettings() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> updates = Map.of("language", "es", "theme", "dark");

        Map<String, Object> result = store.updateGeneralSettings("tenant-1", updates);

        assertThat(result.get("language")).isEqualTo("es");
        assertThat(result.get("theme")).isEqualTo("dark");
        assertThat(result.get("timezone")).isEqualTo("UTC");
    }

    @Test
    void shouldGetDefaultSecuritySettingsForNewTenant() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> settings = store.getSecuritySettings("new-tenant");

        assertThat(settings).isNotEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> passwordPolicy = (Map<String, Object>) settings.get("passwordPolicy");
        assertThat(passwordPolicy.get("minLength")).isEqualTo(12);
        assertThat(passwordPolicy.get("requireUppercase")).isEqualTo(true);
        assertThat(settings.get("sessionTimeout")).isEqualTo(30);
    }

    @Test
    void shouldUpdateSecuritySettings() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> updates = Map.of("sessionTimeout", 60);

        Map<String, Object> result = store.updateSecuritySettings("tenant-1", updates);

        assertThat(result.get("sessionTimeout")).isEqualTo(60);
    }

    @Test
    void shouldListEmptyApiKeysForNewTenant() {
        InMemorySettingsStore store = new InMemorySettingsStore();

        assertThat(store.listApiKeys("new-tenant")).isEmpty();
    }

    @Test
    void shouldCreateApiKey() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> key = Map.of("id", "key-1", "name", "test-key", "secret", "secret123");

        Map<String, Object> result = store.createApiKey("tenant-1", key);

        assertThat(result).isEqualTo(key);
        assertThat(store.listApiKeys("tenant-1")).hasSize(1);
    }

    @Test
    void shouldRevokeApiKey() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> key = Map.of("id", "key-1", "name", "test-key", "secret", "secret123");
        store.createApiKey("tenant-1", key);

        var result = store.revokeApiKey("tenant-1", "key-1");

        assertThat(result).isPresent();
        assertThat(result.get().get("status")).isEqualTo("revoked");
    }

    @Test
    void shouldReturnEmptyWhenRevokingNonExistentKey() {
        InMemorySettingsStore store = new InMemorySettingsStore();

        var result = store.revokeApiKey("tenant-1", "non-existent");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldRotateApiKey() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> key = Map.of("id", "key-1", "name", "test-key", "secret", "secret123");
        store.createApiKey("tenant-1", key);

        var result = store.rotateApiKey("tenant-1", "key-1", "new-secret456");

        assertThat(result).isPresent();
        assertThat(result.get().get("secret")).isEqualTo("new-secret456");
        assertThat(result.get().get("rotationCount")).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyWhenRotatingNonExistentKey() {
        InMemorySettingsStore store = new InMemorySettingsStore();

        var result = store.rotateApiKey("tenant-1", "non-existent", "new-secret");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGetApiKeyById() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> key = Map.of("id", "key-1", "name", "test-key", "secret", "secret123");
        store.createApiKey("tenant-1", key);

        var result = store.getApiKey("tenant-1", "key-1");

        assertThat(result).isPresent();
        assertThat(result.get().get("id")).isEqualTo("key-1");
    }

    @Test
    void shouldReturnEmptyWhenGettingNonExistentKey() {
        InMemorySettingsStore store = new InMemorySettingsStore();

        var result = store.getApiKey("tenant-1", "non-existent");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldGetDefaultProfileForNewTenant() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> profile = store.getProfile("new-tenant");

        assertThat(profile).isNotEmpty();
        assertThat(profile.get("userId")).isEqualTo("anonymous");
        assertThat(profile.get("displayName")).isEqualTo("Data Cloud User");
    }

    @Test
    void shouldUpdateProfile() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> updates = Map.of("displayName", "New Name", "email", "new@example.com");

        Map<String, Object> result = store.updateProfile("tenant-1", updates);

        assertThat(result.get("displayName")).isEqualTo("New Name");
        assertThat(result.get("email")).isEqualTo("new@example.com");
    }

    @Test
    void shouldGetDefaultPreferencesForNewTenant() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> preferences = store.getPreferences("new-tenant");

        assertThat(preferences).isNotEmpty();
        assertThat(preferences.get("userId")).isEqualTo("anonymous");
        assertThat(preferences.get("theme")).isEqualTo("system");
    }

    @Test
    void shouldUpdatePreferences() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> updates = Map.of("theme", "dark", "timezone", "America/New_York");

        Map<String, Object> result = store.updatePreferences("tenant-1", updates);

        assertThat(result.get("theme")).isEqualTo("dark");
        assertThat(result.get("timezone")).isEqualTo("America/New_York");
    }

    @Test
    void shouldGetDefaultNotificationPreferencesForNewTenant() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> preferences = store.getNotificationPreferences("new-tenant");

        assertThat(preferences).isNotEmpty();
        assertThat(preferences.get("userId")).isEqualTo("anonymous");
        assertThat(preferences.get("alertSeverityThreshold")).isEqualTo("warning");
    }

    @Test
    void shouldUpdateNotificationPreferences() {
        InMemorySettingsStore store = new InMemorySettingsStore();
        Map<String, Object> updates = Map.of("alertSeverityThreshold", "error");

        Map<String, Object> result = store.updateNotificationPreferences("tenant-1", updates);

        assertThat(result.get("alertSeverityThreshold")).isEqualTo("error");
    }
}
