package com.ghatana.datacloud.application.search;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.search.*;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application service for search indexing operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates document indexing, batch operations, and index management with
 * validation, error handling, and observability.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * IndexingService service = new IndexingService(searchIndex, metricsCollector);
 *
 * // Index single document
 * SearchDocument doc = SearchDocument.builder()
 *         .tenantId("tenant-123")
 *         .entityId("entity-456")
 *         .content(Map.of("title", "Laptop", "description", "Gaming laptop"))
 *         .build();
 *
 * service.indexDocument(doc)
 *         .whenComplete((v, e) -> {
 *             if (e != null) {
 *                 logger.error("Indexing failed", e);
 *             }
 *         });
 *
 * // Batch indexing
 * List<SearchDocument> documents = List.of(doc1, doc2, doc3);
 * service.indexBatch(documents)
 *         .whenComplete((result, e) -> {
 *             logger.info("Indexed {} of {} documents",
 *                     result.getSuccessful(), result.getTotal());
 *         });
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Application layer service that: - Validates documents before indexing -
 * Orchestrates batch operations with partial failure handling - Emits metrics
 * for all indexing operations - Provides tenant-scoped index management
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - All fields are private final, operations are Promise-based
 * async.
 *
 * @see SearchIndex
 * @see SearchDocument
 * @doc.type class
 * @doc.purpose Application service for search indexing
 * @doc.layer application
 * @doc.pattern Service
 */
public final class IndexingService {

    private final SearchIndex searchIndex;
    private final MetricsCollector metrics;

    public IndexingService(SearchIndex searchIndex, MetricsCollector metrics) {
        this.searchIndex = Objects.requireNonNull(searchIndex, "searchIndex cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    /**
     * Index a single document.
     *
     * <p>
     * Validates document before indexing and emits metrics for success/failure.
     *
     * GIVEN: A valid SearchDocument with tenantId, entityId, and content WHEN:
     * indexDocument() is called THEN: Document is indexed and metrics are
     * emitted
     *
     * @param document document to index
     * @return Promise completing when document is indexed
     */
    public Promise<Void> indexDocument(SearchDocument document) {
        Objects.requireNonNull(document, "document cannot be null");

        String tenantId = document.getTenantId();
        String entityId = document.getEntityId();

        return searchIndex.indexDocument(document)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.index.error",
                                "tenant", tenantId,
                                "entity", entityId,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.index.success",
                                "tenant", tenantId);
                    }
                });
    }

    /**
     * Index multiple documents in batch.
     *
     * <p>
     * Validates all documents, indexes in batch, and returns result with
     * success/failure counts. Partial failures are handled gracefully.
     *
     * GIVEN: A list of valid SearchDocuments WHEN: indexBatch() is called THEN:
     * Documents are indexed with BatchIndexResult tracking successes/failures
     *
     * @param documents documents to index
     * @return Promise with BatchIndexResult containing success/failure counts
     */
    public Promise<SearchIndex.BatchIndexResult> indexBatch(List<SearchDocument> documents) {
        Objects.requireNonNull(documents, "documents cannot be null");

        if (documents.isEmpty()) {
            return Promise.of(new SearchIndex.BatchIndexResult(0, 0, 0, List.of()));
        }

        for (SearchDocument doc : documents) {
            if (doc == null) {
                return Promise.ofException(new IllegalArgumentException("Batch contains null document"));
            }
        }

        // Validate all documents before indexing
        List<String> validationErrors = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            SearchDocument doc = documents.get(i);
            if (doc.getTenantId().isBlank()) {
                validationErrors.add("Document at index " + i + " has blank tenantId");
            } else if (doc.getEntityId().isBlank()) {
                validationErrors.add("Document at index " + i + " has blank entityId");
            } else if (doc.getContent().isEmpty()) {
                validationErrors.add("Document at index " + i + " has empty content");
            }
        }

        if (!validationErrors.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException(
                    "Document validation failed: " + String.join(", ", validationErrors)));
        }

        String tenantId = documents.get(0).getTenantId();

        return searchIndex.indexBatch(documents)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.batch.error",
                                "tenant", tenantId,
                                "size", String.valueOf(documents.size()),
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.bulk_index.success",
                                "tenant", tenantId,
                                "total", String.valueOf(result.getTotal()),
                                "successful", String.valueOf(result.getSuccess()),
                                "failed", String.valueOf(result.getFailures()));
                    }
                });
    }

    /**
     * Remove document from index.
     *
     * GIVEN: A tenantId and entityId WHEN: removeDocument() is called THEN:
     * Document is removed from index
     *
     * @param tenantId tenant ID
     * @param entityId entity ID
     * @return Promise completing when document is removed
     */
    public Promise<Void> removeDocument(String tenantId, String entityId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }
        if (entityId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("entityId cannot be blank"));
        }

        return searchIndex.removeDocument(tenantId, entityId)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.remove.error",
                                "tenant", tenantId,
                                "entity", entityId,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.remove.success",
                                "tenant", tenantId);
                    }
                });
    }

    /**
     * Remove multiple documents in batch.
     *
     * GIVEN: Tenant ID and list of entity IDs WHEN: removeBatch() is called
     * THEN: Documents are removed with BatchIndexResult tracking
     * successes/failures
     *
     * @param tenantId  tenant ID
     * @param entityIds list of entity IDs to remove
     * @return Promise with BatchIndexResult
     */
    public Promise<SearchIndex.BatchIndexResult> removeBatch(String tenantId, List<String> entityIds) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(entityIds, "entityIds cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }

        if (entityIds.isEmpty()) {
            return Promise.of(new SearchIndex.BatchIndexResult(0, 0, 0, List.of()));
        }

        return searchIndex.removeBatch(tenantId, entityIds)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.batch_remove.error",
                                "tenant", tenantId,
                                "size", String.valueOf(entityIds.size()),
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.batch_remove.success",
                                "tenant", tenantId,
                                "total", String.valueOf(result.getTotal()),
                                "successful", String.valueOf(result.getSuccess()),
                                "failed", String.valueOf(result.getFailures()));
                    }
                });
    }

    /**
     * Clear all documents for tenant.
     *
     * <p>
     * <b>WARNING</b>: This operation removes all indexed documents for the
     * tenant. Use with caution.
     *
     * GIVEN: A tenant ID WHEN: clearTenant() is called THEN: All documents for
     * tenant are removed from index
     *
     * @param tenantId tenant ID
     * @return Promise completing when all documents are cleared
     */
    public Promise<Void> clearTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }

        return searchIndex.clearTenant(tenantId)
                .whenComplete((v, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.clear_tenant.error",
                                "tenant", tenantId,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.clear_tenant.success",
                                "tenant", tenantId);
                    }
                });
    }

    /**
     * Get index statistics for tenant.
     *
     * GIVEN: A tenant ID WHEN: getStats() is called THEN: IndexStats with
     * document count, size, and last indexed timestamp are returned
     *
     * @param tenantId tenant ID
     * @return Promise with IndexStats
     */
    public Promise<SearchIndex.IndexStats> getStats(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }

        return searchIndex.getStats(tenantId)
                .whenComplete((stats, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.stats.error",
                                "tenant", tenantId,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.stats.success",
                                "tenant", tenantId,
                                "document_count", String.valueOf(stats.getDocumentCount()));
                    }
                });
    }
}
