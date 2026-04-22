/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP handler for admin settings CRUD operations.
 *
 * <p>Provides endpoints for general and security settings used by
 * the Settings page in the Data Cloud UI (GH-90000 / ADMIN-004).
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

    /** In-memory settings store. Replace with persistent backend (e.g. ConfigManager) in production. */
    private final Map<String, Object> generalSettings = new ConcurrentHashMap<>();
    private final Map<String, Object> securitySettings = new ConcurrentHashMap<>();

    public SettingsHandler(HttpHandlerSupport httpSupport) {
        this.http = Objects.requireNonNull(httpSupport, "httpSupport must not be null");

        // Initialise general settings with production defaults
        generalSettings.put("language", "en");
        generalSettings.put("timezone", "UTC");
        generalSettings.put("dateFormat", "YYYY-MM-DD");
        generalSettings.put("theme", "light");
        generalSettings.put("availableLanguages", List.of("en", "es", "fr", "de", "ja"));
        generalSettings.put("availableTimezones", List.of("UTC", "America/New_York", "Europe/London", "Asia/Tokyo"));

        Map<String, Object> notifications = new ConcurrentHashMap<>();
        notifications.put("email", true);
        notifications.put("inApp", true);
        notifications.put("sms", false);
        generalSettings.put("notifications", notifications);

        Map<String, Object> privacy = new ConcurrentHashMap<>();
        privacy.put("dataRetention", 365);
        privacy.put("analyticsEnabled", true);
        generalSettings.put("privacy", privacy);

        generalSettings.put("twoFactorEnabled", false);
        generalSettings.put("apiKeys", List.of());

        // Initialise security settings with production defaults
        Map<String, Object> passwordPolicy = new ConcurrentHashMap<>();
        passwordPolicy.put("minLength", 12);
        passwordPolicy.put("requireUppercase", true);
        passwordPolicy.put("requireNumbers", true);
        passwordPolicy.put("requireSpecialChars", true);
        securitySettings.put("passwordPolicy", passwordPolicy);

        securitySettings.put("sessionTimeout", 30);
        securitySettings.put("twoFactorRequired", false);

        List<Map<String, Object>> loginHistory = List.of();
        securitySettings.put("loginHistory", loginHistory);

        List<Map<String, Object>> activeSessions = List.of();
        securitySettings.put("activeSessions", activeSessions);

        List<Map<String, Object>> auditTrail = List.of();
        securitySettings.put("auditTrail", auditTrail);
    }

    /**
     * GET /api/v1/settings — return general settings.
     */
    public Promise<HttpResponse> handleGetGeneralSettings(HttpRequest request) {
        return Promise.of(http.jsonResponse(Map.copyOf(generalSettings)));
    }

    /**
     * POST /api/v1/settings — update general settings (merge).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateGeneralSettings(HttpRequest request) {
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                    payload.forEach((key, value) -> {
                        if (value == null) {
                            generalSettings.remove(key);
                        } else {
                            generalSettings.put(key, value);
                        }
                    });

                    // Validate merged settings
                    if (!validateGeneralSettings(generalSettings)) {
                        return Promise.of(http.errorResponse(400, "Invalid general settings value"));
                    }

                    return Promise.of(http.jsonResponse(Map.copyOf(generalSettings)));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Malformed JSON body: " + e.getMessage()));
                }
            });
    }

    /**
     * GET /api/v1/settings/security — return security settings.
     */
    public Promise<HttpResponse> handleGetSecuritySettings(HttpRequest request) {
        return Promise.of(http.jsonResponse(Map.copyOf(securitySettings)));
    }

    /**
     * POST /api/v1/settings/security — update security settings (merge).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleUpdateSecuritySettings(HttpRequest request) {
        return request.loadBody()
            .then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                    payload.forEach((key, value) -> {
                        if (value == null) {
                            securitySettings.remove(key);
                        } else {
                            securitySettings.put(key, value);
                        }
                    });

                    return Promise.of(http.jsonResponse(Map.copyOf(securitySettings)));
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
