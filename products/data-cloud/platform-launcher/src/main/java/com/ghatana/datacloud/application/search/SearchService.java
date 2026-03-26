package com.ghatana.datacloud.application.search;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.search.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Application service for full-text and semantic search operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Orchestrates search queries, facet aggregation, and result ranking with
 * validation, error handling, and observability.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * SearchService service = new SearchService(searchIndex, metricsCollector);
 *
 * // Full-text search
 * SearchQuery query = SearchQuery.builder()
 *         .tenantId("tenant-123")
 *         .queryText("gaming laptop")
 *         .filters(Map.of("category", "electronics"))
 *         .facets(List.of("category", "brand"))
 *         .limit(20)
 *         .build();
 *
 * List<SearchResult> results = service.search(query)
 *         .whenComplete((r, e) -> {
 *             if (e == null) {
 *                 logger.info("Found {} results", r.size());
 *             }
 *         });
 *
 * // Semantic search
 * float[] embedding = embeddingService.encode("gaming laptop");
 * List<SearchResult> semanticResults = service.searchByVector(
 *         "tenant-123", embedding, 10);
 *
 * // Facet aggregation
 * List<SearchFacet> facets = service.getFacets(
 *         "tenant-123", "category");
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Application layer service that: - Validates queries before execution -
 * Orchestrates full-text and vector search - Aggregates facet counts for
 * filtering - Emits metrics for all search operations - Caches frequently
 * accessed facets
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - All fields are private final, operations are Promise-based
 * async.
 *
 * @see SearchIndex
 * @see SearchQuery
 * @see SearchResult
 * @see SearchFacet
 * @doc.type class
 * @doc.purpose Application service for search operations
 * @doc.layer application
 * @doc.pattern Service
 */
public final class SearchService {

    private final SearchIndex searchIndex;
    private final MetricsCollector metrics;

    public SearchService(SearchIndex searchIndex, MetricsCollector metrics) {
        this.searchIndex = Objects.requireNonNull(searchIndex, "searchIndex cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    /**
     * Execute full-text search query.
     *
     * <p>
     * Validates query, executes search, and returns ranked results with facets.
     *
     * GIVEN: A valid SearchQuery with tenantId and query parameters WHEN:
     * search() is called THEN: Search results are returned with relevance
     * scores and highlights
     *
     * @param query search query with filters, facets, and pagination
     * @return Promise with list of SearchResult ordered by relevance
     */
    public Promise<List<SearchResult>> search(SearchQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        String tenantId = query.getTenantId();

        if (!query.hasQueryText()) {
            throw new IllegalArgumentException("query text cannot be empty");
        }

        long startTime = System.currentTimeMillis();

        return searchIndex.search(query)
                .whenComplete((results, e) -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    if (e != null) {
                        metrics.incrementCounter("search.query.error",
                                "tenant", tenantId,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.query.success",
                                "tenant", tenantId,
                                "result_count", String.valueOf(results.size()),
                                "has_query_text", String.valueOf(query.hasQueryText()),
                                "has_filters", String.valueOf(query.hasFilters()));

                        // Track query latency
                        metrics.recordTimer("search.query.duration",
                                durationMs,
                                "tenant", tenantId);
                    }
                });
    }

    /**
     * Execute semantic search using vector embedding.
     *
     * <p>
     * Performs similarity search using vector embeddings, returning results
     * ordered by cosine similarity score.
     *
     * GIVEN: TenantId, vector embedding, and result limit WHEN:
     * searchByVector() is called THEN: Similar documents are returned ordered
     * by similarity score
     *
     * @param tenantId tenant ID
     * @param vector   embedding vector (typically 768 or 1536 dimensions)
     * @param limit    maximum number of results
     * @return Promise with list of SearchResult ordered by similarity
     */
    public Promise<List<SearchResult>> searchByVector(
            String tenantId,
            float[] vector,
            int limit) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(vector, "vector cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }
        if (vector.length == 0) {
            return Promise.ofException(new IllegalArgumentException("vector cannot be empty"));
        }
        if (limit <= 0) {
            return Promise.ofException(new IllegalArgumentException("limit must be positive"));
        }
        if (limit > 100) {
            return Promise.ofException(new IllegalArgumentException("limit cannot exceed 100"));
        }

        long startTime = System.currentTimeMillis();

        return searchIndex.searchByVector(tenantId, vector, Map.of(), limit)
                .whenComplete((results, e) -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    if (e != null) {
                        metrics.incrementCounter("search.vector.error",
                                "tenant", tenantId,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.vector.success",
                                "tenant", tenantId,
                                "result_count", String.valueOf(results.size()),
                                "vector_dimensions", String.valueOf(vector.length));

                        // Track vector search latency
                        metrics.recordTimer("search.vector.duration",
                                durationMs,
                                "tenant", tenantId);
                    }
                });
    }

    /**
     * Get facet aggregations for a field.
     *
     * <p>
     * Returns unique values and document counts for specified field, useful for
     * building filter UI.
     *
     * GIVEN: TenantId and field name WHEN: getFacets() is called THEN: Facet
     * with unique values and counts is returned
     *
     * @param tenantId  tenant ID
     * @param fieldName field to aggregate
     * @return Promise with SearchFacet containing values and counts
     */
    public Promise<SearchFacet> getFacets(String tenantId, String fieldName) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }
        if (fieldName.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("fieldName cannot be blank"));
        }

        SearchQuery query = SearchQuery.builder()
                .tenantId(tenantId)
                .queryText("*")
                .build();

        return searchIndex.getFacets(query, fieldName)
                .map(facetCounts -> {
                    // Convert Map<String, Long> to List<FacetValue>
                    List<SearchFacet.FacetValue> values = facetCounts.entrySet().stream()
                            .map(entry -> SearchFacet.FacetValue.of(entry.getKey(), entry.getValue().intValue()))
                            .toList();
                    return SearchFacet.builder()
                            .fieldName(fieldName)
                            .values(values)
                            .build();
                })
                .whenComplete((facet, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.facets.error",
                                "tenant", tenantId,
                                "field", fieldName,
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.facets.success",
                                "tenant", tenantId,
                                "field", fieldName,
                                "value_count", String.valueOf(facet != null ? facet.getTotalValues() : 0));
                    }
                });
    }

    /**
     * Get multiple facet aggregations in batch.
     *
     * <p>
     * Returns facets for all requested fields, optimized for multiple facets.
     *
     * GIVEN: TenantId and list of field names WHEN: getBatchFacets() is called
     * THEN: List of SearchFacet results returned
     *
     * @param tenantId   tenant ID
     * @param fieldNames fields to aggregate
     * @return Promise with list of SearchFacet
     */
    public Promise<List<SearchFacet>> getBatchFacets(String tenantId, List<String> fieldNames) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(fieldNames, "fieldNames cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }
        if (fieldNames.isEmpty()) {
            return Promise.of(List.of());
        }

        // Execute facet queries in parallel
        List<Promise<SearchFacet>> facetPromises = fieldNames.stream()
                .map(fieldName -> getFacets(tenantId, fieldName))
                .toList();

        return Promises.toList(facetPromises)
                .whenComplete((facets, e) -> {
                    if (e != null) {
                        metrics.incrementCounter("search.batch_facets.error",
                                "tenant", tenantId,
                                "field_count", String.valueOf(fieldNames.size()),
                                "error", e.getClass().getSimpleName());
                    } else {
                        metrics.incrementCounter("search.batch_facets.success",
                                "tenant", tenantId,
                                "field_count", String.valueOf(fieldNames.size()));
                    }
                });
    }

    /**
     * Search with facet filters applied.
     *
     * <p>
     * Convenience method for filtered search with facet results.
     *
     * GIVEN: SearchQuery with filters and facets WHEN: searchWithFacets() is
     * called THEN: SearchResultsWithFacets containing both results and facets
     *
     * @param query search query with filters and facets
     * @return Promise with SearchResultsWithFacets
     */
    public Promise<SearchResultsWithFacets> searchWithFacets(SearchQuery query) {
        Objects.requireNonNull(query, "query cannot be null");

        // Execute search and facet queries in parallel
        Promise<List<SearchResult>> searchPromise = search(query);
        Promise<List<SearchFacet>> facetsPromise = query.hasFacets()
                ? getBatchFacets(query.getTenantId(), query.getFacets())
                : Promise.of(List.of());

        return Promises.toList(List.of(searchPromise, facetsPromise))
                .map(list -> new SearchResultsWithFacets(
                        (List<SearchResult>) list.get(0),
                        (List<SearchFacet>) list.get(1)));
    }

    /**
     * Container for search results with facets.
     *
     * <p>
     * <b>Purpose</b><br>
     * Holds both search results and facet aggregations from a single query.
     */
    public static final class SearchResultsWithFacets {

        private final List<SearchResult> results;
        private final List<SearchFacet> facets;

        public SearchResultsWithFacets(List<SearchResult> results, List<SearchFacet> facets) {
            this.results = Objects.requireNonNull(results, "results cannot be null");
            this.facets = Objects.requireNonNull(facets, "facets cannot be null");
        }

        public List<SearchResult> getResults() {
            return Collections.unmodifiableList(results);
        }

        public List<SearchFacet> getFacets() {
            return Collections.unmodifiableList(facets);
        }

        public int getResultCount() {
            return results.size();
        }

        public int getFacetCount() {
            return facets.size();
        }
    }
}
