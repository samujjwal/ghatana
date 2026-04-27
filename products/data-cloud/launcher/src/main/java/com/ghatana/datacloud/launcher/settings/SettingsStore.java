/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistent storage contract for tenant-scoped admin settings.
 *
 * <p>Abstraction layer that allows {@link com.ghatana.datacloud.launcher.http.handlers.SettingsHandler}
 * to operate against either in-memory or durable backends (database, KV store, etc.).
 *
 * @doc.type interface
 * @doc.purpose Persistent storage abstraction for Data Cloud admin settings
 * @doc.layer product
 * @doc.pattern Repository, Strategy
 */
public interface SettingsStore {

    /**
     * Returns the storage mode label for this store implementation.
     *
     * <p>DC-AUD-024: Exposes whether settings are stored in-memory (non-durable)
     * or in a persistent backend so consumers and UI can warn appropriately.
     *
     * @return storage mode string, e.g. "in-memory" or "persistent"
     */
    String getStorageMode();

    /**
     * Retrieve general settings for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return map of general settings; never {@code null}
     */
    Map<String, Object> getGeneralSettings(String tenantId);

    /**
     * Replace general settings for the given tenant.
     *
     * @param tenantId tenant identifier
     * @param settings new settings map
     * @return the persisted settings
     */
    Map<String, Object> updateGeneralSettings(String tenantId, Map<String, Object> settings);

    /**
     * Retrieve security settings for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return map of security settings; never {@code null}
     */
    Map<String, Object> getSecuritySettings(String tenantId);

    /**
     * Replace security settings for the given tenant.
     *
     * @param tenantId tenant identifier
     * @param settings new security settings map
     * @return the persisted settings
     */
    Map<String, Object> updateSecuritySettings(String tenantId, Map<String, Object> settings);

    /**
     * List API keys scoped to the tenant.
     *
     * @param tenantId tenant identifier
     * @return list of API key metadata maps (secrets must be excluded)
     */
    List<Map<String, Object>> listApiKeys(String tenantId);

    /**
     * Create a new API key for the tenant.
     *
     * @param tenantId tenant identifier
     * @param key      API key metadata map (must include {@code id}, {@code name}, {@code scopes}, {@code status}, {@code createdAt}, {@code expiresAt})
     * @return the stored key metadata (secret may be returned once)
     */
    Map<String, Object> createApiKey(String tenantId, Map<String, Object> key);

    /**
     * Revoke an API key by ID.
     *
     * @param tenantId tenant identifier
     * @param keyId    API key identifier
     * @return the revoked key metadata, or empty if not found
     */
    Optional<Map<String, Object>> revokeApiKey(String tenantId, String keyId);

    /**
     * Rotate (regenerate secret) an API key by ID.
     *
     * @param tenantId tenant identifier
     * @param keyId    API key identifier
     * @param newSecret the newly generated secret
     * @return the rotated key metadata, or empty if not found
     */
    Optional<Map<String, Object>> rotateApiKey(String tenantId, String keyId, String newSecret);

    /**
     * Retrieve a single API key by ID.
     *
     * @param tenantId tenant identifier
     * @param keyId    API key identifier
     * @return the key metadata, or empty if not found
     */
    Optional<Map<String, Object>> getApiKey(String tenantId, String keyId);

    /**
     * Retrieve user profile for the tenant.
     *
     * @param tenantId tenant identifier
     * @return map of profile fields; never {@code null}
     */
    Map<String, Object> getProfile(String tenantId);

    /**
     * Update user profile fields.
     *
     * @param tenantId tenant identifier
     * @param profile  profile map
     * @return the persisted profile
     */
    Map<String, Object> updateProfile(String tenantId, Map<String, Object> profile);

    /**
     * Retrieve user preferences for the tenant.
     *
     * @param tenantId tenant identifier
     * @return map of preferences; never {@code null}
     */
    Map<String, Object> getPreferences(String tenantId);

    /**
     * Update user preferences.
     *
     * @param tenantId    tenant identifier
     * @param preferences preferences map
     * @return the persisted preferences
     */
    Map<String, Object> updatePreferences(String tenantId, Map<String, Object> preferences);

    /**
     * Retrieve notification preferences for the tenant.
     *
     * @param tenantId tenant identifier
     * @return map of notification preferences; never {@code null}
     */
    Map<String, Object> getNotificationPreferences(String tenantId);

    /**
     * Update notification preferences.
     *
     * @param tenantId     tenant identifier
     * @param preferences  notification preferences map
     * @return the persisted notification preferences
     */
    Map<String, Object> updateNotificationPreferences(String tenantId, Map<String, Object> preferences);
}
