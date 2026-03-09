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

package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service Provider Interface for similarity search operations.
 *
 * <p>This capability allows storage plugins to provide semantic/vector search
 * functionality. Records can be stored with embeddings and retrieved using
 * approximate nearest neighbor (ANN) search.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Semantic search using vector embeddings</li>
 *   <li>K-nearest neighbor queries</li>
 *   <li>Configurable distance metrics</li>
 *   <li>Metadata filtering</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for similarity search capability
 * @doc.layer core
 * @doc.pattern Capability SPI
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface SimilaritySearchCapability {

    /**
     * Search for similar records using text query.
     *
     * <p>The text is converted to an embedding using the configured
     * embedding service, then nearest neighbors are found.
     *
     * @param request the search request containing query and parameters
     * @return Promise containing search results
     */
    Promise<SearchResults> search(SearchRequest request);

    /**
     * Find records similar to an existing record.
     *
     * @param recordId the ID of the source record
     * @param k the number of similar records to return
     * @param includeSelf whether to include the source record in results
     * @param tenantId the tenant ID
     * @return Promise containing search results
     */
    Promise<SearchResults> findSimilar(String recordId, int k, boolean includeSelf, String tenantId);

    /**
     * Store a record with its embedding for similarity search.
     *
     * @param record the record to store
     * @param tenantId the tenant ID
     * @return Promise completing when stored
     */
    Promise<Void> store(DataRecord record, String tenantId);

    /**
     * Get a vector record by ID.
     *
     * @param recordId the record ID
     * @param tenantId the tenant ID
     * @return Promise containing the record if found
     */
    Promise<Optional<VectorData>> getById(String recordId, String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Distance metric for similarity calculations.
     */
    enum DistanceMetric {
        COSINE,
        EUCLIDEAN,
        DOT_PRODUCT,
        MANHATTAN
    }

    /**
     * Wrapper for vector data associated with a record.
     */
    @Value
    @Builder
    class VectorData {
        /**
         * The source record.
         */
        DataRecord record;

        /**
         * The embedding vector.
         */
        float[] embedding;

        /**
         * Vector dimension.
         */
        int dimension;

        /**
         * Model used to generate embedding.
         */
        String embeddingModel;

        /**
         * Calculate cosine similarity with another vector.
         *
         * @param other the other embedding
         * @return cosine similarity [-1, 1]
         */
        public float cosineSimilarity(float[] other) {
            if (embedding == null || other == null || embedding.length != other.length) {
                return 0f;
            }

            float dotProduct = 0f;
            float normA = 0f;
            float normB = 0f;

            for (int i = 0; i < embedding.length; i++) {
                dotProduct += embedding[i] * other[i];
                normA += embedding[i] * embedding[i];
                normB += other[i] * other[i];
            }

            if (normA == 0 || normB == 0) {
                return 0f;
            }

            return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
        }
    }

    /**
     * Search request parameters.
     */
    @Value
    @Builder
    class SearchRequest {
        /**
         * Text query to search for (will be embedded).
         */
        String queryText;

        /**
         * Pre-computed query embedding (if available).
         */
        float[] queryEmbedding;

        /**
         * Number of results to return.
         */
        @Builder.Default
        int k = 10;

        /**
         * Minimum similarity threshold.
         */
        @Builder.Default
        float minScore = 0.0f;

        /**
         * Distance metric to use.
         */
        @Builder.Default
        DistanceMetric distanceMetric = DistanceMetric.COSINE;

        /**
         * Metadata filters to apply.
         */
        @Builder.Default
        Map<String, Object> filters = Collections.emptyMap();

        /**
         * Tenant ID for multi-tenancy.
         */
        String tenantId;

        /**
         * Whether to include the embedding in results.
         */
        @Builder.Default
        boolean includeEmbedding = false;
    }

    /**
     * Search results container.
     */
    @Value
    @Builder
    class SearchResults {
        /**
         * The matching results ordered by similarity.
         */
        @Builder.Default
        List<ScoredResult> results = Collections.emptyList();

        /**
         * Total number of matches (may exceed returned results).
         */
        @Builder.Default
        int totalMatches = 0;

        /**
         * Time taken to execute search in milliseconds.
         */
        @Builder.Default
        long searchTimeMs = 0;

        /**
         * Distance metric used for scoring.
         */
        @Builder.Default
        DistanceMetric distanceMetric = DistanceMetric.COSINE;

        /**
         * Check if there are any results.
         *
         * @return true if results are not empty
         */
        public boolean hasResults() {
            return results != null && !results.isEmpty();
        }

        /**
         * Create empty results.
         *
         * @return empty search results
         */
        public static SearchResults empty() {
            return SearchResults.builder().build();
        }
    }

    /**
     * A single search result with similarity score.
     */
    @Value
    @Builder
    class ScoredResult {
        /**
         * The vector data for the matched record.
         */
        VectorData record;

        /**
         * Similarity score (higher is more similar for cosine).
         */
        float score;

        /**
         * The rank in the result set (1-based).
         */
        int rank;

        /**
         * Distance metric used for this score.
         */
        @Builder.Default
        DistanceMetric distanceMetric = DistanceMetric.COSINE;
    }
}
