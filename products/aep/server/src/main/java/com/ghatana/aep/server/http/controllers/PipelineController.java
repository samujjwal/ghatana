/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.security.AepInputValidator;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.core.pipeline.NaturalLanguagePipelineService;
import com.ghatana.core.pipeline.NaturalLanguagePipelineService.PipelineSpec;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.platform.domain.auth.TenantId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline management controller.
 *
 * <p>Handles CRUD operations for AEP pipelines including:
 * <ul>
 *   <li>Listing pipelines</li>
 *   <li>Getting pipeline details</li>
 *   <li>Creating pipelines</li>
 *   <li>Updating pipelines</li>
 *   <li>Deleting pipelines</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Pipeline CRUD operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public class PipelineController implements AepController {

    private static final Logger log = LoggerFactory.getLogger(PipelineController.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PipelineRepository pipelineRepository;
    private final ObjectMapper objectMapper;
    /** P3-19: Natural language pipeline service for primary creation mode. */
    private final NaturalLanguagePipelineService nlqService;

    public PipelineController(PipelineRepository pipelineRepository, ObjectMapper objectMapper) {
        this(pipelineRepository, objectMapper, null);
    }

    public PipelineController(PipelineRepository pipelineRepository, ObjectMapper objectMapper,
                             NaturalLanguagePipelineService nlqService) {
        this.pipelineRepository = pipelineRepository;
        this.objectMapper = objectMapper;
        this.nlqService = nlqService;
    }

    @Override
    public String getBasePath() {
        return "/api/v1/pipelines";
    }

    @Override
    public Promise<HttpResponse> handle(HttpRequest request, String path) throws Exception {
        String tenantId = extractTenantId(request);

        HttpMethod method = request.getMethod();
        if (HttpMethod.GET.equals(method)) return handleGet(request, path, tenantId);
        if (HttpMethod.POST.equals(method)) {
            // P3-19: Check if this is an NLQ-based creation (primary mode)
            if (path.equals("/nlq") || path.equals("/nlq/")) {
                return handleNLQCreate(request, tenantId);
            }
            return handlePost(request, tenantId);
        }
        if (HttpMethod.PUT.equals(method)) return handlePut(request, path, tenantId);
        if (HttpMethod.DELETE.equals(method)) return handleDelete(request, path, tenantId);
        return Promise.of(HttpHelper.errorResponse(405, "Method not allowed"));
    }

    private Promise<HttpResponse> handleGet(HttpRequest request, String path, String tenantId) {
        if (path.isEmpty() || path.equals("/")) {
            return handleList(request, tenantId);
        }

        String pipelineId = path.substring(1);
        if (!AepInputValidator.isValidPipelineId(pipelineId)) {
            return Promise.of(errorResponse(400, "Invalid pipeline ID"));
        }

        return pipelineRepository.findById(pipelineId, tenantId)
            .map(opt -> opt.map(this::toJsonResponse)
                .orElseGet(() -> HttpHelper.errorResponse(404, "Pipeline not found")));
    }

    /**
     * P3-19: Primary pipeline creation mode using natural language input.
     * POST /api/v1/pipelines/nlq
     * 
     * Accepts a natural language description and automatically generates a pipeline.
     * This is promoted as the primary creation mode for better UX.
     */
    private Promise<HttpResponse> handleNLQCreate(HttpRequest request, String tenantId) {
        if (nlqService == null) {
            log.warn("[PipelineController] NLQ service not configured for tenant={}", tenantId);
            return Promise.of(HttpHelper.errorResponse(501, "Natural language pipeline service not configured"));
        }

        return request.loadBody()
            .then(buf -> {
                String body = buf.getString(StandardCharsets.UTF_8);

                if (!AepInputValidator.isValidJson(body)) {
                    return Promise.of(errorResponse(400, "Invalid JSON"));
                }

                try {
                    Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
                    String description = asString(payload.get("description"));
                    
                    if (description == null || description.isBlank()) {
                        return Promise.of(errorResponse(400, "Description is required for NLQ pipeline creation"));
                    }

                    // Validate description
                    NaturalLanguagePipelineService.ValidationResult validation = 
                        nlqService.validateDescription(description);
                    if (!validation.valid()) {
                        return Promise.of(HttpHelper.errorResponse(400, 
                            "Invalid description: " + String.join(", ", validation.errors())));
                    }

                    // Generate pipeline from natural language
                    Map<String, Object> context = new HashMap<>();
                    context.put("tenantId", tenantId);
                    if (payload.containsKey("eventType")) {
                        context.put("eventType", payload.get("eventType"));
                    }

                    PipelineSpec spec = nlqService.generatePipeline(description, context);

                    // Create pipeline registration from generated spec
                    String configJson = objectMapper.writeValueAsString(Map.of(
                        "stages", spec.stages(),
                        "eventType", spec.eventType()
                    ));

                    PipelineRegistration pipeline = PipelineRegistration.builder()
                        .id(UUID.randomUUID().toString())
                        .tenantId(TenantId.of(tenantId))
                        .name(spec.name())
                        .description(spec.description())
                        .active(true)
                        .version(1)
                        .config(configJson)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .createdBy("nlq-pipeline-creator")
                        .updatedBy("nlq-pipeline-creator")
                        .build();

                    return pipelineRepository.save(pipeline)
                        .map(saved -> {
                            Map<String, Object> response = new HashMap<>();
                            response.put("pipeline", Map.of(
                                "id", saved.getId(),
                                "name", saved.getName(),
                                "description", saved.getDescription(),
                                "version", saved.getVersion(),
                                "active", saved.isActive()
                            ));
                            response.put("generatedFrom", description);
                            response.put("inferredCapabilities", List.of(spec.eventType()));
                            
                            return HttpResponse.ofCode(201)
                                .withHeader(HttpHeaders.CONTENT_TYPE,
                                    HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                                .withBody(objectMapper.writeValueAsString(response)
                                    .getBytes(StandardCharsets.UTF_8))
                                .build();
                        })
                        .then(Promise::of, e -> {
                            log.error("Failed to create NLQ pipeline for tenant={}", tenantId, e);
                            return Promise.of(errorResponse(500, "Failed to create pipeline: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.error("Error parsing NLQ pipeline create request", e);
                    return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                }
            });
    }

    private Promise<HttpResponse> handleList(HttpRequest request, String tenantId) {
        String limitParam = request.getQueryParameter("limit");
        String offsetParam = request.getQueryParameter("offset");

        int limit = parseIntOrDefault(limitParam, 20);
        int offset = parseIntOrDefault(offsetParam, 0);

        limit = Math.min(limit, 100);
        limit = Math.max(limit, 1);
        offset = Math.max(offset, 0);

        final int effectiveLimit = limit;
        final int effectiveOffset = offset;

        return pipelineRepository.findByTenantId(tenantId)
            .map(allPipelines -> {
                int from = Math.min(effectiveOffset, allPipelines.size());
                int to = Math.min(from + effectiveLimit, allPipelines.size());
                List<PipelineRegistration> page = allPipelines.subList(from, to);
                String body = String.format(
                    "{\"pipelines\":%s,\"total\":%d}",
                    toJsonArray(page), allPipelines.size()
                );
                return HttpResponse.ok200()
                    .withHeader(HttpHeaders.CONTENT_TYPE,
                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                    .withBody(body.getBytes(StandardCharsets.UTF_8))
                    .build();
            });
    }

    private Promise<HttpResponse> handlePost(HttpRequest request, String tenantId) {
        return request.loadBody()
            .then(buf -> {
                String body = buf.getString(StandardCharsets.UTF_8);

                if (!AepInputValidator.isValidJson(body)) {
                    return Promise.of(errorResponse(400, "Invalid JSON"));
                }

                try {
                    Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
                    String name = asString(payload.get("name"));
                    if (name == null || name.isBlank()) {
                        return Promise.of(errorResponse(400, "Pipeline name is required"));
                    }

                    PipelineRegistration pipeline = PipelineRegistration.builder()
                        .id(UUID.randomUUID().toString())
                        .tenantId(TenantId.of(tenantId))
                        .name(name)
                        .description(asString(payload.get("description")))
                        .active(payload.get("active") == null || Boolean.TRUE.equals(payload.get("active")))
                        .version(payload.get("version") instanceof Number n ? n.intValue() : 1)
                        .config(extractConfig(payload))
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .createdBy(asString(payload.getOrDefault("createdBy", "pipeline-controller")))
                        .updatedBy(asString(payload.getOrDefault("updatedBy", "pipeline-controller")))
                        .build();

                    return pipelineRepository.save(pipeline)
                        .map(saved -> {
                        return HttpResponse.ofCode(201)
                            .withHeader(HttpHeaders.CONTENT_TYPE,
                                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                            .withBody(toJsonBody(saved).getBytes(StandardCharsets.UTF_8))
                            .build();
                        })
                        .then(Promise::of, e -> {
                            log.error("Failed to create pipeline", e);
                            return Promise.of(errorResponse(500, "Failed to create pipeline: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.error("Error parsing pipeline create request", e);
                    return Promise.of(errorResponse(400, "Invalid pipeline data: " + e.getMessage()));
                }
            });
    }

    private Promise<HttpResponse> handlePut(HttpRequest request, String path, String tenantId) {
        if (path.isEmpty() || path.equals("/")) {
            return Promise.of(errorResponse(400, "Pipeline ID required"));
        }

        String pipelineId = path.substring(1);
        if (!AepInputValidator.isValidPipelineId(pipelineId)) {
            return Promise.of(errorResponse(400, "Invalid pipeline ID"));
        }

        return request.loadBody()
            .then(buf -> {
                String body = buf.getString(StandardCharsets.UTF_8);

                if (!AepInputValidator.isValidJson(body)) {
                    return Promise.of(errorResponse(400, "Invalid JSON"));
                }

                try {
                    Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);

                    return pipelineRepository.findById(pipelineId, tenantId)
                        .then(optExisting -> {
                            if (optExisting.isEmpty()) {
                                return Promise.of(errorResponse(404, "Pipeline not found: " + pipelineId));
                            }

                            PipelineRegistration existing = optExisting.get();
                            PipelineRegistration updated = existing.newVersion();
                            updated.setId(existing.getId());
                            updated.setVersion(existing.getVersion() + 1);

                            String name = asString(payload.get("name"));
                            if (name != null && !name.isBlank()) {
                                updated.setName(name);
                            }
                            String description = asString(payload.get("description"));
                            if (description != null) {
                                updated.setDescription(description);
                            }
                            if (payload.containsKey("active")) {
                                updated.setActive(Boolean.TRUE.equals(payload.get("active")));
                            }
                            String config = extractConfig(payload);
                            if (config != null) {
                                updated.setConfig(config);
                            }
                            updated.setUpdatedAt(Instant.now());
                            updated.setUpdatedBy(asString(payload.getOrDefault("updatedBy", "pipeline-controller")));

                            return pipelineRepository.save(updated)
                                .map(saved -> HttpResponse.ok200()
                                    .withHeader(HttpHeaders.CONTENT_TYPE,
                                        HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
                                    .withBody(toJsonBody(saved).getBytes(StandardCharsets.UTF_8))
                                    .build());
                        })
                        .then(Promise::of, e -> {
                            log.error("Failed to update pipeline id={}", pipelineId, e);
                            return Promise.of(errorResponse(500, "Failed to update pipeline: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.error("Error parsing pipeline update request", e);
                    return Promise.of(errorResponse(400, "Invalid pipeline data: " + e.getMessage()));
                }
            });
    }

    private Promise<HttpResponse> handleDelete(HttpRequest request, String path, String tenantId) {
        if (path.isEmpty() || path.equals("/")) {
            return Promise.of(errorResponse(400, "Pipeline ID required"));
        }

        String pipelineId = path.substring(1);
        if (!AepInputValidator.isValidPipelineId(pipelineId)) {
            return Promise.of(errorResponse(400, "Invalid pipeline ID"));
        }

        return pipelineRepository.delete(pipelineId, tenantId)
            .map(ignored -> HttpResponse.ofCode(204).build())
            .then(Promise::of, e -> {
                log.error("Failed to delete pipeline id={}", pipelineId, e);
                return Promise.of(errorResponse(404, "Pipeline not found"));
            });
    }

    private String extractTenantId(HttpRequest request) {
        return HttpHelper.resolveTenantId(request);
    }

    private String extractConfig(Map<String, Object> payload) {
        Object rawConfig = payload.get("config");
        try {
            if (rawConfig instanceof String configText && !configText.isBlank()) {
                return configText;
            }
            if (rawConfig != null) {
                return objectMapper.writeValueAsString(rawConfig);
            }
            Object stages = payload.get("stages");
            if (stages instanceof List<?>) {
                return objectMapper.writeValueAsString(Map.of("stages", stages));
            }
            return null;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline config payload", e);
        }
    }

    private HttpResponse toJsonResponse(PipelineRegistration pipeline) {
        return HttpResponse.ok200()
            .withHeader(HttpHeaders.CONTENT_TYPE,
                HttpHeaderValue.ofContentType(ContentType.of(MediaTypes.JSON)))
            .withBody(toJsonBody(pipeline).getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private String toJsonBody(PipelineRegistration pipeline) {
        return String.format(
            "{\"id\":\"%s\",\"name\":\"%s\",\"status\":\"%s\",\"version\":%d,\"active\":%s,\"tenantId\":\"%s\",\"createdAt\":\"%s\",\"updatedAt\":\"%s\"}",
            escapeJson(pipeline.getId()),
            escapeJson(pipeline.getName()),
            pipeline.isActive() ? "ACTIVE" : "INACTIVE",
            pipeline.getVersion(),
            pipeline.isActive(),
            escapeJson(String.valueOf(pipeline.getTenantId())),
            pipeline.getCreatedAt() != null ? pipeline.getCreatedAt() : "",
            pipeline.getUpdatedAt() != null ? pipeline.getUpdatedAt() : ""
        );
    }

    private String toJsonArray(List<PipelineRegistration> pipelines) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pipelines.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonBody(pipelines.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private HttpResponse errorResponse(int code, String message) {
        return HttpHelper.errorResponse(code, message);
    }
}
