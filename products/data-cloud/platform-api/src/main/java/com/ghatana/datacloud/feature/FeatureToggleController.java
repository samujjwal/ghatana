/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * REST controller for feature toggle management.
 *
 * <p>Handles HTTP requests for feature flag CRUD operations.
 *
 * @doc.type class
 * @doc.purpose REST controller for feature toggles
 * @doc.layer product
 * @doc.pattern Controller
 */
public class FeatureToggleController implements AsyncServlet {

    private final FeatureFlagService featureFlagService;

    public FeatureToggleController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) {
        String path = request.getRelativePath();
        String method = request.getMethod().toString();

        if ("POST".equals(method) && path.endsWith("/flags")) {
            return createFlag(request);
        } else if ("GET".equals(method) && path.matches(".*/flags/[^/]+")) {
            return getFlag(request, extractId(path));
        } else if ("GET".equals(method) && path.endsWith("/flags")) {
            return listFlags(request);
        } else if ("PUT".equals(method) && path.matches(".*/flags/[^/]+/toggle")) {
            return toggleFlag(request, extractId(path));
        } else if ("DELETE".equals(method) && path.matches(".*/flags/[^/]+")) {
            return deleteFlag(request, extractId(path));
        } else if ("POST".equals(method) && path.endsWith("/evaluate")) {
            return evaluateFlag(request);
        }

        return Promise.of(HttpResponse.ofCode(404).withPlainText("Not Found"));
    }

    private Promise<HttpResponse> createFlag(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    // Parse request body and create flag
                    FeatureFlagService.FeatureFlag flag = parseFlag(body.getStringUtf8());
                    return featureFlagService.createFlag(flag)
                        .then(saved -> Promise.of(HttpResponse.ok200()
                            .withJson(toJson(saved))));
                } catch (Exception e) {
                    return Promise.of(HttpResponse.ofCode(400)
                        .withPlainText("Invalid request: " + e.getMessage()));
                }
            });
    }

    private Promise<HttpResponse> getFlag(HttpRequest request, String key) {
        return featureFlagService.getFlag(key)
            .then(flagOpt -> {
                if (flagOpt.isPresent()) {
                    return Promise.of(HttpResponse.ok200()
                        .withJson(toJson(flagOpt.get())));
                } else {
                    return Promise.of(HttpResponse.ofCode(404)
                        .withPlainText("Flag not found"));
                }
            });
    }

    private Promise<HttpResponse> listFlags(HttpRequest request) {
        String tenantId = extractTenantId(request);
        return featureFlagService.listFlags(tenantId)
            .then(flags -> Promise.of(HttpResponse.ok200()
                .withJson(toJson(Map.of("flags", flags, "total", flags.size())))));
    }

    private Promise<HttpResponse> toggleFlag(HttpRequest request, String key) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.getStringUtf8());
                    boolean enabled = Boolean.TRUE.equals(bodyMap.get("enabled"));
                    return featureFlagService.toggle(key, enabled)
                        .then(updated -> Promise.of(HttpResponse.ok200()
                            .withJson(toJson(updated))));
                } catch (Exception e) {
                    return Promise.of(HttpResponse.ofCode(400)
                        .withPlainText("Invalid request"));
                }
            });
    }

    private Promise<HttpResponse> deleteFlag(HttpRequest request, String key) {
        return featureFlagService.deleteFlag(key)
            .then(v -> Promise.of(HttpResponse.ok200()
                .withJson(toJson(Map.of("deleted", true)))));
    }

    private Promise<HttpResponse> evaluateFlag(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.getStringUtf8());
                    String key = (String) bodyMap.get("key");
                    FeatureFlagService.FeatureContext context = parseContext(bodyMap);

                    return featureFlagService.isEnabled(key, context)
                        .then(enabled -> Promise.of(HttpResponse.ok200()
                            .withJson(toJson(Map.of(
                                "key", key,
                                "enabled", enabled,
                                "timestamp", System.currentTimeMillis()
                            )))));
                } catch (Exception e) {
                    return Promise.of(HttpResponse.ofCode(400)
                        .withPlainText("Invalid request"));
                }
            });
    }

    private String extractId(String path) {
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("flags".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader("X-Tenant-ID");
        return tenantId != null ? tenantId : "default-tenant";
    }

    private FeatureFlagService.FeatureFlag parseFlag(String json) {
        // Simplified parsing - in production use Jackson
        return new FeatureFlagService.FeatureFlag(
            "new-flag", "New Flag", "", "tenant-alpha",
            false, java.util.List.of(), 0, java.util.Set.of(), "",
            java.time.Instant.now(), java.time.Instant.now(), "user"
        );
    }

    private FeatureFlagService.FeatureContext parseContext(Map<String, Object> bodyMap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) bodyMap.getOrDefault("context", Map.of());

        return FeatureFlagService.FeatureContext.builder()
            .userId((String) ctx.get("userId"))
            .tenantId((String) ctx.get("tenantId"))
            .attributes((Map<String, Object>) ctx.getOrDefault("attributes", Map.of()))
            .build();
    }

    private Map<String, Object> parseJson(String json) {
        // Simplified - in production use Jackson
        return Map.of();
    }

    private String toJson(Object obj) {
        // Simplified - in production use Jackson
        return "{}";
    }
}
