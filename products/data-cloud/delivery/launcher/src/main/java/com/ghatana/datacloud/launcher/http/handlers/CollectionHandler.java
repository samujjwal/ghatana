package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.observability.idempotency.IdempotencyHelper;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import com.ghatana.platform.security.annotation.RequiresRole;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HTTP handler for first-class Collection contract (P3.1).
 *
 * <p>Manages collections as first-class domain objects with lifecycle, quality,
 * retention, lineage, and permissions. Collections are stored as entities in
 * the {@code dc_collections} collection but with typed schema validation.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code GET  /api/v1/collections}              — list collections for tenant</li>
 *   <li>{@code POST /api/v1/collections}              — create new collection</li>
 *   <li>{@code GET  /api/v1/collections/:id}          — get collection by ID</li>
 *   <li>{@code PUT  /api/v1/collections/:id}          — update collection</li>
 *   <li>{@code DELETE /api/v1/collections/:id}        — delete/archive collection</li>
 *   <li>{@code POST /api/v1/collections/:id/publish}  — publish collection (DRAFT → PUBLISHED)</li>
 *   <li>{@code POST /api/v1/collections/:id/deprecate} — deprecate collection (PUBLISHED → DEPRECATED)</li>
 *   <li>{@code POST /api/v1/collections/:id/archive}  — archive collection (DEPRECATED → ARCHIVED)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP surface for first-class Collection domain contract
 * @doc.layer product
 * @doc.pattern Handler
 */
@RequiresRole("ADMIN")
public final class CollectionHandler {

    private static final Logger log = LoggerFactory.getLogger(CollectionHandler.class);
    private static final String DC_COLLECTIONS = "dc_collections";
    private static final Set<String> VALID_LIFECYCLE_STATUSES = Set.of("DRAFT", "PUBLISHED", "DEPRECATED", "ARCHIVED");
    private static final Set<String> VALID_OPERATIONAL_STATUSES = Set.of("healthy", "degraded", "unavailable", "maintenance");

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final String deploymentProfile;
    private final IdempotencyStore idempotencyStore;

    /**
     * @param client entity store client for persisting collection metadata
     * @param http shared HTTP helper
     * @param objectMapper JSON object mapper
     * @param auditService optional audit service; when null audit emissions are skipped
     * @param deploymentProfile deployment profile (e.g., "local", "sovereign", "staging", "production")
     * @param idempotencyStore idempotency store for collection operations
     */
    public CollectionHandler(DataCloudClient client, HttpHandlerSupport http,
                            ObjectMapper objectMapper, AuditService auditService,
                            String deploymentProfile, IdempotencyStore idempotencyStore) {
        this.client = client;
        this.http = http;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.deploymentProfile = deploymentProfile != null ? deploymentProfile : "local";
        this.idempotencyStore = idempotencyStore;
    }

    public Promise<HttpResponse> handleListCollections(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        return client.listEntities(DC_COLLECTIONS, tenantId)
            .map(entities -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("collections", entities);
                response.put("tenantId", tenantId);
                response.put("count", entities.size());
                return http.jsonResponse(response, requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleGetCollection(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String collectionId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection ID is required", requestId));
        }

        return client.getEntity(DC_COLLECTIONS, collectionId, tenantId)
            .map(collection -> http.jsonResponse(collection.data(), requestId))
            .whenException(error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleCreateCollection(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        Map<String, Object> body;
        try {
            body = http.parseJsonBody(request);
        } catch (Exception e) {
            return Promise.of(http.errorResponse(400, "Invalid JSON: " + e.getMessage(), requestId));
        }
        Map<String, Object> collection = body;

        // Validate required fields
        if (!collection.containsKey("name") || collection.get("name") == null) {
            return Promise.of(http.errorResponse(400, "Collection name is required", requestId));
        }
        if (!collection.containsKey("lifecycleStatus")) {
            collection.put("lifecycleStatus", "DRAFT");
        }
        if (!collection.containsKey("operationalStatus")) {
            collection.put("operationalStatus", "healthy");
        }

        String lifecycleStatus = (String) collection.get("lifecycleStatus");
        if (!VALID_LIFECYCLE_STATUSES.contains(lifecycleStatus)) {
            return Promise.of(http.errorResponse(400, "Invalid lifecycleStatus: " + lifecycleStatus, requestId));
        }

        String operationalStatus = (String) collection.get("operationalStatus");
        if (!VALID_OPERATIONAL_STATUSES.contains(operationalStatus)) {
            return Promise.of(http.errorResponse(400, "Invalid operationalStatus: " + operationalStatus, requestId));
        }

        collection.put("tenantId", tenantId);
        collection.put("createdAt", Instant.now().toString());
        collection.put("updatedAt", Instant.now().toString());

        String collectionId = (String) collection.get("id");
        if (collectionId == null || collectionId.isBlank()) {
            collectionId = java.util.UUID.randomUUID().toString();
            collection.put("id", collectionId);
        }
        final String finalCollectionId = collectionId;

        return client.createEntity(DC_COLLECTIONS, collectionId, collection, tenantId)
            .map(created -> {
                emitAuditEvent("COLLECTION_CREATED", finalCollectionId, tenantId, requestId);
                return http.jsonResponse(created.data(), requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleUpdateCollection(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String collectionId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection ID is required", requestId));
        }

        Map<String, Object> body;
        try {
            body = http.parseJsonBody(request);
        } catch (Exception e) {
            return Promise.of(http.errorResponse(400, "Invalid JSON: " + e.getMessage(), requestId));
        }
        Map<String, Object> updates = body;
        updates.put("updatedAt", Instant.now().toString());

        return client.updateEntity(DC_COLLECTIONS, collectionId, updates, tenantId)
            .map(updated -> {
                emitAuditEvent("COLLECTION_UPDATED", collectionId, tenantId, requestId);
                return http.jsonResponse(updated.data(), requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleDeleteCollection(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String collectionId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection ID is required", requestId));
        }

        return client.deleteEntity(DC_COLLECTIONS, collectionId, tenantId)
            .map(deleted -> {
                emitAuditEvent("COLLECTION_DELETED", collectionId, tenantId, requestId);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", collectionId);
                response.put("deleted", true);
                return http.jsonResponse(response, requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handlePublishCollection(HttpRequest request) {
        return updateLifecycleStatus(request, "PUBLISHED", "COLLECTION_PUBLISHED");
    }

    public Promise<HttpResponse> handleDeprecateCollection(HttpRequest request) {
        return updateLifecycleStatus(request, "DEPRECATED", "COLLECTION_DEPRECATED");
    }

    public Promise<HttpResponse> handleArchiveCollection(HttpRequest request) {
        return updateLifecycleStatus(request, "ARCHIVED", "COLLECTION_ARCHIVED");
    }

    private Promise<HttpResponse> updateLifecycleStatus(HttpRequest request, String newStatus, String auditAction) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String collectionId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection ID is required", requestId));
        }

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("lifecycleStatus", newStatus);
        updates.put("updatedAt", Instant.now().toString());

        return client.updateEntity(DC_COLLECTIONS, collectionId, updates, tenantId)
            .map(updated -> {
                emitAuditEvent(auditAction, collectionId, tenantId, requestId);
                return http.jsonResponse(updated.data(), requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    private void emitAuditEvent(String action, String collectionId, String tenantId, String requestId) {
        if (auditService == null) {
            return;
        }
        try {
            AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .eventType(action)
                .resourceType("collection")
                .resourceId(collectionId)
                .details(Map.of("requestId", requestId))
                .build();
            auditService.record(event);
        } catch (Exception e) {
            log.warn("Failed to record audit event for action {}: {}", action, e.getMessage());
        }
    }
}
