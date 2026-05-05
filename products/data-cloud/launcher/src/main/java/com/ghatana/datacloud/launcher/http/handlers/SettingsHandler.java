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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private static final Logger log = LoggerFactory.getLogger(SettingsHandler.class);
    private static final String IN_MEMORY_STORE_MODE = "in-memory";

    private final HttpHandlerSupport http;
    private final SettingsStore store;
    /**
     * When {@code true}, write operations are rejected if the store is in-memory.
     * Enable this for production/strict profiles to prevent data loss on restart.
     */
    private final boolean strictMode;
    // P3.5: In-memory pending-approval registry for sensitive settings changes
    private final Map<String, List<Map<String, Object>>> pendingApprovals = new ConcurrentHashMap<>();

    /**
     * Creates handler with default in-memory store (non-strict mode).
     *
     * @param httpSupport shared HTTP support
     */
    public SettingsHandler(HttpHandlerSupport httpSupport) {
        this(httpSupport, new InMemorySettingsStore(), false);
    }

    /**
     * Creates handler with explicit store (enables persistent backends).
     *
     * @param httpSupport shared HTTP support
     * @param store       settings storage implementation
     */
    public SettingsHandler(HttpHandlerSupport httpSupport, SettingsStore store) {
        this(httpSupport, store, false);
    }

    /**
     * Creates handler with explicit store and strict-mode control.
     *
     * <p>DC-P2-009: When {@code strictMode=true} write operations are rejected if the
     * configured store is non-persistent (in-memory), preventing silent data loss
     * in production and strict deployment profiles.
     *
     * @param httpSupport shared HTTP support
     * @param store       settings storage implementation
     * @param strictMode  {@code true} to block in-memory writes in production/strict profiles
     */
    public SettingsHandler(HttpHandlerSupport httpSupport, SettingsStore store, boolean strictMode) {
        this.http = Objects.requireNonNull(httpSupport, "httpSupport must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.strictMode = strictMode;
    }

    /**
     * Returns an HTTP 503 if strict mode is active and the backing store is in-memory.
     *
     * <p>DC-P2-009: Protects against silent data loss in production profiles.
     * Returns {@code null} when the guard passes (write may proceed).
     */
    private HttpResponse strictModeGuard(String operation) {
        if (strictMode && IN_MEMORY_STORE_MODE.equals(store.getStorageMode())) {
            log.warn("[DC-P2-009] Settings write blocked in strict mode: operation={} storeMode={}",
                     operation, store.getStorageMode());
            return http.errorResponse(503,
                "Settings writes are disabled: in-memory store is not allowed in strict/production profile");
        }
        return null;
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
        Map<String, Object> response = new LinkedHashMap<>(store.getGeneralSettings(tenantId));
        response.put("_storageMode", store.getStorageMode());
        return Promise.of(http.jsonResponse(response));
    }

    /**
     * POST /api/v1/settings — update general settings (merge).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateGeneralSettings(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        HttpResponse guardResponse = strictModeGuard("updateGeneralSettings");
        if (guardResponse != null) {
            return Promise.of(guardResponse);
        }
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                    Map<String, Object> current = new LinkedHashMap<>(store.getGeneralSettings(tenantId));
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

                    Map<String, Object> saved = store.updateGeneralSettings(tenantId, current);
                    log.info("[DC-P2-009] General settings updated: tenant={} keys={}",
                             tenantId, payload.keySet());
                    return Promise.of(http.jsonResponse(saved));
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
        HttpResponse guardResponse = strictModeGuard("updateSecuritySettings");
        if (guardResponse != null) {
            return Promise.of(guardResponse);
        }
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                    Map<String, Object> current = new LinkedHashMap<>(store.getSecuritySettings(tenantId));
                    payload.forEach((key, value) -> {
                        if (value == null) {
                            current.remove(key);
                        } else {
                            current.put(key, value);
                        }
                    });

                    if (!validateSecuritySettings(current)) {
                        return Promise.of(http.errorResponse(400, "Invalid security settings value"));
                    }

                    Map<String, Object> saved = store.updateSecuritySettings(tenantId, current);
                    log.info("[DC-P2-009] Security settings updated: tenant={} keys={}",
                             tenantId, payload.keySet());
                    return Promise.of(http.jsonResponse(saved));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
                }
            });
    }

    // ── API Key endpoints ───────────────────────────────────────────────────

    public Promise<HttpResponse> handleListApiKeys(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        List<Map<String, Object>> keys = store.listApiKeys(tenantId);
        // DC-P2-009: Mask secrets — the list endpoint must never reveal key secrets.
        List<Map<String, Object>> maskedKeys = keys.stream()
            .map(key -> {
                Map<String, Object> masked = new LinkedHashMap<>(key);
                masked.remove("secret");
                masked.put("secretRevealed", false);
                return (Map<String, Object>) masked;
            })
            .toList();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("tenantId", tenantId);
        envelope.put("keys", maskedKeys);
        envelope.put("count", maskedKeys.size());
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
                Map<String, Object> key = new LinkedHashMap<>();
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
                // rotatedAt is absent until the key is first rotated
                key.put("rotationCount", 0);
                key.put("audit", List.of(
                    Map.of("action", "created", "actor", "settings-handler", "timestamp", now.toString())
                ));
                store.createApiKey(tenantId, key);
                Map<String, Object> response = new LinkedHashMap<>(key);
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
            Map<String, Object> response = new LinkedHashMap<>(keyOpt.get());
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
            Map<String, Object> response = new LinkedHashMap<>(rotated.get());
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
        Map<String, Object> response = new LinkedHashMap<>(store.getProfile(tenantId));
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
                Map<String, Object> current = new LinkedHashMap<>(store.getProfile(tenantId));
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
        Map<String, Object> response = new LinkedHashMap<>(store.getPreferences(tenantId));
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
                Map<String, Object> current = new LinkedHashMap<>(store.getPreferences(tenantId));
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
        Map<String, Object> response = new LinkedHashMap<>(store.getNotificationPreferences(tenantId));
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
                Map<String, Object> current = new LinkedHashMap<>(store.getNotificationPreferences(tenantId));
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

    // ── Admin approval endpoints (P3.5) ─────────────────────────────────────

    /**
     * {@code POST /api/v1/settings/approval-request}
     *
     * <p>Proposes a sensitive settings change that requires admin approval.
     * The request is stored as pending and must be explicitly approved or
     * rejected before it takes effect.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRequestApproval(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                String changeType = String.valueOf(payload.getOrDefault("changeType", "general"));
                String requestId = "approval-" + UUID.randomUUID().toString();
                Map<String, Object> proposed = (Map<String, Object>) payload.getOrDefault("payload", Map.of());

                Map<String, Object> approval = new LinkedHashMap<>();
                approval.put("id", requestId);
                approval.put("tenantId", tenantId);
                approval.put("changeType", changeType);
                approval.put("payload", proposed);
                approval.put("status", "pending");
                approval.put("requestedAt", Instant.now().toString());
                approval.put("requestedBy", payload.getOrDefault("requestedBy", "operator"));
                approval.put("reason", payload.getOrDefault("reason", ""));

                pendingApprovals.computeIfAbsent(tenantId, k -> new java.util.ArrayList<>()).add(approval);

                return Promise.of(http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "requestId", requestId,
                    "status", "pending",
                    "message", "Sensitive settings change requires admin approval",
                    "requestedAt", Instant.now().toString()
                )));
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
            }
        });
    }

    /**
     * {@code GET /api/v1/settings/approvals}
     *
     * <p>Lists pending approval requests for the tenant (admin-only view).
     */
    public Promise<HttpResponse> handleListApprovals(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        List<Map<String, Object>> approvals = pendingApprovals.getOrDefault(tenantId, List.of());
        List<Map<String, Object>> pending = approvals.stream()
            .filter(a -> "pending".equals(a.get("status")))
            .toList();

        return Promise.of(http.jsonResponse(Map.of(
            "tenantId", tenantId,
            "total", approvals.size(),
            "pending", pending.size(),
            "approvals", pending,
            "timestamp", Instant.now().toString()
        )));
    }

    /**
     * {@code POST /api/v1/settings/approvals/:id/approve}
     *
     * <p>Approves a pending settings change and applies it.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleApproveRequest(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String requestId = request.getPathParameter("id");
        List<Map<String, Object>> approvals = pendingApprovals.getOrDefault(tenantId, new java.util.ArrayList<>());

        for (Map<String, Object> approval : approvals) {
            if (requestId.equals(approval.get("id")) && "pending".equals(approval.get("status"))) {
                approval.put("status", "approved");
                approval.put("approvedAt", Instant.now().toString());
                String adminId = request.getHeader(io.activej.http.HttpHeaders.of("X-Admin-Id"));
                if (adminId == null || adminId.isBlank()) adminId = "admin";
                approval.put("approvedBy", adminId);

                String changeType = String.valueOf(approval.get("changeType"));
                Map<String, Object> proposed = (Map<String, Object>) approval.getOrDefault("payload", Map.of());

                switch (changeType) {
                    case "security" -> {
                        Map<String, Object> current = new LinkedHashMap<>(store.getSecuritySettings(tenantId));
                        proposed.forEach((k, v) -> { if (v != null) current.put(k, v); });
                        store.updateSecuritySettings(tenantId, current);
                    }
                    case "general" -> {
                        Map<String, Object> current = new LinkedHashMap<>(store.getGeneralSettings(tenantId));
                        proposed.forEach((k, v) -> { if (v != null) current.put(k, v); });
                        store.updateGeneralSettings(tenantId, current);
                    }
                    case "profile" -> {
                        Map<String, Object> current = new LinkedHashMap<>(store.getProfile(tenantId));
                        proposed.forEach((k, v) -> { if (v != null) current.put(k, v); });
                        store.updateProfile(tenantId, current);
                    }
                    default -> {
                        // No-op for unknown change types
                    }
                }

                return Promise.of(http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "requestId", requestId,
                    "status", "approved",
                    "changeType", changeType,
                    "appliedAt", Instant.now().toString()
                )));
            }
        }
        return Promise.of(http.errorResponse(404, "Approval request not found or already resolved: " + requestId));
    }

    /**
     * {@code POST /api/v1/settings/approvals/:id/reject}
     *
     * <p>Rejects a pending settings change.
     */
    public Promise<HttpResponse> handleRejectRequest(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        String requestId = request.getPathParameter("id");
        List<Map<String, Object>> approvals = pendingApprovals.getOrDefault(tenantId, new java.util.ArrayList<>());

        for (Map<String, Object> approval : approvals) {
            if (requestId.equals(approval.get("id")) && "pending".equals(approval.get("status"))) {
                approval.put("status", "rejected");
                approval.put("rejectedAt", Instant.now().toString());
                String rejectedBy = request.getHeader(io.activej.http.HttpHeaders.of("X-Admin-Id"));
                if (rejectedBy == null || rejectedBy.isBlank()) rejectedBy = "admin";
                approval.put("rejectedBy", rejectedBy);

                return Promise.of(http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "requestId", requestId,
                    "status", "rejected",
                    "rejectedAt", Instant.now().toString()
                )));
            }
        }
        return Promise.of(http.errorResponse(404, "Approval request not found or already resolved: " + requestId));
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

    /**
     * Validates security settings values.
     *
     * <p>DC-P2-009: Enforces that security-sensitive fields are within safe bounds.
     */
    private boolean validateSecuritySettings(Map<String, Object> settings) {
        Object sessionTimeout = settings.get("sessionTimeout");
        if (sessionTimeout instanceof Number n) {
            int timeout = n.intValue();
            if (timeout < 1 || timeout > 10080) { // 1 minute to 7 days
                return false;
            }
        }
        return true;
    }
}
