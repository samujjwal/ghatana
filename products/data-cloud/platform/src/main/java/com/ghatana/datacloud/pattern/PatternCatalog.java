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

package com.ghatana.datacloud.pattern;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for pattern storage, retrieval, and search.
 *
 * <p>The PatternCatalog is the authoritative source for all learned patterns.
 * It supports CRUD operations, search, and lifecycle management.
 *
 * <h2>Key Capabilities</h2>
 * <ul>
 *   <li>Pattern CRUD with version control</li>
 *   <li>Search by type, tags, and metadata</li>
 *   <li>Semantic search using embeddings</li>
 *   <li>Multi-tenant isolation</li>
 *   <li>Pattern lifecycle management</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Central pattern registry
 * @doc.layer core
 * @doc.pattern Repository, Registry
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface PatternCatalog {

    // ═══════════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a new pattern.
     *
     * @param pattern the pattern to register
     * @return Promise containing the registered pattern with assigned ID
     */
    Promise<PatternRecord> register(PatternRecord pattern);

    /**
     * Updates an existing pattern, creating a new version.
     *
     * @param pattern the updated pattern
     * @param changelog description of changes
     * @return Promise containing the updated pattern
     */
    Promise<PatternRecord> update(PatternRecord pattern, String changelog);

    /**
     * Gets a pattern by ID.
     *
     * @param patternId the pattern ID
     * @param tenantId the tenant ID
     * @return Promise containing the pattern if found
     */
    Promise<Optional<PatternRecord>> get(String patternId, String tenantId);

    /**
     * Gets a specific version of a pattern.
     *
     * @param patternId the pattern ID
     * @param version the version string
     * @param tenantId the tenant ID
     * @return Promise containing the pattern version if found
     */
    Promise<Optional<PatternRecord>> getVersion(String patternId, String version, String tenantId);

    /**
     * Deletes a pattern (soft delete - archives).
     *
     * @param patternId the pattern ID
     * @param tenantId the tenant ID
     * @return Promise containing success status
     */
    Promise<Boolean> delete(String patternId, String tenantId);

    /**
     * Permanently removes a pattern.
     *
     * @param patternId the pattern ID
     * @param tenantId the tenant ID
     * @return Promise containing success status
     */
    Promise<Boolean> purge(String patternId, String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Search Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Searches patterns with filters.
     *
     * @param query the search query
     * @return Promise containing matching patterns
     */
    Promise<PatternSearchResult> search(PatternQuery query);

    /**
     * Finds patterns by type.
     *
     * @param type the pattern type
     * @param tenantId the tenant ID
     * @return Promise containing matching patterns
     */
    Promise<List<PatternRecord>> findByType(PatternType type, String tenantId);

    /**
     * Finds patterns by tags.
     *
     * @param tags the tags to match (any)
     * @param tenantId the tenant ID
     * @return Promise containing matching patterns
     */
    Promise<List<PatternRecord>> findByTags(List<String> tags, String tenantId);

    /**
     * Finds patterns similar to a query vector.
     *
     * @param embedding the query embedding
     * @param k number of results
     * @param tenantId the tenant ID
     * @return Promise containing similar patterns
     */
    Promise<List<PatternRecord>> findSimilar(float[] embedding, int k, String tenantId);

    /**
     * Lists all active patterns for a tenant.
     *
     * @param tenantId the tenant ID
     * @param limit maximum results
     * @return Promise containing active patterns
     */
    Promise<List<PatternRecord>> listActive(String tenantId, int limit);

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Activates a pattern for production use.
     *
     * @param patternId the pattern ID
     * @param tenantId the tenant ID
     * @return Promise containing the activated pattern
     */
    Promise<PatternRecord> activate(String patternId, String tenantId);

    /**
     * Deprecates a pattern.
     *
     * @param patternId the pattern ID
     * @param reason deprecation reason
     * @param tenantId the tenant ID
     * @return Promise containing the deprecated pattern
     */
    Promise<PatternRecord> deprecate(String patternId, String reason, String tenantId);

    /**
     * Archives a pattern.
     *
     * @param patternId the pattern ID
     * @param tenantId the tenant ID
     * @return Promise containing the archived pattern
     */
    Promise<PatternRecord> archive(String patternId, String tenantId);

    /**
     * Finds patterns that need revalidation.
     *
     * @param tenantId the tenant ID
     * @return Promise containing patterns needing revalidation
     */
    Promise<List<PatternRecord>> findNeedingRevalidation(String tenantId);

    /**
     * Marks patterns as expired based on validity windows.
     *
     * @param tenantId the tenant ID
     * @return Promise containing number of patterns expired
     */
    Promise<Integer> expireStale(String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets catalog statistics.
     *
     * @param tenantId the tenant ID
     * @return Promise containing statistics
     */
    Promise<CatalogStats> getStats(String tenantId);

    /**
     * Gets pattern performance metrics.
     *
     * @param patternId the pattern ID
     * @param tenantId the tenant ID
     * @return Promise containing metrics
     */
    Promise<PatternMetrics> getMetrics(String patternId, String tenantId);

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Query for searching patterns.
     */
    @Value
    @Builder
    class PatternQuery {
        /**
         * Text query for name/description.
         */
        String textQuery;

        /**
         * Pattern types to include.
         */
        @Builder.Default
        List<PatternType> types = List.of();

        /**
         * Tags to match (any).
         */
        @Builder.Default
        List<String> tags = List.of();

        /**
         * Status to filter by.
         */
        @Builder.Default
        List<PatternRecord.PatternStatus> statuses = List.of();

        /**
         * Minimum confidence.
         */
        @Builder.Default
        float minConfidence = 0.0f;

        /**
         * Minimum precision.
         */
        @Builder.Default
        float minPrecision = 0.0f;

        /**
         * The tenant ID.
         */
        String tenantId;

        /**
         * Created after this time.
         */
        Instant createdAfter;

        /**
         * Created before this time.
         */
        Instant createdBefore;

        /**
         * Maximum results.
         */
        @Builder.Default
        int limit = 100;

        /**
         * Offset for pagination.
         */
        @Builder.Default
        int offset = 0;

        /**
         * Sort field.
         */
        @Builder.Default
        String sortBy = "confidence";

        /**
         * Sort direction.
         */
        @Builder.Default
        boolean ascending = false;

        /**
         * Creates a simple query for active patterns.
         *
         * @param tenantId the tenant ID
         * @return active patterns query
         */
        public static PatternQuery active(String tenantId) {
            return PatternQuery.builder()
                    .tenantId(tenantId)
                    .statuses(List.of(PatternRecord.PatternStatus.ACTIVE))
                    .build();
        }
    }

    /**
     * Result of pattern search.
     */
    @Value
    @Builder
    class PatternSearchResult {
        /**
         * Matching patterns.
         */
        List<PatternRecord> patterns;

        /**
         * Total matches (before pagination).
         */
        int totalCount;

        /**
         * Search time in milliseconds.
         */
        long searchTimeMs;

        /**
         * Whether more results are available.
         */
        boolean hasMore;
    }

    /**
     * Catalog statistics.
     */
    @Value
    @Builder
    class CatalogStats {
        /**
         * Total patterns.
         */
        int totalPatterns;

        /**
         * Active patterns.
         */
        int activePatterns;

        /**
         * Deprecated patterns.
         */
        int deprecatedPatterns;

        /**
         * Patterns by type.
         */
        Map<PatternType, Integer> byType;

        /**
         * Average confidence.
         */
        float avgConfidence;

        /**
         * Average precision.
         */
        float avgPrecision;

        /**
         * Patterns needing revalidation.
         */
        int needingRevalidation;
    }

    /**
     * Pattern performance metrics.
     */
    @Value
    @Builder
    class PatternMetrics {
        /**
         * Pattern ID.
         */
        String patternId;

        /**
         * Total matches.
         */
        long totalMatches;

        /**
         * True positives.
         */
        long truePositives;

        /**
         * False positives.
         */
        long falsePositives;

        /**
         * False negatives.
         */
        long falseNegatives;

        /**
         * Precision.
         */
        float precision;

        /**
         * Recall.
         */
        float recall;

        /**
         * F1 score.
         */
        float f1Score;

        /**
         * Average match score.
         */
        float avgMatchScore;

        /**
         * Last matched at.
         */
        Instant lastMatchedAt;

        /**
         * Matches per hour (rolling).
         */
        float matchesPerHour;
    }
}
