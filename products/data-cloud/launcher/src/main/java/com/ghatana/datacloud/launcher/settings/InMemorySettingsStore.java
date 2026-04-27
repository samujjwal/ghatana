/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SettingsStore}.
 *
 * <p>Used as the default backing store for {@link SettingsHandler}.
 * All state is tenant-scoped and non-durable; intended for local/dev usage.
 * Production deployments should inject a persistent implementation.
 *
 * @doc.type class
 * @doc.purpose Default in-memory settings storage for Data Cloud admin settings
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemorySettingsStore implements SettingsStore {

    /**
     * Returns the storage mode label for this store implementation.
     *
     * <p>DC-AUD-024: Exposes in-memory mode so consumers and UI can warn
     * that settings are non-durable and will be lost on restart.
     */
    @Override
    public String getStorageMode() {
        return "in-memory";
    }

    private final Map<String, Map<String, Object>> generalSettingsByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> securitySettingsByTenant = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> apiKeysByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> userProfileByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> userPreferencesByTenant = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> notificationPreferencesByTenant = new ConcurrentHashMap<>();

    public InMemorySettingsStore() {
        // Pre-populate defaults for the "anonymous" tenant used in dev mode
        ensureTenantInitialized("anonymous");
    }

    private void ensureTenantInitialized(String tenantId) {
        generalSettingsByTenant.computeIfAbsent(tenantId, k -> {
            Map<String, Object> general = new ConcurrentHashMap<>();
            general.put("language", "en");
            general.put("timezone", "UTC");
            general.put("dateFormat", "YYYY-MM-DD");
            general.put("theme", "light");
            general.put("availableLanguages", List.of("en", "es", "fr", "de", "ja"));
            general.put("availableTimezones", List.of("UTC", "America/New_York", "Europe/London", "Asia/Tokyo"));

            Map<String, Object> notifications = new ConcurrentHashMap<>();
            notifications.put("email", true);
            notifications.put("inApp", true);
            notifications.put("sms", false);
            general.put("notifications", notifications);

            Map<String, Object> privacy = new ConcurrentHashMap<>();
            privacy.put("dataRetention", 365);
            privacy.put("analyticsEnabled", true);
            general.put("privacy", privacy);

            general.put("twoFactorEnabled", false);
            general.put("apiKeys", List.of());
            return general;
        });

        userProfileByTenant.computeIfAbsent(tenantId, k -> {
            Map<String, Object> profile = new ConcurrentHashMap<>();
            profile.put("userId", "anonymous");
            profile.put("displayName", "Data Cloud User");
            profile.put("email", "user@ghatana.local");
            profile.put("avatarUrl", "");
            profile.put("createdAt", Instant.now().toString());
            profile.put("updatedAt", Instant.now().toString());
            return profile;
        });

        userPreferencesByTenant.computeIfAbsent(tenantId, k -> {
            Map<String, Object> preferences = new ConcurrentHashMap<>();
            preferences.put("userId", "anonymous");
            preferences.put("theme", "system");
            preferences.put("timezone", "UTC");
            preferences.put("dateFormat", "ISO");
            preferences.put("updatedAt", Instant.now().toString());
            return preferences;
        });

        notificationPreferencesByTenant.computeIfAbsent(tenantId, k -> {
            Map<String, Object> channels = new ConcurrentHashMap<>();
            channels.put("email", true);
            channels.put("inApp", true);
            channels.put("slack", false);
            channels.put("webhook", false);

            Map<String, Object> notificationPrefs = new ConcurrentHashMap<>();
            notificationPrefs.put("userId", "anonymous");
            notificationPrefs.put("channels", channels);
            notificationPrefs.put("alertSeverityThreshold", "warning");
            notificationPrefs.put("digestFrequency", "daily");
            notificationPrefs.put("updatedAt", Instant.now().toString());
            return notificationPrefs;
        });

        securitySettingsByTenant.computeIfAbsent(tenantId, k -> {
            Map<String, Object> passwordPolicy = new ConcurrentHashMap<>();
            passwordPolicy.put("minLength", 12);
            passwordPolicy.put("requireUppercase", true);
            passwordPolicy.put("requireNumbers", true);
            passwordPolicy.put("requireSpecialChars", true);

            Map<String, Object> security = new ConcurrentHashMap<>();
            security.put("passwordPolicy", passwordPolicy);
            security.put("sessionTimeout", 30);
            security.put("twoFactorRequired", false);
            security.put("loginHistory", List.of());
            security.put("activeSessions", List.of());
            security.put("auditTrail", List.of());
            return security;
        });

        apiKeysByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>());
    }

    @Override
    public Map<String, Object> getGeneralSettings(String tenantId) {
        ensureTenantInitialized(tenantId);
        return Map.copyOf(generalSettingsByTenant.get(tenantId));
    }

    @Override
    public Map<String, Object> updateGeneralSettings(String tenantId, Map<String, Object> settings) {
        ensureTenantInitialized(tenantId);
        Map<String, Object> stored = generalSettingsByTenant.get(tenantId);
        stored.putAll(settings);
        return Map.copyOf(stored);
    }

    @Override
    public Map<String, Object> getSecuritySettings(String tenantId) {
        ensureTenantInitialized(tenantId);
        return Map.copyOf(securitySettingsByTenant.get(tenantId));
    }

    @Override
    public Map<String, Object> updateSecuritySettings(String tenantId, Map<String, Object> settings) {
        ensureTenantInitialized(tenantId);
        Map<String, Object> stored = securitySettingsByTenant.get(tenantId);
        stored.putAll(settings);
        return Map.copyOf(stored);
    }

    @Override
    public List<Map<String, Object>> listApiKeys(String tenantId) {
        ensureTenantInitialized(tenantId);
        return List.copyOf(apiKeysByTenant.get(tenantId));
    }

    @Override
    public Map<String, Object> createApiKey(String tenantId, Map<String, Object> key) {
        ensureTenantInitialized(tenantId);
        List<Map<String, Object>> keys = apiKeysByTenant.get(tenantId);
        keys.add(new ConcurrentHashMap<>(key));
        return key;
    }

    @Override
    public Optional<Map<String, Object>> revokeApiKey(String tenantId, String keyId) {
        List<Map<String, Object>> keys = apiKeysByTenant.get(tenantId);
        if (keys == null) {
            return Optional.empty();
        }
        for (Map<String, Object> key : keys) {
            if (keyId.equals(key.get("id"))) {
                key.put("status", "revoked");
                key.put("revokedAt", Instant.now().toString());
                return Optional.of(Map.copyOf(key));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, Object>> rotateApiKey(String tenantId, String keyId, String newSecret) {
        List<Map<String, Object>> keys = apiKeysByTenant.get(tenantId);
        if (keys == null) {
            return Optional.empty();
        }
        for (Map<String, Object> key : keys) {
            if (keyId.equals(key.get("id"))) {
                key.put("secret", newSecret);
                key.put("rotatedAt", Instant.now().toString());
                key.put("rotationCount", ((Number) key.getOrDefault("rotationCount", 0)).intValue() + 1);
                key.put("secretRevealed", true);
                return Optional.of(Map.copyOf(key));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, Object>> getApiKey(String tenantId, String keyId) {
        List<Map<String, Object>> keys = apiKeysByTenant.get(tenantId);
        if (keys == null) {
            return Optional.empty();
        }
        for (Map<String, Object> key : keys) {
            if (keyId.equals(key.get("id"))) {
                return Optional.of(Map.copyOf(key));
            }
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Object> getProfile(String tenantId) {
        ensureTenantInitialized(tenantId);
        return Map.copyOf(userProfileByTenant.get(tenantId));
    }

    @Override
    public Map<String, Object> updateProfile(String tenantId, Map<String, Object> profile) {
        ensureTenantInitialized(tenantId);
        Map<String, Object> stored = userProfileByTenant.get(tenantId);
        stored.putAll(profile);
        return Map.copyOf(stored);
    }

    @Override
    public Map<String, Object> getPreferences(String tenantId) {
        ensureTenantInitialized(tenantId);
        return Map.copyOf(userPreferencesByTenant.get(tenantId));
    }

    @Override
    public Map<String, Object> updatePreferences(String tenantId, Map<String, Object> preferences) {
        ensureTenantInitialized(tenantId);
        Map<String, Object> stored = userPreferencesByTenant.get(tenantId);
        stored.putAll(preferences);
        return Map.copyOf(stored);
    }

    @Override
    public Map<String, Object> getNotificationPreferences(String tenantId) {
        ensureTenantInitialized(tenantId);
        return Map.copyOf(notificationPreferencesByTenant.get(tenantId));
    }

    @Override
    public Map<String, Object> updateNotificationPreferences(String tenantId, Map<String, Object> preferences) {
        ensureTenantInitialized(tenantId);
        Map<String, Object> stored = notificationPreferencesByTenant.get(tenantId);
        stored.putAll(preferences);
        return Map.copyOf(stored);
    }
}
