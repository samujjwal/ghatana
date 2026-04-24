/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
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

        return notFound();
    }

    private Promise<HttpResponse> createFlag(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    FeatureFlagService.FeatureFlag flag = parseFlag(body.asString(StandardCharsets.UTF_8));
                    return featureFlagService.createFlag(flag)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request: " + e.getMessage());
                }
            });
    }

    private Promise<HttpResponse> getFlag(HttpRequest request, String key) {
        return featureFlagService.getFlag(key)
            .then(flagOpt -> {
                if (flagOpt.isPresent()) {
                    return okJson(flagOpt.get());
                }
                return Promise.of(HttpResponse.ofCode(404).withPlainText("Flag not found").build());
            });
    }

    private Promise<HttpResponse> listFlags(HttpRequest request) {
        String tenantId = extractTenantId(request);
        return featureFlagService.listFlags(tenantId)
            .then(flags -> okJson(Map.of("flags", flags, "total", flags.size())));
    }

    private Promise<HttpResponse> toggleFlag(HttpRequest request, String key) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    boolean enabled = Boolean.TRUE.equals(bodyMap.get("enabled"));
                    return featureFlagService.toggle(key, enabled)
                        .then(this::okJson);
                } catch (Exception e) {
                    return badRequest("Invalid request");
                }
            });
    }

    private Promise<HttpResponse> deleteFlag(HttpRequest request, String key) {
        return featureFlagService.deleteFlag(key)
            .then(v -> okJson(Map.of("deleted", true)));
    }

    private Promise<HttpResponse> evaluateFlag(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    Map<String, Object> bodyMap = parseJson(body.asString(StandardCharsets.UTF_8));
                    String key = (String) bodyMap.get("key");
                    FeatureFlagService.FeatureContext context = parseContext(bodyMap);

                    return featureFlagService.isEnabled(key, context)
                        .then(enabled -> okJson(Map.of(
                            "key", key,
                            "enabled", enabled,
                            "timestamp", System.currentTimeMillis()
                        )));
                } catch (Exception e) {
                    return badRequest("Invalid request");
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
        return TenantExtractor.fromHttpOrDefault(request, "default-tenant");
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
        Object attributes = ctx.getOrDefault("attributes", Map.of());

        return FeatureFlagService.FeatureContext.builder()
            .userId((String) ctx.get("userId"))
            .tenantId((String) ctx.get("tenantId"))
            .attributes(attributes instanceof Map<?, ?> attributeMap
                ? attributeMap.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    Map.Entry::getValue
                ))
                : Map.of())
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

    private Promise<HttpResponse> okJson(Object payload) {
        return Promise.of(HttpResponse.ok200().withJson(toJson(payload)).build());
    }

    private Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400).withPlainText(message).build());
    }

    private Promise<HttpResponse> notFound() {
        return Promise.of(HttpResponse.ofCode(404).withPlainText("Not Found").build());
    }
}
