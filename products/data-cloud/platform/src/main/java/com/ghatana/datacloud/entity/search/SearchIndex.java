package com.ghatana.datacloud.entity.search;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Search index port interface for full-text and semantic search.
 *
 * <p><b>Purpose</b><br>
 * Defines indexing and search operations for entity documents. Supports
 * full-text search, semantic vector search, faceted filtering, and batch
 * operations with tenant isolation.
 *
 * <p><b>Index Structure</b><br>
 * Each document contains:
 * - tenantId: Tenant isolation field
 * - entityId: Unique entity identifier
 * - content: Text fields for full-text search
 * - vector: Embedding vector for semantic search
 * - metadata: Additional fields for filtering/faceting
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SearchIndex index = new ElasticsearchIndex(client, metrics);
 *
 * // Index single document
 * SearchDocument doc = SearchDocument.builder()
 *     .tenantId("tenant-123")
 *     .entityId("entity-456")
 *     .content(Map.of("title", "Product Name", "description", "Details"))
 *     .metadata(Map.of("category", "electronics", "price", 99.99))
 *     .build();
 *
 * index.indexDocument(doc)
 *     .then(v -> System.out.println("Indexed"));
 *
 * // Search with query
 * SearchQuery query = SearchQuery.builder()
 *     .tenantId("tenant-123")
 *     .queryText("product")
 *     .filters(Map.of("category", "electronics"))
 *     .limit(10)
 *     .build();
 *
 * index.search(query)
 *     .then(results -> results.forEach(r -> System.out.println(r.getEntityId())));
 * }</pre>
 *
 * <p><b>Implementation Requirements</b><br>
 * - All operations must be Promise-based async
 * - Tenant isolation enforced on all queries
 * - Batch operations with configurable batch size
 * - Support both full-text and vector search
 * - Metrics tracking for indexing/search operations
 * - Error handling without blocking application
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe and support concurrent indexing/search.
 *
 * @see SearchDocument
 * @see SearchQuery
 * @see SearchResult
 * @doc.type interface
 * @doc.purpose Search indexing and query port interface
 * @doc.layer domain
 * @doc.pattern Port
 */
public interface SearchIndex {

    /**
     * Indexes single document for search.
     *
     * <p>Document is added to search index with tenant isolation. If document
     * with same tenantId + entityId exists, it is replaced.
     *
     * @param document the document to index
     * @return promise that completes when indexed
     * @throws NullPointerException if document is null
     */
    Promise<Void> indexDocument(SearchDocument document);

    /**
     * Indexes multiple documents in batch.
     *
     * <p>Batch indexing is more efficient than individual operations. Documents
     * are processed sequentially with fail-soft semantics.
     *
     * @param documents the documents to index
     * @return promise of batch result with success/failure counts
     * @throws NullPointerException if documents is null
     */
    Promise<BatchIndexResult> indexBatch(List<SearchDocument> documents);

    /**
     * Removes document from search index.
     *
     * @param tenantId the tenant ID
     * @param entityId the entity ID
     * @return promise that completes when removed
     * @throws NullPointerException if any parameter is null
     */
    Promise<Void> removeDocument(String tenantId, String entityId);

    /**
     * Removes all documents for entity IDs in batch.
     *
     * @param tenantId  the tenant ID
     * @param entityIds the entity IDs to remove
     * @return promise of batch result with success/failure counts
     * @throws NullPointerException if any parameter is null
     */
    Promise<BatchIndexResult> removeBatch(String tenantId, List<String> entityIds);

    /**
     * Searches index with full-text query.
     *
     * <p>Performs text search across content fields with optional filters
     * and faceting. Results are ranked by relevance score.
     *
     * @param query the search query parameters
     * @return promise of search results ordered by relevance
     * @throws NullPointerException if query is null
     */
    Promise<List<SearchResult>> search(SearchQuery query);

    /**
     * Searches index with semantic vector query.
     *
     * <p>Performs similarity search using embedding vector. Returns documents
     * with highest cosine similarity to query vector.
     *
     * @param tenantId     the tenant ID
     * @param queryVector  the query embedding vector
     * @param filters      optional metadata filters
     * @param limit        maximum results to return
     * @return promise of search results ordered by similarity
     * @throws NullPointerException if tenantId or queryVector is null
     */
    Promise<List<SearchResult>> searchByVector(
            String tenantId,
            float[] queryVector,
            Map<String, Object> filters,
            int limit);

    /**
     * Gets facet counts for search query.
     *
     * <p>Returns aggregated counts for each facet value matching query.
     * Useful for building filter UI.
     *
     * @param query      the search query
     * @param facetField the field to facet on
     * @return promise of facet counts map (value → count)
     * @throws NullPointerException if any parameter is null
     */
    Promise<Map<String, Long>> getFacets(SearchQuery query, String facetField);

    /**
     * Clears all documents for tenant from index.
     *
     * <p>Use with caution - removes all indexed documents for tenant.
     *
     * @param tenantId the tenant ID
     * @return promise that completes when cleared
     * @throws NullPointerException if tenantId is null
     */
    Promise<Void> clearTenant(String tenantId);

    /**
     * Gets index statistics for tenant.
     *
     * <p>Returns document count, index size, and other metrics.
     *
     * @param tenantId the tenant ID
     * @return promise of index statistics
     * @throws NullPointerException if tenantId is null
     */
    Promise<IndexStats> getStats(String tenantId);

    /**
     * Batch indexing result with success/failure tracking.
     */
    final class BatchIndexResult {
        private final int total;
        private final int success;
        private final int failures;
        private final List<String> errors;

        public BatchIndexResult(int total, int success, int failures, List<String> errors) {
            this.total = total;
            this.success = success;
            this.failures = failures;
            this.errors = errors;
        }

        public int getTotal() {
            return total;
        }

        public int getSuccess() {
            return success;
        }

        public int getFailures() {
            return failures;
        }

        public List<String> getErrors() {
            return errors;
        }

        public double getSuccessRate() {
            return total > 0 ? (double) success / total * 100.0 : 0.0;
        }
    }

    /**
     * Index statistics for a tenant.
     */
    final class IndexStats {
        private final String tenantId;
        private final long documentCount;
        private final long indexSizeBytes;
        private final long lastIndexedTimestamp;

        public IndexStats(
                String tenantId,
                long documentCount,
                long indexSizeBytes,
                long lastIndexedTimestamp) {

            this.tenantId = tenantId;
            this.documentCount = documentCount;
            this.indexSizeBytes = indexSizeBytes;
            this.lastIndexedTimestamp = lastIndexedTimestamp;
        }

        public String getTenantId() {
            return tenantId;
        }

        public long getDocumentCount() {
            return documentCount;
        }

        public long getIndexSizeBytes() {
            return indexSizeBytes;
        }

        public long getLastIndexedTimestamp() {
            return lastIndexedTimestamp;
        }
    }
}
