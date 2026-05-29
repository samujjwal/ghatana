package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
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
 * HTTP handler for first-class Dataset contract (P3.1).
 *
 * <p>Manages datasets as logical groupings of collections with domain
 * classification, data classification, and stewardship metadata.
 * Datasets are stored as entities in the {@code dc_datasets} collection.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code GET  /api/v1/datasets}              — list datasets for tenant</li>
 *   <li>{@code POST /api/v1/datasets}              — create new dataset</li>
 *   <li>{@code GET  /api/v1/datasets/:id}          — get dataset by ID</li>
 *   <li>{@code PUT  /api/v1/datasets/:id}          — update dataset</li>
 *   <li>{@code DELETE /api/v1/datasets/:id}        — delete dataset</li>
 *   <li>{@code POST /api/v1/datasets/:id/collections} — add collection to dataset</li>
 *   <li>{@code DELETE /api/v1/datasets/:id/collections/:collectionId} — remove collection from dataset</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP surface for first-class Dataset domain contract
 * @doc.layer product
 * @doc.pattern Handler
 */
@RequiresRole("ADMIN")
public final class DatasetHandler {

    private static final Logger log = LoggerFactory.getLogger(DatasetHandler.class);
    private static final String DC_DATASETS = "dc_datasets";
    private static final Set<String> VALID_CLASSIFICATIONS = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final String deploymentProfile;
    private final IdempotencyStore idempotencyStore;

    /**
     * @param client entity store client for persisting dataset metadata
     * @param http shared HTTP helper
     * @param objectMapper JSON object mapper
     * @param auditService optional audit service; when null audit emissions are skipped
     * @param deploymentProfile deployment profile (e.g., "local", "sovereign", "staging", "production")
     * @param idempotencyStore idempotency store for dataset operations
     */
    public DatasetHandler(DataCloudClient client, HttpHandlerSupport http,
                         ObjectMapper objectMapper, AuditService auditService,
                         String deploymentProfile, IdempotencyStore idempotencyStore) {
        this.client = client;
        this.http = http;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.deploymentProfile = deploymentProfile != null ? deploymentProfile : "local";
        this.idempotencyStore = idempotencyStore;
    }

    public Promise<HttpResponse> handleListDatasets(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();

        return client.listEntities(DC_DATASETS, tenantId)
            .map(entities -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("datasets", entities);
                response.put("tenantId", tenantId);
                response.put("count", entities.size());
                return http.jsonResponse(response, requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleGetDataset(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String datasetId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (datasetId.isBlank()) {
            return Promise.of(http.errorResponse(400, "dataset ID is required", requestId));
        }

        return client.getEntity(DC_DATASETS, datasetId, tenantId)
            .map(dataset -> http.jsonResponse(dataset.data(), requestId))
            .whenException(error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleCreateDataset(HttpRequest request) {
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
        Map<String, Object> dataset = body;

        // Validate required fields
        if (!dataset.containsKey("name") || dataset.get("name") == null) {
            return Promise.of(http.errorResponse(400, "Dataset name is required", requestId));
        }
        if (!dataset.containsKey("collections") || !(dataset.get("collections") instanceof List)) {
            return Promise.of(http.errorResponse(400, "Dataset collections array is required", requestId));
        }

        if (dataset.containsKey("classification")) {
            String classification = (String) dataset.get("classification");
            if (!VALID_CLASSIFICATIONS.contains(classification)) {
                return Promise.of(http.errorResponse(400, "Invalid classification: " + classification, requestId));
            }
        }

        dataset.put("tenantId", tenantId);
        dataset.put("createdAt", Instant.now().toString());
        dataset.put("updatedAt", Instant.now().toString());

        String datasetId = (String) dataset.get("id");
        if (datasetId == null || datasetId.isBlank()) {
            datasetId = java.util.UUID.randomUUID().toString();
            dataset.put("id", datasetId);
        }
        final String finalDatasetId = datasetId;

        return client.createEntity(DC_DATASETS, datasetId, dataset, tenantId)
            .map(created -> {
                emitAuditEvent("DATASET_CREATED", finalDatasetId, tenantId, requestId);
                return http.jsonResponse(created.data(), requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleUpdateDataset(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String datasetId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (datasetId.isBlank()) {
            return Promise.of(http.errorResponse(400, "dataset ID is required", requestId));
        }

        Map<String, Object> body;
        try {
            body = http.parseJsonBody(request);
        } catch (Exception e) {
            return Promise.of(http.errorResponse(400, "Invalid JSON: " + e.getMessage(), requestId));
        }
        Map<String, Object> updates = body;
        updates.put("updatedAt", Instant.now().toString());

        return client.updateEntity(DC_DATASETS, datasetId, updates, tenantId)
            .map(updated -> {
                emitAuditEvent("DATASET_UPDATED", datasetId, tenantId, requestId);
                return http.jsonResponse(updated.data(), requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleDeleteDataset(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String datasetId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");

        if (datasetId.isBlank()) {
            return Promise.of(http.errorResponse(400, "dataset ID is required", requestId));
        }

        return client.deleteEntity(DC_DATASETS, datasetId, tenantId)
            .map(deleted -> {
                emitAuditEvent("DATASET_DELETED", datasetId, tenantId, requestId);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", datasetId);
                response.put("deleted", true);
                return http.jsonResponse(response, requestId);
            })
            .then(Promise::of, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleAddCollectionToDataset(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String datasetId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");
        final String collectionId = Optional.ofNullable(request.getPathParameter("collectionId"))
            .map(String::trim)
            .orElse("");

        if (datasetId.isBlank()) {
            return Promise.of(http.errorResponse(400, "dataset ID is required", requestId));
        }
        if (collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection ID is required", requestId));
        }

        return client.getEntity(DC_DATASETS, datasetId, tenantId)
            .map(dataset -> {
                Map<String, Object> data = new java.util.LinkedHashMap<>(dataset.data());
                List<String> collections = (List<String>) data.get("collections");
                if (collections == null) {
                    collections = new java.util.ArrayList<>();
                }
                if (!collections.contains(collectionId)) {
                    collections.add(collectionId);
                    data.put("collections", collections);
                    data.put("updatedAt", Instant.now().toString());
                    return client.updateEntity(DC_DATASETS, datasetId, data, tenantId)
                        .map(updated -> {
                            emitAuditEvent("DATASET_COLLECTION_ADDED", datasetId, tenantId, requestId);
                            return http.jsonResponse(updated.data(), requestId);
                        });
                }
                return Promise.of(http.jsonResponse(dataset.data(), requestId));
            })
            .then(promise -> promise, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    public Promise<HttpResponse> handleRemoveCollectionFromDataset(HttpRequest request) {
        final String requestId = http.resolveCorrelationId(request);
        final HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        final String datasetId = Optional.ofNullable(request.getPathParameter("id"))
            .map(String::trim)
            .orElse("");
        final String collectionId = Optional.ofNullable(request.getPathParameter("collectionId"))
            .map(String::trim)
            .orElse("");

        if (datasetId.isBlank()) {
            return Promise.of(http.errorResponse(400, "dataset ID is required", requestId));
        }
        if (collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collection ID is required", requestId));
        }

        return client.getEntity(DC_DATASETS, datasetId, tenantId)
            .map(dataset -> {
                Map<String, Object> data = new java.util.LinkedHashMap<>(dataset.data());
                List<String> collections = (List<String>) data.get("collections");
                if (collections != null && collections.contains(collectionId)) {
                    collections.remove(collectionId);
                    data.put("collections", collections);
                    data.put("updatedAt", Instant.now().toString());
                    return client.updateEntity(DC_DATASETS, datasetId, data, tenantId)
                        .map(updated -> {
                            emitAuditEvent("DATASET_COLLECTION_REMOVED", datasetId, tenantId, requestId);
                            return http.jsonResponse(updated.data(), requestId);
                        });
                }
                return Promise.of(http.jsonResponse(dataset.data(), requestId));
            })
            .then(promise -> promise, error -> Promise.of(http.errorResponse(500, error.getMessage(), requestId)));
    }

    private void emitAuditEvent(String action, String datasetId, String tenantId, String requestId) {
        if (auditService == null) {
            return;
        }
        try {
            AuditEvent event = AuditEvent.builder()
                .tenantId(tenantId)
                .eventType(action)
                .resourceType("dataset")
                .resourceId(datasetId)
                .details(Map.of("requestId", requestId))
                .build();
            auditService.record(event);
        } catch (Exception e) {
            log.warn("Failed to record audit event for action {}: {}", action, e.getMessage());
        }
    }
}
