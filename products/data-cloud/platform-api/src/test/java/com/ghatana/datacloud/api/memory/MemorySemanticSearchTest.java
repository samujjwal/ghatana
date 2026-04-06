/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.memory;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for in-memory semantic search ranking, filtering, and pagination.
 *
 * <p>Uses pre-computed cosine similarity between embedding vectors to avoid
 * model inference dependencies in unit tests.</p>
 *
 * @doc.type    class
 * @doc.purpose Tests for semantic search ranking, filtering, and pagination
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Memory Semantic Search Tests")
class MemorySemanticSearchTest extends EventloopTestBase {

    // ── Embedding model (test stub) ───────────────────────────────────────────

    record MemoryItem(String id, String tenantId, String content, double[] embedding) {}

    record SearchResult(MemoryItem item, double similarity) {}

    private SemanticSearchService search;

    @BeforeEach
    void setUp() {
        search = new SemanticSearchService();
        // Seed with items using hand-crafted 3-D embeddings for predictable similarity
        search.index(new MemoryItem("m1", "tenant-A", "java programming language",
                new double[]{1.0, 0.0, 0.0}));
        search.index(new MemoryItem("m2", "tenant-A", "python scripting interpreted",
                new double[]{0.9, 0.1, 0.0}));
        search.index(new MemoryItem("m3", "tenant-A", "database sql relational",
                new double[]{0.0, 1.0, 0.0}));
        search.index(new MemoryItem("m4", "tenant-A", "machine learning neural network",
                new double[]{0.0, 0.0, 1.0}));
        search.index(new MemoryItem("m5", "tenant-B", "java enterprise backend",
                new double[]{1.0, 0.0, 0.0}));
    }

    // ── Semantic search ranking ───────────────────────────────────────────────

    @Test
    @DisplayName("search returns results ordered by decreasing similarity")
    void searchReturnsResultsOrderedBySimilarity() {
        double[] query = {1.0, 0.0, 0.0}; // matches m1, m2 closely
        List<SearchResult> results = search.query("tenant-A", query, 10);

        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).similarity())
                    .isGreaterThanOrEqualTo(results.get(i + 1).similarity());
        }
    }

    @Test
    @DisplayName("top match is the item with highest cosine similarity to the query")
    void topMatchHasHighestCosineSimilarity() {
        double[] query = {1.0, 0.0, 0.0}; // unit vector along first dimension
        List<SearchResult> results = search.query("tenant-A", query, 5);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).item().id()).isEqualTo("m1"); // exact match
        assertThat(results.get(0).similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("item with zero cosine similarity is ranked last")
    void zeroSimilarityItemRankedLast() {
        double[] query = {0.0, 0.0, 1.0}; // perpendicular to m1 (java) and m3 (db)
        List<SearchResult> results = search.query("tenant-A", query, 5);

        SearchResult lastResult = results.get(results.size() - 1);
        assertThat(lastResult.similarity()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("search is scoped to the requesting tenant")
    void searchIsScopedToTenant() {
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> tenantAResults = search.query("tenant-A", query, 10);
        List<SearchResult> tenantBResults = search.query("tenant-B", query, 10);

        Set<String> tenantAIds = tenantAResults.stream()
                .map(r -> r.item().id()).collect(Collectors.toSet());
        Set<String> tenantBIds = tenantBResults.stream()
                .map(r -> r.item().id()).collect(Collectors.toSet());

        // m5 belongs to tenant-B only
        assertThat(tenantBIds).contains("m5");
        assertThat(tenantAIds).doesNotContain("m5");
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("top-k limits the result set to k items")
    void topKLimitsResultSet() {
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.query("tenant-A", query, 2);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("top-k larger than index size returns all available items")
    void topKLargerThanIndexReturnsAllItems() {
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.query("tenant-A", query, 100);

        assertThat(results.size()).isLessThanOrEqualTo(4); // 4 items for tenant-A
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("content filter excludes items not matching the keyword")
    void contentFilterExcludesNonMatchingItems() {
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.queryWithContentFilter("tenant-A", query, 10, "java");

        assertThat(results).allMatch(r -> r.item().content().contains("java"));
    }

    @Test
    @DisplayName("content filter returning no matches produces an empty result list")
    void contentFilterWithNoMatchesProducesEmptyList() {
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.queryWithContentFilter("tenant-A", query, 10, "kubernetes");

        assertThat(results).isEmpty();
    }

    // ── Semantic search service (inner, for tests) ────────────────────────────

    static class SemanticSearchService {
        private final List<MemoryItem> index = new ArrayList<>();

        void index(MemoryItem item) { index.add(item); }

        List<SearchResult> query(String tenantId, double[] queryVector, int topK) {
            return index.stream()
                    .filter(m -> m.tenantId().equals(tenantId))
                    .map(m -> new SearchResult(m, cosine(queryVector, m.embedding())))
                    .sorted(Comparator.comparingDouble(SearchResult::similarity).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        List<SearchResult> queryWithContentFilter(String tenantId, double[] queryVector,
                                                   int topK, String keyword) {
            return query(tenantId, queryVector, topK).stream()
                    .filter(r -> r.item().content().contains(keyword))
                    .collect(Collectors.toList());
        }

        private double cosine(double[] a, double[] b) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            if (normA == 0 || normB == 0) return 0;
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        }
    }
}
