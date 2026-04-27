/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.settings.InMemorySettingsStore;
import com.ghatana.datacloud.launcher.settings.SettingsStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP handler for admin settings CRUD operations.
 *
 * <p>Provides endpoints for general and security settings used by
 * the Settings page in the Data Cloud UI (GH-90000 / ADMIN-004).
 *
 * <p>All endpoints are tenant-scoped. In development mode the
 * {@code "anonymous"} tenant is used when no {@code X-Tenant-Id}
 * header is present (configurable via strict-tenant resolution).
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/v1/settings}         — general settings (language, theme, notifications, privacy)</li>
 *   <li>{@code POST /api/v1/settings}         — update general settings</li>
 *   <li>{@code GET  /api/v1/settings/security} — security configuration (password policy, sessions, audit)</li>
 *   <li>{@code POST /api/v1/settings/security} — update security settings</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Admin settings CRUD API for Data Cloud UI Settings page
 * @doc.layer product
 * @doc.pattern Handler
 */
public class SettingsHandler {

    private final HttpHandlerSupport http;
    private final SettingsStore store;

    /**
     * Creates handler with default in-memory store.
     *
     * @param httpSupport shared HTTP support
     */
    public SettingsHandler(HttpHandlerSupport httpSupport) {
        this(httpSupport, new InMemorySettingsStore());
    }

    /**
     * Creates handler with explicit store (enables persistent backends).
     *
     * @param httpSupport shared HTTP support
     * @param store       settings storage implementation
     */
    public SettingsHandler(HttpHandlerSupport httpSupport, SettingsStore store) {
        this.http = Objects.requireNonNull(httpSupport, "httpSupport must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    private String resolveTenantId(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return "anonymous";
        }
        return tenantId;
    }

    /**
     * GET /api/v1/settings — return general settings.
     */
    public Promise<HttpResponse> handleGetGeneralSettings(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        Map<String, Object> response = new ConcurrentHashMap<>(store.getGeneralSettings(tenantId));
        response.put("_storageMode", store.getStorageMode());
        return Promise.of(http.jsonResponse(response));
    }

    /**
     * POST /api/v1/settings — update general settings (merge).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateGeneralSettings(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                    Map<String, Object> current = new ConcurrentHashMap<>(store.getGeneralSettings(tenantId));
                    payload.forEach((key, value) -> {
                        if (value == null) {
                            current.remove(key);
                        } else {
                            current.put(key, value);
                        }
                    });

                    // Validate merged settings
                    if (!validateGeneralSettings(current)) {
                        return Promise.of(http.errorResponse(400, "Invalid general settings value"));
                    }

                    return Promise.of(http.jsonResponse(store.updateGeneralSettings(tenantId, current)));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
                }
            });
    }

    /**
     * GET /api/v1/settings/security — return security settings.
     */
    public Promise<HttpResponse> handleGetSecuritySettings(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return Promise.of(http.jsonResponse(store.getSecuritySettings(tenantId)));
    }

    /**
     * POST /api/v1/settings/security — update security settings (merge).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateSecuritySettings(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                    Map<String, Object> current = new ConcurrentHashMap<>(store.getSecuritySettings(tenantId));
                    payload.forEach((key, value) -> {
                        if (value == null) {
                            current.remove(key);
                        } else {
                            current.put(key, value);
                        }
                    });

                    return Promise.of(http.jsonResponse(store.updateSecuritySettings(tenantId, current)));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
                }
            });
    }

    // ── API Key endpoints ───────────────────────────────────────────────────

    public Promise<HttpResponse> handleListApiKeys(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        List<Map<String, Object>> keys = store.listApiKeys(tenantId);
        Map<String, Object> envelope = new ConcurrentHashMap<>();
        envelope.put("tenantId", tenantId);
        envelope.put("keys", keys);
        envelope.put("count", keys.size());
        envelope.put("_storageMode", store.getStorageMode());
        envelope.put("timestamp", Instant.now().toString());
        return Promise.of(http.jsonResponse(envelope));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateApiKey(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                String id = UUID.randomUUID().toString();
                String secret = UUID.randomUUID().toString().replace("-", "");
                Instant now = Instant.now();
                Map<String, Object> key = new ConcurrentHashMap<>();
                key.put("id", id);
                key.put("tenantId", tenantId);
                key.put("name", payload.getOrDefault("name", "unnamed-key"));
                key.put("scopes", payload.getOrDefault("scopes", List.of("read")));
                key.put("roles", payload.getOrDefault("roles", List.of("viewer")));
                key.put("secret", secret);
                key.put("secretRevealed", false);
                key.put("status", "active");
                key.put("createdAt", now.toString());
                key.put("expiresAt", now.plusSeconds(86400 * 90).toString());
                key.put("rotatedAt", null);
                key.put("rotationCount", 0);
                key.put("audit", List.of(
                    Map.of("action", "created", "actor", "settings-handler", "timestamp", now.toString())
                ));
                store.createApiKey(tenantId, key);
                Map<String, Object> response = new ConcurrentHashMap<>(key);
                response.remove("secret");
                return Promise.of(http.jsonResponse(response));
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
            }
        });
    }

    public Promise<HttpResponse> handleGetApiKey(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String keyId = request.getPathParameter("id");
        Optional<Map<String, Object>> keyOpt = store.getApiKey(tenantId, keyId);
        if (keyOpt.isPresent()) {
            Map<String, Object> response = new ConcurrentHashMap<>(keyOpt.get());
            response.remove("secret");
            return Promise.of(http.jsonResponse(response));
        }
        return Promise.of(http.errorResponse(404, "API key not found: " + keyId));
    }

    public Promise<HttpResponse> handleRotateApiKey(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String keyId = request.getPathParameter("id");
        String newSecret = UUID.randomUUID().toString().replace("-", "");
        Optional<Map<String, Object>> rotated = store.rotateApiKey(tenantId, keyId, newSecret);
        if (rotated.isPresent()) {
            Map<String, Object> response = new ConcurrentHashMap<>(rotated.get());
            // One-time reveal of the new secret after rotation
            response.put("secret", newSecret);
            response.put("secretRevealed", true);
            return Promise.of(http.jsonResponse(response));
        }
        return Promise.of(http.errorResponse(404, "API key not found: " + keyId));
    }

    public Promise<HttpResponse> handleRevokeApiKey(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String keyId = request.getPathParameter("id");
        Optional<Map<String, Object>> revoked = store.revokeApiKey(tenantId, keyId);
        if (revoked.isPresent()) {
            return Promise.of(http.jsonResponse(revoked.get()));
        }
        return Promise.of(http.errorResponse(404, "API key not found: " + keyId));
    }

    // ── Profile endpoints ───────────────────────────────────────────────────

    public Promise<HttpResponse> handleGetProfile(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        Map<String, Object> response = new ConcurrentHashMap<>(store.getProfile(tenantId));
        response.put("_storageMode", store.getStorageMode());
        return Promise.of(http.jsonResponse(response));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateProfile(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                Map<String, Object> current = new ConcurrentHashMap<>(store.getProfile(tenantId));
                payload.forEach((key, value) -> {
                    if (value != null) {
                        current.put(key, value);
                    }
                });
                current.put("updatedAt", Instant.now().toString());
                return Promise.of(http.jsonResponse(store.updateProfile(tenantId, current)));
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
            }
        });
    }

    // ── Preferences endpoints ───────────────────────────────────────────────

    public Promise<HttpResponse> handleGetPreferences(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        Map<String, Object> response = new ConcurrentHashMap<>(store.getPreferences(tenantId));
        response.put("_storageMode", store.getStorageMode());
        return Promise.of(http.jsonResponse(response));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdatePreferences(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                Map<String, Object> current = new ConcurrentHashMap<>(store.getPreferences(tenantId));
                payload.forEach((key, value) -> {
                    if (value != null) {
                        current.put(key, value);
                    }
                });
                current.put("updatedAt", Instant.now().toString());
                return Promise.of(http.jsonResponse(store.updatePreferences(tenantId, current)));
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
            }
        });
    }

    // ── Notification preferences endpoints ──────────────────────────────────

    public Promise<HttpResponse> handleGetNotificationPreferences(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        Map<String, Object> response = new ConcurrentHashMap<>(store.getNotificationPreferences(tenantId));
        response.put("_storageMode", store.getStorageMode());
        return Promise.of(http.jsonResponse(response));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateNotificationPreferences(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                Map<String, Object> current = new ConcurrentHashMap<>(store.getNotificationPreferences(tenantId));
                payload.forEach((key, value) -> {
                    if (value != null) {
                        current.put(key, value);
                    }
                });
                current.put("updatedAt", Instant.now().toString());
                return Promise.of(http.jsonResponse(store.updateNotificationPreferences(tenantId, current)));
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
            }
        });
    }

    /**
     * Validates general settings values.
     */
    private boolean validateGeneralSettings(Map<String, Object> settings) {
        Object theme = settings.get("theme");
        if (theme != null && !(theme instanceof String s && (s.equals("light") || s.equals("dark") || s.equals("auto")))) {
            return false;
        }
        return true;
    }
}
