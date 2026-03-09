package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.security.TenantExtractor;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.api.dto.CollectionResponse;
import com.ghatana.datacloud.api.dto.CreateCollectionRequest;
import com.ghatana.datacloud.api.dto.DtoMapper;
import com.ghatana.datacloud.api.dto.PaginationListResponse;
import com.ghatana.datacloud.api.dto.UpdateCollectionRequest;
import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.entity.MetaCollection;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for collection management operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides HTTP API endpoints for collection CRUD operations with multi-tenant
 * isolation
 * and RBAC enforcement. Supports creating, reading, updating, and deleting
 * collections.
 *
 * <p>
 * <b>Endpoints</b><br>
 * <ul>
 * <li><b>POST /api/collections:</b> Create collection
 * <li><b>GET /api/collections/{collectionId}:</b> Get collection by ID
 * <li><b>PUT /api/collections/{collectionId}:</b> Update collection
 * <li><b>DELETE /api/collections/{collectionId}:</b> Delete collection
 * <li><b>GET /api/collections:</b> List collections with pagination
 * </ul>
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations extract tenantId from X-Tenant-Id header and enforce tenant
 * isolation.
 *
 * <p>
 * <b>Error Handling</b><br>
 * - 400: Invalid request (missing fields, invalid data)
 * - 401: Unauthorized (missing tenant context)
 * - 403: Forbidden (RBAC violation)
 * - 404: Not found
 * - 500: Internal server error
 *
 * <p>
 * <b>Performance</b><br>
 * All operations async (Promise-based) for non-blocking execution.
 *
 * @see CollectionService
 * @see MetaCollection
 * @doc.type class
 * @doc.purpose REST API controller for collection management
 * @doc.layer product
 * @doc.pattern Controller (API Layer)
 */
public class CollectionController {

        private static final Logger logger = LoggerFactory.getLogger(CollectionController.class);

        private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-Id");
        private static final HttpHeader HEADER_USER_ID = HttpHeaders.of("X-User-Id");

        private final CollectionService collectionService;
        private final MetricsCollector metrics;
        private final ObjectMapper mapper;
        private final DtoMapper dtoMapper;

        /**
         * Create collection controller.
         *
         * @param collectionService service for collection operations
         * @param metrics           metrics collector for observability
         * @param mapper            JSON object mapper
         * @param dtoMapper         DTO mapper for entity conversion
         */
        public CollectionController(
                        CollectionService collectionService,
                        MetricsCollector metrics,
                        ObjectMapper mapper,
                        DtoMapper dtoMapper) {
                this.collectionService = Objects.requireNonNull(collectionService, "CollectionService cannot be null");
                this.metrics = Objects.requireNonNull(metrics, "MetricsCollector cannot be null");
                this.mapper = Objects.requireNonNull(mapper, "ObjectMapper cannot be null");
                this.dtoMapper = Objects.requireNonNull(dtoMapper, "DtoMapper cannot be null");
                logger.info("CollectionController initialized");
        }

        /**
         * Handle incoming HTTP requests for collection operations.
         *
         * @param request HTTP request
         * @return Promise of HTTP response
         */
        public Promise<HttpResponse> handle(HttpRequest request) {
                String tenantId = extractTenantId(request);
                if (tenantId == null || tenantId.isBlank()) {
                        metrics.incrementCounter("controller.collection.error",
                                        "error_type", "MISSING_TENANT_ID");
                        return Promise.of(ResponseBuilder.badRequest()
                                        .json(Collections.singletonMap("error", "X-Tenant-Id header is required"))
                                        .build());
                }

                String path = request.getPath();
                HttpMethod method = request.getMethod();

                try {
                        if (method == HttpMethod.POST && path.equals("/api/collections")) {
                                return createCollection(request, tenantId);
                        } else if (method == HttpMethod.GET && path.equals("/api/collections")) {
                                return listCollections(request, tenantId);
                        } else if (method == HttpMethod.GET && path.matches("/api/collections/[a-f0-9-]+")) {
                                String collectionId = extractIdFromPath(path);
                                return getCollection(collectionId, tenantId);
                        } else if (method == HttpMethod.PUT && path.matches("/api/collections/[a-f0-9-]+")) {
                                String collectionId = extractIdFromPath(path);
                                return updateCollection(request, collectionId, tenantId);
                        } else if (method == HttpMethod.DELETE && path.matches("/api/collections/[a-f0-9-]+")) {
                                String collectionId = extractIdFromPath(path);
                                return deleteCollection(collectionId, tenantId);
                        } else {
                                metrics.incrementCounter("controller.collection.error",
                                                "error_type", "NOT_FOUND");
                                return Promise.of(ResponseBuilder.notFound()
                                                .json(Collections.singletonMap("error", "Endpoint not found"))
                                                .build());
                        }
                } catch (Exception e) {
                        metrics.incrementCounter("controller.collection.error",
                                        "error_type", e.getClass().getSimpleName());
                        logger.error("Error handling collection request", e);
                        return Promise.of(ResponseBuilder.internalServerError()
                                        .json(Collections.singletonMap("error", "Internal server error"))
                                        .build());
                }
        }

        /**
         * Create a new collection.
         *
         * @param request  HTTP request containing collection data
         * @param tenantId tenant ID from header
         * @return Promise of HTTP response
         */
        private Promise<HttpResponse> createCollection(HttpRequest request, String tenantId) {
                long startTime = System.currentTimeMillis();

                return Promise.of(request.getBody().asString(StandardCharsets.UTF_8))
                                .then(body -> {
                                        try {
                                                CreateCollectionRequest req = mapper.readValue(body,
                                                                CreateCollectionRequest.class);

                                                if (req.name() == null || req.name().isBlank()) {
                                                        metrics.incrementCounter("controller.collection.create.error",
                                                                        "error_type", "VALIDATION_ERROR",
                                                                        "tenant", tenantId);
                                                        return Promise.of(ResponseBuilder.badRequest()
                                                                        .json(Collections.singletonMap("error",
                                                                                        "Collection name is required"))
                                                                        .build());
                                                }

                                                MetaCollection collection = DtoMapper.toDomain(req, tenantId,
                                                                extractUserId(request));

                                                return collectionService
                                                                .createCollection(tenantId, collection,
                                                                                extractUserId(request))
                                                                .map(created -> {
                                                                        long duration = System.currentTimeMillis()
                                                                                        - startTime;
                                                                        metrics.recordTimer(
                                                                                        "controller.collection.duration",
                                                                                        duration,
                                                                                        "operation", "create",
                                                                                        "tenant", tenantId);
                                                                        metrics.incrementCounter(
                                                                                        "controller.collection.create",
                                                                                        "tenant", tenantId);

                                                                        CollectionResponse response = dtoMapper
                                                                                        .toCollectionResponse(created);
                                                                        return ResponseBuilder.created()
                                                                                        .json(response)
                                                                                        .build();
                                                                })
                                                                .whenException(e -> {
                                                                        metrics.incrementCounter(
                                                                                        "controller.collection.create.error",
                                                                                        "error_type",
                                                                                        e.getClass().getSimpleName(),
                                                                                        "tenant", tenantId);
                                                                        logger.error("Failed to create collection", e);
                                                                        logger.error("Failed to create collection", e);
                                                                });
                                        } catch (Exception e) {
                                                metrics.incrementCounter("controller.collection.create.error",
                                                                "error_type", "PARSE_ERROR",
                                                                "tenant", tenantId);
                                                logger.error("Failed to parse create collection request", e);
                                                return Promise.of(ResponseBuilder.badRequest()
                                                                .json(Collections.singletonMap("error",
                                                                                "Invalid request format"))
                                                                .build());
                                        }
                                });
        }

        /**
         * Get collection by ID.
         *
         * @param collectionId collection ID
         * @param tenantId     tenant ID
         * @return Promise of HTTP response
         */
        private Promise<HttpResponse> getCollection(String collectionId, String tenantId) {
                long startTime = System.currentTimeMillis();

                return collectionService.getCollection(tenantId, collectionId)
                                .map(optional -> {
                                        long duration = System.currentTimeMillis() - startTime;
                                        metrics.recordTimer("controller.collection.duration", duration,
                                                        "operation", "get",
                                                        "tenant", tenantId);
                                        metrics.incrementCounter("controller.collection.get",
                                                        "tenant", tenantId);

                                        if (optional.isEmpty()) {
                                                metrics.incrementCounter("controller.collection.error",
                                                                "error_type", "NOT_FOUND",
                                                                "tenant", tenantId);
                                                return ResponseBuilder.notFound()
                                                                .json(Collections.singletonMap("error",
                                                                                "Collection not found"))
                                                                .build();
                                        }

                                        CollectionResponse response = dtoMapper.toCollectionResponse(optional.get());
                                        return ResponseBuilder.ok()
                                                        .json(response)
                                                        .build();
                                })
                                .whenException(e -> {
                                        metrics.incrementCounter("controller.collection.get.error",
                                                        "error_type", e.getClass().getSimpleName(),
                                                        "tenant", tenantId);
                                        logger.error("Failed to get collection", e);
                                        logger.error("Failed to get collection", e);
                                });
        }

        /**
         * Update collection.
         *
         * @param request      HTTP request containing updated collection data
         * @param collectionId collection ID
         * @param tenantId     tenant ID
         * @return Promise of HTTP response
         */
        private Promise<HttpResponse> updateCollection(HttpRequest request, String collectionId, String tenantId) {
                long startTime = System.currentTimeMillis();

                return Promise.of(request.getBody().asString(StandardCharsets.UTF_8))
                                .then(body -> {
                                        try {
                                                UpdateCollectionRequest req = mapper.readValue(body,
                                                                UpdateCollectionRequest.class);

                                                // Load existing collection and apply update
                                                return collectionService.getCollection(tenantId, collectionId)
                                                                .then(optional -> {
                                                                        if (optional.isEmpty()) {
                                                                                metrics.incrementCounter(
                                                                                                "controller.collection.update.error",
                                                                                                "error_type",
                                                                                                "NOT_FOUND",
                                                                                                "tenant", tenantId);
                                                                                HttpResponse notFound = ResponseBuilder
                                                                                                .notFound()
                                                                                                .json(Collections
                                                                                                                .singletonMap(
                                                                                                                                "error",
                                                                                                                                "Collection not found"))
                                                                                                .build();
                                                                                return Promise.of(notFound);
                                                                        }

                                                                        MetaCollection existing = optional.get();
                                                                        DtoMapper.applyUpdate(existing, req,
                                                                                        extractUserId(request));

                                                                        return collectionService.updateCollection(
                                                                                        tenantId, existing,
                                                                                        extractUserId(request))
                                                                                        .map(updated -> {
                                                                                                long duration = System
                                                                                                                .currentTimeMillis()
                                                                                                                - startTime;
                                                                                                metrics.recordTimer(
                                                                                                                "controller.collection.duration",
                                                                                                                duration,
                                                                                                                "operation",
                                                                                                                "update",
                                                                                                                "tenant",
                                                                                                                tenantId);
                                                                                                metrics.incrementCounter(
                                                                                                                "controller.collection.update",
                                                                                                                "tenant",
                                                                                                                tenantId);

                                                                                                CollectionResponse response = dtoMapper
                                                                                                                .toCollectionResponse(
                                                                                                                                updated);
                                                                                                return ResponseBuilder
                                                                                                                .ok()
                                                                                                                .json(response)
                                                                                                                .build();
                                                                                        });
                                                                })
                                                                .then(response -> Promise.of(response),
                                                                                error -> {
                                                                                        metrics.incrementCounter(
                                                                                                        "controller.collection.update.error",
                                                                                                        "error_type",
                                                                                                        error.getClass()
                                                                                                                        .getSimpleName(),
                                                                                                        "tenant",
                                                                                                        tenantId);
                                                                                        logger.error(
                                                                                                        "Failed to update collection",
                                                                                                        error);
                                                                                        return Promise.of(
                                                                                                        ResponseBuilder
                                                                                                                        .internalServerError()
                                                                                                                        .json(Collections
                                                                                                                                        .singletonMap(
                                                                                                                                                        "error",
                                                                                                                                                        "Failed to update collection"))
                                                                                                                        .build());
                                                                                });
                                        } catch (Exception e) {
                                                metrics.incrementCounter("controller.collection.update.error",
                                                                "error_type", "PARSE_ERROR",
                                                                "tenant", tenantId);
                                                logger.error("Failed to parse update collection request", e);
                                                return Promise.of(ResponseBuilder.badRequest()
                                                                .json(Collections.singletonMap("error",
                                                                                "Invalid request format"))
                                                                .build());
                                        }
                                });
        }

        /**
         * Delete collection.
         *
         * @param collectionId collection ID
         * @param tenantId     tenant ID
         * @return Promise of HTTP response
         */
        private Promise<HttpResponse> deleteCollection(String collectionId, String tenantId) {
                long startTime = System.currentTimeMillis();

                return collectionService.deleteCollection(tenantId, UUID.fromString(collectionId), extractUserId(null))
                                .then(result -> {
                                        long duration = System.currentTimeMillis() - startTime;
                                        metrics.recordTimer("controller.collection.duration", duration,
                                                        "operation", "delete",
                                                        "tenant", tenantId);
                                        metrics.incrementCounter("controller.collection.delete",
                                                        "tenant", tenantId);

                                        HttpResponse response = ResponseBuilder.ok()
                                                        .json(Collections.singletonMap("message",
                                                                        "Collection deleted successfully"))
                                                        .build();
                                        return Promise.of(response);
                                }, e -> {
                                        metrics.incrementCounter("controller.collection.delete.error",
                                                        "error_type", e.getClass().getSimpleName(),
                                                        "tenant", tenantId);
                                        logger.error("Failed to delete collection", e);
                                        return Promise.of(ResponseBuilder.internalServerError()
                                                        .json(Collections.singletonMap("error",
                                                                        "Failed to delete collection"))
                                                        .build());
                                });
        }

        /**
         * List collections with pagination.
         *
         * @param request  HTTP request with query parameters
         * @param tenantId tenant ID
         * @return Promise of HTTP response
         */
        private Promise<HttpResponse> listCollections(HttpRequest request, String tenantId) {
                long startTime = System.currentTimeMillis();

                int page = getQueryParam(request, "page", 0);
                int size = getQueryParam(request, "size", 10);

                if (page < 0 || size < 1 || size > 100) {
                        metrics.incrementCounter("controller.collection.list.error",
                                        "error_type", "VALIDATION_ERROR",
                                        "tenant", tenantId);
                        return Promise.of(ResponseBuilder.badRequest()
                                        .json(Collections.singletonMap("error", "Invalid pagination parameters"))
                                        .build());
                }

                return collectionService.listCollections(tenantId)
                                .then(collections -> {
                                        long duration = System.currentTimeMillis() - startTime;
                                        metrics.recordTimer("controller.collection.duration", duration,
                                                        "operation", "list",
                                                        "tenant", tenantId);
                                        metrics.incrementCounter("controller.collection.list",
                                                        "tenant", tenantId,
                                                        "count", String.valueOf(collections.size()));

                                        List<CollectionResponse> responses = collections.stream()
                                                        .map(dtoMapper::toCollectionResponse)
                                                        .collect(Collectors.toList());

                                        PaginationListResponse<CollectionResponse> response = PaginationListResponse
                                                        .<CollectionResponse>builder()
                                                        .items(responses)
                                                        .totalCount(collections.size())
                                                        .hasMore(false)
                                                        .build();

                                        HttpResponse httpResponse = ResponseBuilder.ok()
                                                        .json(response)
                                                        .build();
                                        return Promise.of(httpResponse);
                                }, e -> {
                                        metrics.incrementCounter("controller.collection.list.error",
                                                        "error_type", e.getClass().getSimpleName(),
                                                        "tenant", tenantId);
                                        logger.error("Failed to list collections", e);
                                        return Promise.of(ResponseBuilder.internalServerError()
                                                        .json(Collections.singletonMap("error",
                                                                        "Failed to list collections"))
                                                        .build());
                                });
        }

        /**
         * Extract tenant ID from request header.
         *
         * @param request HTTP request
         * @return tenant ID or null if not present
         */
        private String extractTenantId(HttpRequest request) {
        return TenantExtractor.fromHttp(request).orElse(null);
        }

        /**
         * Extract user ID from request header.
         *
         * @param request HTTP request
         * @return user ID or "system" if not present
         */
        private String extractUserId(HttpRequest request) {
                String userId = request != null ? request.getHeader(HEADER_USER_ID) : null;
                return userId != null ? userId : "system";
        }

        /**
         * Extract ID from URL path.
         *
         * @param path URL path
         * @return extracted ID
         */
        private String extractIdFromPath(String path) {
                String[] parts = path.split("/");
                return parts.length > 0 ? parts[parts.length - 1] : "";
        }

        /**
         * Get integer query parameter from request.
         *
         * @param request      HTTP request
         * @param paramName    parameter name
         * @param defaultValue default value if not present
         * @return parameter value or default
         */
        private int getQueryParam(HttpRequest request, String paramName, int defaultValue) {
                String value = request.getQueryParameter(paramName);
                if (value == null || value.isBlank()) {
                        return defaultValue;
                }
                try {
                        return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                        return defaultValue;
                }
        }
}
