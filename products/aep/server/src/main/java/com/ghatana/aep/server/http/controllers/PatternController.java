/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.http.HttpHelper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Controller for pattern management endpoints.
 *
 * @doc.type class
 * @doc.purpose Pattern CRUD operations
 * @doc.layer product
 * @doc.pattern Service
 */
public class PatternController {

    private static final Logger log = LoggerFactory.getLogger(PatternController.class);

    private final AepEngine engine;

    public PatternController(AepEngine engine) {
        this.engine = engine;
    }

    public Promise<HttpResponse> handleListPatterns(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";

        return engine.listPatterns(tenantId)
            .map(patterns -> HttpHelper.jsonResponse(Map.of(
                "patterns", patterns,
                "count", patterns.size(),
                "timestamp", Instant.now().toString()
            )));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRegisterPattern(HttpRequest request) {
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> patternData = HttpHelper.mapper().readValue(body, Map.class);

                String tenantId = (String) patternData.getOrDefault("tenantId", "default");
                String name = (String) patternData.get("name");
                String description = (String) patternData.getOrDefault("description", "");
                String typeStr = (String) patternData.getOrDefault("type", "CUSTOM");
                Map<String, Object> config =
                    (Map<String, Object>) patternData.getOrDefault("config", Map.of());

                AepEngine.PatternType type = AepEngine.PatternType.valueOf(typeStr.toUpperCase());
                AepEngine.PatternDefinition definition =
                    new AepEngine.PatternDefinition(name, description, type, config);

                return engine.registerPattern(tenantId, definition)
                    .map(pattern -> HttpHelper.jsonResponse(Map.of(
                        "pattern", Map.of(
                            "id", pattern.id(),
                            "name", pattern.name(),
                            "type", pattern.type().name()
                        ),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                log.error("Error registering pattern", e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid pattern data: " + e.getMessage()));
            }
        }, e -> {
            log.error("Failed to read pattern body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    public Promise<HttpResponse> handleGetPattern(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        String patternId = request.getPathParameter("patternId");

        return engine.getPattern(tenantId, patternId)
            .map(optPattern -> {
                if (optPattern.isPresent()) {
                    AepEngine.Pattern pattern = optPattern.get();
                    return HttpHelper.jsonResponse(Map.of(
                        "pattern", Map.of(
                            "id", pattern.id(),
                            "name", pattern.name(),
                            "type", pattern.type().name(),
                            "createdAt", pattern.createdAt().toString()
                        )
                    ));
                } else {
                    return HttpHelper.errorResponse(404, "Pattern not found: " + patternId);
                }
            });
    }

    public Promise<HttpResponse> handleDeletePattern(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";
        String patternId = request.getPathParameter("patternId");

        return engine.deletePattern(tenantId, patternId)
            .map(v -> HttpHelper.jsonResponse(Map.of(
                "deleted", true,
                "patternId", patternId,
                "timestamp", Instant.now().toString()
            )));
    }
}
