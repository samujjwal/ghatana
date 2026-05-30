/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ObjectMapper objectMapper;

    public FeatureToggleController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
        this.objectMapper = new ObjectMapper();
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
        return TenantExtractor.fromHttpOrThrow(request);
    }

    private FeatureFlagService.FeatureFlag parseFlag(String json) throws JsonProcessingException {
        Map<String, Object> jsonMap = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        String key = requireString(jsonMap, "key");
        String name = requireString(jsonMap, "name");
        String tenantId = requireString(jsonMap, "tenantId");
        String createdBy = requireString(jsonMap, "createdBy");
        int rolloutPercentage = parseRolloutPercentage(jsonMap.get("rolloutPercentage"));

        return new FeatureFlagService.FeatureFlag(
            key,
            name,
            optionalString(jsonMap.get("description")),
            tenantId,
            Boolean.TRUE.equals(jsonMap.get("enabled")),
            parseTargetRules(jsonMap.get("rules")),
            rolloutPercentage,
            parseStringSet(jsonMap.get("variants")),
            optionalString(jsonMap.get("defaultVariant")),
            java.time.Instant.now(),
            java.time.Instant.now(),
            createdBy
        );
    }

    private List<FeatureFlagService.TargetRule> parseTargetRules(Object rulesValue) {
        if (!(rulesValue instanceof List<?> rules)) {
            return List.of();
        }

        return rules.stream()
            .filter(Map.class::isInstance)
            .map(rule -> (Map<?, ?>) rule)
            .map(rule -> new FeatureFlagService.TargetRule(
                requireString(rule, "name"),
                parseCondition(rule.get("condition")),
                parseAction(rule.get("action")),
                rule.get("variant") != null ? String.valueOf(rule.get("variant")) : null
            ))
            .toList();
    }

    private FeatureFlagService.Condition parseCondition(Object conditionValue) {
        if (conditionValue == null) {
            return null;
        }
        if (!(conditionValue instanceof Map<?, ?> condition)) {
            throw new IllegalArgumentException("condition must be an object");
        }

        return new FeatureFlagService.Condition(
            requireString(condition, "attribute"),
            parseOperator(condition.get("operator")),
            condition.get("value")
        );
    }

    private FeatureFlagService.Action parseAction(Object value) {
        return parseEnum(value, FeatureFlagService.Action.class, "action");
    }

    private FeatureFlagService.Condition.Operator parseOperator(Object value) {
        return parseEnum(value, FeatureFlagService.Condition.Operator.class, "operator");
    }

    private <T extends Enum<T>> T parseEnum(Object value, Class<T> enumType, String fieldName) {
        String enumValue = requireStringValue(value, fieldName);
        try {
            return Enum.valueOf(enumType, enumValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + enumValue);
        }
    }

    private String requireString(Map<?, ?> source, String fieldName) {
        return requireStringValue(source.get(fieldName), fieldName);
    }

    private String requireStringValue(Object value, String fieldName) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return text;
    }

    private String optionalString(Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Optional string field has invalid type");
        }
        return text;
    }

    private int parseRolloutPercentage(Object value) {
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("rolloutPercentage must be numeric");
        }
        int percentage = number.intValue();
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        return percentage;
    }

    private Set<String> parseStringSet(Object value) {
        if (!(value instanceof Iterable<?> values)) {
            return Set.of();
        }

        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (Object item : values) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return Set.copyOf(result);
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

    private Map<String, Object> parseJson(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"Failed to serialize response\"}";
        }
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
