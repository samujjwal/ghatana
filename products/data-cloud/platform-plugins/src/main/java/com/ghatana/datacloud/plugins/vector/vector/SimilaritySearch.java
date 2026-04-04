/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.plugins.vector;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for semantic similarity search operations.
 *
 * <p>Provides vector-based search capabilities including k-nearest neighbors,
 * range queries, and filtered semantic search.
 *
 * <h2>Search Types</h2>
 * <ul>
 *   <li><b>KNN</b>: Find K most similar records</li>
 *   <li><b>Range</b>: Find all records within similarity threshold</li>
 *   <li><b>Filtered</b>: Combine semantic search with metadata filters</li>
 *   <li><b>Hybrid</b>: Combine vector and keyword search</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Semantic similarity search operations
 * @doc.layer plugin
 * @doc.pattern Repository, Query
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface SimilaritySearch {

    /**
     * Finds the K most similar records to a query vector.
     *
     * @param query the query embedding
     * @param k number of results to return
     * @param tenantId the tenant ID
     * @return Promise containing search results
     */
    Promise<SearchResults> searchKnn(float[] query, int k, String tenantId);

    /**
     * Finds the K most similar records with filters.
     *
     * @param request the search request
     * @return Promise containing search results
     */
    Promise<SearchResults> search(SearchRequest request);

    /**
     * Finds records within a similarity threshold.
     *
     * @param query the query embedding
     * @param minSimilarity minimum similarity threshold (0.0 to 1.0)
     * @param maxResults maximum results to return
     * @param tenantId the tenant ID
     * @return Promise containing search results
     */
    Promise<SearchResults> searchByThreshold(
            float[] query,
            float minSimilarity,
            int maxResults,
            String tenantId);

    /**
     * Finds records similar to an existing record.
     *
     * @param recordId the reference record ID
     * @param k number of similar records to find
     * @param excludeSelf whether to exclude the reference record
     * @param tenantId the tenant ID
     * @return Promise containing search results
     */
    Promise<SearchResults> findSimilar(
            String recordId,
            int k,
            boolean excludeSelf,
            String tenantId);

    /**
     * Performs hybrid search combining vector and keyword matching.
     *
     * @param request the hybrid search request
     * @return Promise containing search results
     */
    Promise<SearchResults> hybridSearch(HybridSearchRequest request);

    /**
     * Gets a record by ID if it exists in the vector store.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @return Promise containing the record if found
     */
    Promise<Optional<VectorRecord>> getById(String recordId, String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Request for similarity search.
     */
    @Value
    @Builder
    class SearchRequest {
        /**
         * The query embedding.
         */
        float[] queryVector;

        /**
         * Text to embed (alternative to queryVector).
         */
        String queryText;

        /**
         * Number of results to return.
         */
        @Builder.Default
        int k = 10;

        /**
         * Minimum similarity threshold.
         */
        @Builder.Default
        float minSimilarity = 0.0f;

        /**
         * The tenant ID.
         */
        String tenantId;

        /**
         * Metadata filters to apply.
         */
        @Builder.Default
        Map<String, Object> filters = Map.of();

        /**
         * Tags to filter by (AND logic).
         */
        @Builder.Default
        List<String> requiredTags = List.of();

        /**
         * Tags to filter by (OR logic).
         */
        @Builder.Default
        List<String> anyTags = List.of();

        /**
         * Record types to include.
         */
        @Builder.Default
        List<String> recordTypes = List.of();

        /**
         * Whether to include the vector in results.
         */
        @Builder.Default
        boolean includeVector = false;

        /**
         * Whether to include metadata in results.
         */
        @Builder.Default
        boolean includeMetadata = true;

        /**
         * Distance metric to use.
         */
        @Builder.Default
        DistanceMetric metric = DistanceMetric.COSINE;
    }

    /**
     * Request for hybrid search.
     */
    @Value
    @Builder
    class HybridSearchRequest {
        /**
         * The query embedding.
         */
        float[] queryVector;

        /**
         * The keyword query.
         */
        String keywordQuery;

        /**
         * Number of results to return.
         */
        @Builder.Default
        int k = 10;

        /**
         * Weight for vector similarity (0.0 to 1.0).
         */
        @Builder.Default
        float vectorWeight = 0.7f;

        /**
         * Weight for keyword matching (0.0 to 1.0).
         */
        @Builder.Default
        float keywordWeight = 0.3f;

        /**
         * The tenant ID.
         */
        String tenantId;

        /**
         * Additional filters.
         */
        @Builder.Default
        Map<String, Object> filters = Map.of();
    }

    /**
     * Results from a similarity search.
     */
    @Value
    @Builder
    class SearchResults {
        /**
         * The matching records with scores.
         */
        List<ScoredResult> results;

        /**
         * Total matching records (may be more than returned).
         */
        int totalMatches;

        /**
         * Time taken for the search in milliseconds.
         */
        long searchTimeMs;

        /**
         * Whether results were truncated due to limit.
         */
        boolean truncated;

        /**
         * The query vector used (for debugging).
         */
        float[] queryVector;

        /**
         * Creates empty results.
         *
         * @return empty search results
         */
        public static SearchResults empty() {
            return SearchResults.builder()
                    .results(List.of())
                    .totalMatches(0)
                    .searchTimeMs(0)
                    .truncated(false)
                    .build();
        }

        /**
         * Checks if any results were found.
         *
         * @return true if results exist
         */
        public boolean hasResults() {
            return results != null && !results.isEmpty();
        }

        /**
         * Gets the top result if available.
         *
         * @return the top result, or empty
         */
        public Optional<ScoredResult> topResult() {
            return hasResults() ? Optional.of(results.get(0)) : Optional.empty();
        }
    }

    /**
     * A search result with similarity score.
     */
    @Value
    @Builder
    class ScoredResult {
        /**
         * The matching record.
         */
        VectorRecord record;

        /**
         * The similarity/relevance score.
         */
        float score;

        /**
         * The rank in results (1-based).
         */
        int rank;

        /**
         * Distance from query (metric-dependent).
         */
        float distance;

        /**
         * Explanation of the score (optional).
         */
        String scoreExplanation;
    }

    /**
     * Distance metrics for similarity calculation.
     */
    enum DistanceMetric {
        /**
         * Cosine similarity - measures angle between vectors.
         * Score range: -1.0 to 1.0 (higher = more similar)
         */
        COSINE,

        /**
         * Euclidean distance - straight-line distance.
         * Score range: 0 to infinity (lower = more similar)
         */
        EUCLIDEAN,

        /**
         * Dot product - inner product of vectors.
         * Score range: -infinity to infinity (higher = more similar)
         */
        DOT_PRODUCT,

        /**
         * Manhattan distance - sum of absolute differences.
         * Score range: 0 to infinity (lower = more similar)
         */
        MANHATTAN
    }
}
