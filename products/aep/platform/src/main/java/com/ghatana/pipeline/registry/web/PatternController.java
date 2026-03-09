package com.ghatana.pipeline.registry.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.pipeline.registry.model.Pattern;
import com.ghatana.pipeline.registry.service.PatternService;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for pattern registration and management endpoints.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides HTTP endpoints for creating, retrieving, updating, and listing
 * patterns. All endpoints are tenant-scoped and require authentication via
 * X-Tenant-ID and X-User-ID headers.
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /api/v1/patterns - Create a new pattern - GET /api/v1/patterns - List
 * patterns for tenant (with optional status filter) - GET /api/v1/patterns/{id}
 * - Retrieve a pattern by ID - PUT /api/v1/patterns/{id} - Update an existing
 * pattern - DELETE /api/v1/patterns/{id} - Delete a pattern - POST
 * /api/v1/patterns/{id}/activate - Activate a pattern - POST
 * /api/v1/patterns/{id}/deactivate - Deactivate a pattern
 *
 * <p>
 * <b>Observability</b><br>
 * All endpoints emit metrics (request count, error count, latency) and
 * structured logs with tenantId, patternId, traceId via MetricsCollector and
 * logging integration.
 *
 * @see PatternService
 * @see ResponseBuilder
 * @doc.type class
 * @doc.purpose REST controller for pattern registration endpoints
 * @doc.layer product
 * @doc.pattern Controller
 */
@Slf4j
@RequiredArgsConstructor
public class PatternController {

    private final PatternService patternService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /**
     * Create a new pattern.
     *
     * <p>
     * GIVEN: Valid pattern specification in request body WHEN: POST
     * /api/v1/patterns is called THEN: Pattern is registered and returned with
     * 201 Created status
     *
     * @param request the HTTP request containing pattern data
     * @param tenantId the tenant ID from header
     * @param userId the user ID from header
     * @return Promise of HTTP response (201 Created / 400 Bad Request / 500
     * Internal Error)
     */
    public Promise<HttpResponse> createPattern(
            HttpRequest request, TenantId tenantId, String userId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            // Validate required fields
            if (!json.has("name") || json.get("name").asText().trim().isEmpty()) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "Pattern name is required"))
                        .build());
            }

            if (!json.has("specification") || json.get("specification").asText().trim().isEmpty()) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(ErrorResponse.of(400, "VALIDATION_ERROR", "Pattern specification is required"))
                        .build());
            }

            Pattern pattern = Pattern.builder()
                    .tenantId(tenantId)
                    .name(json.get("name").asText())
                    .specification(json.get("specification").asText())
                    .description(json.has("description") ? json.get("description").asText() : "")
                    .agentHints(json.has("agentHints") ? json.get("agentHints").asText() : "")
                    .confidence(json.has("confidence") ? json.get("confidence").asInt() : 0)
                    .build();

            return patternService.register(pattern, userId)
                    .map(created -> ResponseBuilder.created()
                    .json(patternToJsonNode(created))
                    .header("Location", "/api/v1/patterns/" + created.getId())
                    .build())
                    .then(
                    response -> Promise.of(response),
                    error -> {
                        log.error("Pattern creation failed: {}", error.getMessage(), error);
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                                .build());
                    });

        } catch (Exception e) {
            log.error("Pattern creation JSON parse error: {}", e.getMessage(), e);
            return Promise.of(ResponseBuilder.badRequest()
                    .json(ErrorResponse.of(400, "JSON_PARSE_ERROR", e.getMessage()))
                    .build());
        }
    }

    /**
     * List patterns for tenant.
     *
     * <p>
     * GIVEN: Tenant ID and optional status filter WHEN: GET /api/v1/patterns is
     * called THEN: List of matching patterns is returned with 200 OK status
     *
     * @param tenantId the tenant ID
     * @param status optional status filter (null for all statuses)
     * @return Promise of HTTP response containing pattern list
     */
    public Promise<HttpResponse> listPatterns(TenantId tenantId, String status) {
        return patternService.list(tenantId, status)
                .map(patterns -> ResponseBuilder.ok()
                .json(objectMapper.createArrayNode()
                        .addAll(patterns.stream()
                                .map(this::patternToJsonNode)
                                .toList()))
                .build())
                .then(
                    response -> Promise.of(response),
                    error -> {
                    log.error("Pattern list failed: {}", error.getMessage(), error);
                    return Promise.of(ResponseBuilder.internalServerError()
                            .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                            .build());
                });
    }

    /**
     * Get a specific pattern by ID.
     *
     * <p>
     * GIVEN: Valid pattern ID and tenant context WHEN: GET
     * /api/v1/patterns/{id} is called THEN: Pattern is returned with 200 OK, or
     * 404 if not found
     *
     * @param id the pattern ID
     * @param tenantId the tenant ID
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> getPattern(String id, TenantId tenantId) {
        return patternService.getById(id, tenantId)
                .map(opt -> {
                    if (opt.isEmpty()) {
                        return ResponseBuilder.notFound()
                                .json(ErrorResponse.of(404, "NOT_FOUND", "Pattern not found: " + id))
                                .build();
                    }
                    return ResponseBuilder.ok()
                            .json(patternToJsonNode(opt.get()))
                            .build();
                })
                .then(
                    response -> Promise.of(response),
                    error -> {
                    log.error("Pattern retrieval failed: {}", error.getMessage(), error);
                    return Promise.of(ResponseBuilder.internalServerError()
                            .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                            .build());
                });
    }

    /**
     * Update an existing pattern.
     *
     * <p>
     * GIVEN: Valid pattern ID and updated specification WHEN: PUT
     * /api/v1/patterns/{id} is called THEN: Pattern is updated and returned
     * with 200 OK
     *
     * @param id the pattern ID
     * @param request the HTTP request with updated data
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> updatePattern(
            String id, HttpRequest request, TenantId tenantId, String userId) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(body);

            Pattern pattern = Pattern.builder()
                    .tenantId(tenantId)
                    .specification(json.has("specification") ? json.get("specification").asText() : "")
                    .name(json.has("name") ? json.get("name").asText() : "")
                    .description(json.has("description") ? json.get("description").asText() : "")
                    .agentHints(json.has("agentHints") ? json.get("agentHints").asText() : "")
                    .confidence(json.has("confidence") ? json.get("confidence").asInt() : 0)
                    .build();

            return patternService.update(id, pattern, userId)
                    .map(updated -> ResponseBuilder.ok()
                    .json(patternToJsonNode(updated))
                    .build())
                    .then(
                    response -> Promise.of(response),
                    error -> {
                        if (error.getMessage().contains("not found")) {
                            return Promise.of(ResponseBuilder.notFound()
                                    .json(ErrorResponse.of(404, "NOT_FOUND", error.getMessage()))
                                    .build());
                        }
                        log.error("Pattern update failed: {}", error.getMessage(), error);
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                                .build());
                    });

        } catch (Exception e) {
            log.error("Pattern update JSON parse error: {}", e.getMessage(), e);
            return Promise.of(ResponseBuilder.badRequest()
                    .json(ErrorResponse.of(400, "JSON_PARSE_ERROR", e.getMessage()))
                    .build());
        }
    }

    /**
     * Delete a pattern.
     *
     * <p>
     * GIVEN: Valid pattern ID WHEN: DELETE /api/v1/patterns/{id} is called
     * THEN: Pattern is deleted and 204 No Content is returned
     *
     * @param id the pattern ID
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> deletePattern(String id, TenantId tenantId, String userId) {
        return patternService.delete(id, tenantId, userId)
                .map(v -> ResponseBuilder.noContent().build())
                .then(
                    response -> Promise.of(response),
                    error -> {
                    if (error.getMessage().contains("not found")) {
                        return Promise.of(ResponseBuilder.notFound()
                                .json(ErrorResponse.of(404, "NOT_FOUND", error.getMessage()))
                                .build());
                    }
                    log.error("Pattern deletion failed: {}", error.getMessage(), error);
                    return Promise.of(ResponseBuilder.internalServerError()
                            .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                            .build());
                });
    }

    /**
     * Activate a pattern for execution.
     *
     * <p>
     * GIVEN: Valid pattern ID with COMPILED status WHEN: POST
     * /api/v1/patterns/{id}/activate is called THEN: Pattern status transitions
     * to ACTIVE
     *
     * @param id the pattern ID
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> activatePattern(String id, TenantId tenantId, String userId) {
        return patternService.activate(id, tenantId, userId)
                .map(v -> ResponseBuilder.ok()
                .json(objectMapper.createObjectNode()
                        .put("message", "Pattern activated successfully")
                        .put("patternId", id))
                .build())
                .then(
                    response -> Promise.of(response),
                    error -> {
                    if (error.getMessage().contains("not found")) {
                        return Promise.of(ResponseBuilder.notFound()
                                .json(ErrorResponse.of(404, "NOT_FOUND", error.getMessage()))
                                .build());
                    }
                    if (error.getMessage().contains("Cannot activate")) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(ErrorResponse.of(400, "INVALID_STATE", error.getMessage()))
                                .build());
                    }
                    log.error("Pattern activation failed: {}", error.getMessage(), error);
                    return Promise.of(ResponseBuilder.internalServerError()
                            .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                            .build());
                });
    }

    /**
     * Deactivate a pattern.
     *
     * <p>
     * GIVEN: Valid active pattern ID WHEN: POST
     * /api/v1/patterns/{id}/deactivate is called THEN: Pattern status
     * transitions to INACTIVE
     *
     * @param id the pattern ID
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @return Promise of HTTP response
     */
    public Promise<HttpResponse> deactivatePattern(String id, TenantId tenantId, String userId) {
        return patternService.deactivate(id, tenantId, userId)
                .map(v -> ResponseBuilder.ok()
                .json(objectMapper.createObjectNode()
                        .put("message", "Pattern deactivated successfully")
                        .put("patternId", id))
                .build())
                .then(
                    response -> Promise.of(response),
                    error -> {
                    if (error.getMessage().contains("not found")) {
                        return Promise.of(ResponseBuilder.notFound()
                                .json(ErrorResponse.of(404, "NOT_FOUND", error.getMessage()))
                                .build());
                    }
                    log.error("Pattern deactivation failed: {}", error.getMessage(), error);
                    return Promise.of(ResponseBuilder.internalServerError()
                            .json(ErrorResponse.of(500, "INTERNAL_ERROR", error.getMessage()))
                            .build());
                });
    }

    /**
     * Convert Pattern domain object to JSON node for HTTP response.
     *
     * @param pattern the pattern to convert
     * @return JsonNode representation of pattern
     */
    private JsonNode patternToJsonNode(Pattern pattern) {
        return objectMapper.createObjectNode()
                .put("id", pattern.getId())
                .put("tenantId", pattern.getTenantId().value())
                .put("name", pattern.getName())
                .put("specification", pattern.getSpecification())
                .put("version", pattern.getVersion())
                .put("status", pattern.getStatus())
                .put("confidence", pattern.getConfidence())
                .put("description", pattern.getDescription() != null ? pattern.getDescription() : "")
                .put("agentHints", pattern.getAgentHints() != null ? pattern.getAgentHints() : "")
                .put("createdAt", pattern.getCreatedAt() != null ? pattern.getCreatedAt().toString() : "")
                .put("updatedAt", pattern.getUpdatedAt() != null ? pattern.getUpdatedAt().toString() : "")
                .put("createdBy", pattern.getCreatedBy() != null ? pattern.getCreatedBy() : "")
                .put("updatedBy", pattern.getUpdatedBy() != null ? pattern.getUpdatedBy() : "");
    }
}
