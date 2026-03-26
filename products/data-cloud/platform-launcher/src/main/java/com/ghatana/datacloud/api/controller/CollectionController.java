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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * <li><b>POST /api/v1/collections:</b> Create collection
 * <li><b>GET /api/v1/collections/{collectionId}:</b> Get collection by ID
 * <li><b>PUT /api/v1/collections/{collectionId}:</b> Update collection
 * <li><b>DELETE /api/v1/collections/{collectionId}:</b> Delete collection
 * <li><b>GET /api/v1/collections:</b> List collections with pagination
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
@Tag(name = "Collections", description = "Collection CRUD and listing endpoints")
public class CollectionController {

        private static final Logger logger = LoggerFactory.getLogger(CollectionController.class);

        private static final HttpHeader HEADER_TENANT_ID = HttpHeaders.of("X-Tenant-ID");
        private static final HttpHeader HEADER_USER_ID = HttpHeaders.of("X-User-ID");

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
         * <p><b>Routing</b><br>
         * Dispatches requests to appropriate handler based on HTTP method and path:
         * <ul>
         *   <li>POST /api/v1/collections → {@link #createCollection(HttpRequest, String)}
         *   <li>GET /api/v1/collections → {@link #listCollections(HttpRequest, String)}
         *   <li>GET /api/v1/collections/{collectionId} → {@link #getCollection(String, String)}
         *   <li>PUT /api/v1/collections/{collectionId} → {@link #updateCollection(HttpRequest, String, String)}
         *   <li>DELETE /api/v1/collections/{collectionId} → {@link #deleteCollection(String, String)}
         * </ul>
         *
         * <p><b>Authentication & Authorization</b><br>
         * Validates X-Tenant-Id header for multi-tenant isolation. All operations enforce tenant boundaries.
         *
         * <p><b>Metrics</b><br>
         * Records metrics for request volume, latency, and errors across all operations.
         *
         * <p><b>Error Handling</b><br>
         * Returns appropriate HTTP status codes:
         * <ul>
         *   <li>201 Created: Successful creation
         *   <li>200 OK: Successful GET/PUT
         *   <li>204 No Content: Successful DELETE
         *   <li>400 Bad Request: Invalid request format
         *   <li>401 Unauthorized: Missing tenant context
         *   <li>404 Not Found: Resource not found
         *   <li>500 Internal Server Error: Service failure
         * </ul>
         *
         * @param request HTTP request containing method, path, headers, and body
         * @return Promise of HTTP response with appropriate status and payload
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
                        if (method == HttpMethod.POST && path.equals("/api/v1/collections")) {
                                return createCollection(request, tenantId);
                        } else if (method == HttpMethod.GET && path.equals("/api/v1/collections")) {
                                return listCollections(request, tenantId);
                        } else if (method == HttpMethod.GET && path.matches("/api/v1/collections/[a-f0-9-]+")) {
                                String collectionId = extractIdFromPath(path);
                                return getCollection(collectionId, tenantId);
                        } else if (method == HttpMethod.PUT && path.matches("/api/v1/collections/[a-f0-9-]+")) {
                                String collectionId = extractIdFromPath(path);
                                return updateCollection(request, collectionId, tenantId);
                        } else if (method == HttpMethod.DELETE && path.matches("/api/v1/collections/[a-f0-9-]+")) {
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
         * <p><b>HTTP Method & Path</b><br>
         * POST /api/v1/collections
         *
         * <p><b>Required Headers</b><br>
         * <ul>
         *   <li>X-Tenant-ID: Tenant identifier for multi-tenant isolation
         *   <li>Content-Type: application/json
         * </ul>
         *
         * <p><b>Request Body (JSON)</b><br>
         * <pre>{@code
         * {
         *   "name": "required string - unique within tenant",
         *   "displayName": "optional human-readable name",
         *   "description": "optional description",
         *   "schema": {
         *     "fields": [
         *       {
         *         "name": "field name",
         *         "type": "STRING|NUMBER|BOOLEAN|DATE|TIMESTAMP|JSON",
         *         "required": true/false,
         *         "indexed": true/false
         *       }
         *     ]
         *   },
         *   "storageProfile": {
         *     "tier": "HOT|WARM|COLD",
         *     "ttlDays": 365
         *   }
         * }
         * }</pre>
         *
         * <p><b>Response (201 Created)</b><br>
         * Returns created collection with ID and timestamps:
         * <pre>{@code
         * {
         *   "id": "uuid",
         *   "tenantId": "string",
         *   "name": "string",
         *   "createdAt": "ISO-8601 timestamp",
         *   "recordCount": 0
         * }
         * }</pre>
         *
         * <p><b>Error Responses</b><br>
         * <ul>
         *   <li>400: Invalid request - missing name or invalid schema
         *   <li>401: Missing X-Tenant-ID header
         *   <li>403: User lacks CREATE_COLLECTION permission
         *   <li>409: Collection name already exists in tenant
         *   <li>500: Database or service error
         * </ul>
         *
         * <p><b>Metrics Collected</b><br>
         * <ul>
         *   <li>controller.collection.create (counter)
         *   <li>controller.collection.duration (timer)
         *   <li>controller.collection.create.error (counter on failure)
         * </ul>
         *
         * @param request  HTTP request containing JSON body with collection definition
         * @param tenantId tenant ID from X-Tenant-ID header
         * @return Promise<HttpResponse>: 201 on success, error responses for failures
         */
        @Operation(summary = "Create collection", description = "Creates a new collection within the caller tenant.")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Collection created"),
                        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
                        @ApiResponse(responseCode = "403", description = "Caller lacks permission"),
                        @ApiResponse(responseCode = "409", description = "Collection already exists")
        })
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
         * <p><b>HTTP Method & Path</b><br>
         * GET /api/v1/collections/{collectionId}
         *
         * <p><b>Required Headers</b><br>
         * X-Tenant-ID: Tenant identifier for multi-tenant isolation
         *
         * <p><b>Path Parameters</b><br>
         * collectionId: UUID of collection to retrieve
         *
         * <p><b>Response (200 OK)</b><br>
         * Full collection details including schema and metadata.
         *
         * <p><b>Error Responses</b><br>
         * <ul>
         *   <li>401: Missing X-Tenant-ID header
         *   <li>403: Collection belongs to different tenant
         *   <li>404: Collection not found
         *   <li>500: Database error
         * </ul>
         *
         * @param collectionId collection UUID from URL path
         * @param tenantId     tenant ID from X-Tenant-ID header
         * @return Promise<HttpResponse>: 200 with collection data, or error response
         */
        @Operation(summary = "Get collection", description = "Returns a single collection by identifier within the caller tenant.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Collection returned"),
                        @ApiResponse(responseCode = "404", description = "Collection not found")
        })
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
        @Operation(summary = "Update collection", description = "Updates mutable collection metadata for the caller tenant.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Collection updated"),
                        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
                        @ApiResponse(responseCode = "404", description = "Collection not found")
        })
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
        @Operation(summary = "Delete collection", description = "Deletes a collection within the caller tenant.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Collection deleted"),
                        @ApiResponse(responseCode = "404", description = "Collection not found")
        })
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
        @Operation(summary = "List collections", description = "Lists collections visible to the caller tenant with pagination parameters.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Collections returned"),
                        @ApiResponse(responseCode = "400", description = "Invalid pagination parameters")
        })
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
