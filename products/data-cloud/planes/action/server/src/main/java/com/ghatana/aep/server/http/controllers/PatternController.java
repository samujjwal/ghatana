/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.server.store.DataCloudPatternStore;
import com.ghatana.pattern.api.model.PatternMetadata;
import com.ghatana.pattern.api.model.PatternSpecification;
import com.ghatana.pattern.api.model.PatternStatus;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    @Nullable
    private final DataCloudPatternStore patternStore;

    public PatternController(AepEngine engine) {
        this(engine, null);
    }

    public PatternController(AepEngine engine, @Nullable DataCloudPatternStore patternStore) {
        this.engine = engine;
        this.patternStore = patternStore;
    }

    public Promise<HttpResponse> handleListPatterns(HttpRequest request) {
        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null) tenantId = "default";

        if (patternStore != null) {
            PatternStatus status = null;
            String rawStatus = request.getQueryParameter("status");
            if (rawStatus != null && !rawStatus.isBlank()) {
                try {
                    status = PatternStatus.valueOf(rawStatus.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Promise.of(HttpHelper.errorResponse(400, "Invalid pattern status: " + rawStatus));
                }
            }

            return patternStore.findByTenant(tenantId, status)
                .map(patterns -> HttpHelper.jsonResponse(Map.of(
                    "patterns", patterns.stream().map(this::toPatternMap).toList(),
                    "count", patterns.size(),
                    "timestamp", Instant.now().toString()
                )));
        }

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
                    .then(pattern -> {
                        if (patternStore == null) {
                            return Promise.of(patternResponse(pattern.id(), pattern.name(), pattern.type().name()));
                        }

                        PatternSpecification specification = buildSpecification(
                            pattern.id(),
                            tenantId,
                            name,
                            description,
                            type,
                            config,
                            patternData
                        );

                        return patternStore.save(specification)
                            .map(saved -> patternResponse(saved.getId().toString(), saved.getName(), type.name()))
                            .then(
                                Promise::of,
                                error -> engine.deletePattern(tenantId, pattern.id())
                                    .map(ignored -> HttpHelper.errorResponse(
                                        500,
                                        "Failed to persist pattern: " + error.getMessage()))
                            );
                    });
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

        if (patternStore != null) {
            UUID id;
            try {
                id = UUID.fromString(patternId);
            } catch (IllegalArgumentException e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid pattern ID: " + patternId));
            }

            return patternStore.findByTenantAndId(tenantId, id)
                .map(optPattern -> optPattern
                    .<HttpResponse>map(pattern -> HttpHelper.jsonResponse(Map.of("pattern", toPatternMap(pattern))))
                    .orElseGet(() -> HttpHelper.errorResponse(404, "Pattern not found: " + patternId)));
        }

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
        final String resolvedTenantId = tenantId;
        String patternId = request.getPathParameter("patternId");

        if (patternStore != null) {
            UUID id;
            try {
                id = UUID.fromString(patternId);
            } catch (IllegalArgumentException e) {
                return Promise.of(HttpHelper.errorResponse(400, "Invalid pattern ID: " + patternId));
            }

            return patternStore.delete(resolvedTenantId, id)
                .then(v -> engine.deletePattern(resolvedTenantId, patternId))
                .map(v -> HttpHelper.jsonResponse(Map.of(
                    "deleted", true,
                    "patternId", patternId,
                    "timestamp", Instant.now().toString()
                )));
        }

        return engine.deletePattern(resolvedTenantId, patternId)
            .map(v -> HttpHelper.jsonResponse(Map.of(
                "deleted", true,
                "patternId", patternId,
                "timestamp", Instant.now().toString()
            )));
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleLifecycleTransition(HttpRequest request) {
        if (patternStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Pattern lifecycle operations require DataCloud-backed pattern storage"));
        }

        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        String patternId = request.getPathParameter("patternId");
        String action = request.getPathParameter("action");
        if (action == null || action.isBlank()) {
            action = request.getQueryParameter("action");
        }
        if (action == null || action.isBlank()) {
            return Promise.of(HttpHelper.errorResponse(400, "Lifecycle action is required"));
        }

        UUID id;
        try {
            id = UUID.fromString(patternId);
        } catch (IllegalArgumentException e) {
            return Promise.of(HttpHelper.errorResponse(400, "Invalid pattern ID: " + patternId));
        }

        String normalizedAction = action.trim().toLowerCase();
        PatternStatus targetStatus = mapLifecycleActionToStatus(normalizedAction);

        if (targetStatus == null && !isSimulationAction(normalizedAction)) {
            return Promise.of(HttpHelper.errorResponse(400, "Unsupported lifecycle action: " + action));
        }

        Promise<AepEngine.ProcessingResult> provenancePromise = emitLifecycleEvent(
            tenantId,
            id,
            normalizedAction,
            targetStatus
        );

        if (isSimulationAction(normalizedAction)) {
            return provenancePromise.map(result -> HttpHelper.jsonResponse(Map.of(
                "patternId", id.toString(),
                "action", normalizedAction,
                "simulationRequested", true,
                "eventId", result.eventId(),
                "timestamp", Instant.now().toString()
            )));
        }

        return patternStore.updateStatus(id, targetStatus)
            .then(v -> provenancePromise)
            .map(result -> HttpHelper.jsonResponse(Map.of(
                "patternId", id.toString(),
                "action", normalizedAction,
                "status", targetStatus.name(),
                "transitioned", true,
                "eventId", result.eventId(),
                "timestamp", Instant.now().toString()
            )))
            .then(
                Promise::of,
                error -> {
                    log.error("Failed lifecycle transition for pattern {} action {}", id, normalizedAction, error);
                    return Promise.of(HttpHelper.errorResponse(500,
                        "Lifecycle transition failed: " + error.getMessage()));
                }
            );
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRecordPatternFeedback(HttpRequest request) {
        if (patternStore == null) {
            return Promise.of(HttpHelper.errorResponse(503,
                "Pattern feedback operations require DataCloud-backed pattern storage"));
        }

        String tenantId = request.getQueryParameter("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        String patternId = request.getPathParameter("patternId");
        UUID id;
        try {
            id = UUID.fromString(patternId);
        } catch (IllegalArgumentException e) {
            return Promise.of(HttpHelper.errorResponse(400, "Invalid pattern ID: " + patternId));
        }

        String resolvedTenantId = tenantId;
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = body.isBlank()
                    ? Map.of()
                    : HttpHelper.mapper().readValue(body, Map.class);

                String signal = stringValue(payload.get("signal"));
                if (signal == null) {
                    return Promise.of(HttpHelper.errorResponse(400, "Feedback signal is required"));
                }

                Map<String, Object> feedback = new HashMap<>();
                feedback.put("signal", signal);
                String source = stringValue(payload.get("source"));
                if (source != null) {
                    feedback.put("source", source);
                }
                Object score = payload.get("score");
                if (score instanceof Number number) {
                    feedback.put("score", number.doubleValue());
                }
                String note = stringValue(payload.get("note"));
                if (note != null) {
                    feedback.put("note", note);
                }

                return emitFeedbackEvent(resolvedTenantId, id, feedback)
                    .map(result -> HttpHelper.jsonResponse(Map.of(
                        "patternId", id.toString(),
                        "recorded", true,
                        "eventId", result.eventId(),
                        "timestamp", Instant.now().toString()
                    )));
            } catch (Exception e) {
                return Promise.of(HttpHelper.errorResponse(400,
                    "Invalid feedback payload: " + e.getMessage()));
            }
        }, e -> Promise.of(HttpHelper.errorResponse(400, "Failed to read request body")));
    }

    private HttpResponse patternResponse(String id, String name, String type) {
        return HttpHelper.jsonResponse(Map.of(
            "pattern", Map.of(
                "id", id,
                "name", name,
                "type", type
            ),
            "timestamp", Instant.now().toString()
        ));
    }

    private Promise<AepEngine.ProcessingResult> emitLifecycleEvent(
            String tenantId,
            UUID patternId,
            String action,
            PatternStatus targetStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("patternId", patternId.toString());
        payload.put("action", action);
        payload.put("requestedAt", Instant.now().toString());
        if (targetStatus != null) {
            payload.put("targetStatus", targetStatus.name());
        }

        AepEngine.Event event = new AepEngine.Event(
            "pattern.lifecycle.transition",
            Map.copyOf(payload),
            Map.of("patternId", patternId.toString()),
            Instant.now()
        );
        return engine.process(tenantId, event);
    }

    private Promise<AepEngine.ProcessingResult> emitFeedbackEvent(
            String tenantId,
            UUID patternId,
            Map<String, Object> feedback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("patternId", patternId.toString());
        payload.put("feedback", Map.copyOf(feedback));
        payload.put("recordedAt", Instant.now().toString());

        AepEngine.Event event = new AepEngine.Event(
            "pattern.feedback.recorded",
            Map.copyOf(payload),
            Map.of("patternId", patternId.toString()),
            Instant.now()
        );
        return engine.process(tenantId, event);
    }

    private PatternStatus mapLifecycleActionToStatus(String action) {
        return switch (action) {
            case "draft", "revise" -> PatternStatus.DRAFT;
            case "validate", "compile" -> PatternStatus.COMPILED;
            case "approve" -> PatternStatus.CANDIDATE;
            case "activate", "monitor" -> PatternStatus.ACTIVE;
            case "deactivate" -> PatternStatus.INACTIVE;
            case "retire", "deprecate" -> PatternStatus.DEPRECATED;
            case "suspend" -> PatternStatus.SUSPENDED;
            case "archive" -> PatternStatus.ARCHIVED;
            default -> null;
        };
    }

    private boolean isSimulationAction(String action) {
        return "simulate".equals(action) || "replay".equals(action);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private PatternSpecification buildSpecification(
            String patternId,
            String tenantId,
            String name,
            String description,
            AepEngine.PatternType type,
            Map<String, Object> config,
            Map<String, Object> rawPatternData) {
        List<String> eventTypes = rawStringList(rawPatternData.get("eventTypes"));
        if (eventTypes.isEmpty()) {
            eventTypes = rawStringList(config.get("eventTypes"));
        }
        if (eventTypes.isEmpty()) {
            Object eventType = config.get("eventType");
            if (eventType != null) {
                eventTypes = List.of(String.valueOf(eventType));
            }
        }

        PatternSpecification.Builder builder = PatternSpecification.builder()
            .id(UUID.fromString(patternId))
            .tenantId(tenantId)
            .name(name)
            .description(description)
            .version(((Number) rawPatternData.getOrDefault("version", 1)).intValue())
            .priority(((Number) rawPatternData.getOrDefault("priority", 0)).intValue())
            .activation(true)
            .status(PatternStatus.ACTIVE)
            .eventTypes(eventTypes)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .metadata(Map.of(
                "aepPatternType", type.name(),
                "config", config
            ));

        Object labels = rawPatternData.get("labels");
        if (labels instanceof List<?> list) {
            builder.labels(list.stream().map(String::valueOf).toList());
        }

        Object whereClause = rawPatternData.get("whereClause");
        if (whereClause != null) {
            builder.whereClause(String.valueOf(whereClause));
        }
        return builder.build();
    }

    private Map<String, Object> toPatternMap(PatternMetadata metadata) {
        return Map.ofEntries(
            Map.entry("id", metadata.getId().toString()),
            Map.entry("tenantId", metadata.getTenantId()),
            Map.entry("name", metadata.getName()),
            Map.entry("description", Optional.ofNullable(metadata.getDescription()).orElse("")),
            Map.entry("status", metadata.getStatus().name()),
            Map.entry("priority", metadata.getPriority()),
            Map.entry("eventTypes", Optional.ofNullable(metadata.getEventTypes()).orElse(List.of())),
            Map.entry("createdAt", metadata.getCreatedAt() != null ? metadata.getCreatedAt().toString() : ""),
            Map.entry("updatedAt", metadata.getUpdatedAt() != null ? metadata.getUpdatedAt().toString() : "")
        );
    }

    private List<String> rawStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
