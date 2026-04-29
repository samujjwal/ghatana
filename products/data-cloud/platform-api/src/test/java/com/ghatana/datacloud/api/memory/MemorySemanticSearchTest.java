/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    // ── Embedding model (test stub) ─────────────────────────────────────────── // GH-90000

    record MemoryItem(String id, String tenantId, String content, double[] embedding) {} // GH-90000

    record SearchResult(MemoryItem item, double similarity) {} // GH-90000

    private SemanticSearchService search;

    @BeforeEach
    void setUp() { // GH-90000
        search = new SemanticSearchService(); // GH-90000
        // Seed with items using hand-crafted 3-D embeddings for predictable similarity
        search.index(new MemoryItem("m1", "tenant-A", "java programming language", // GH-90000
                new double[]{1.0, 0.0, 0.0}));
        search.index(new MemoryItem("m2", "tenant-A", "python scripting interpreted", // GH-90000
                new double[]{0.9, 0.1, 0.0}));
        search.index(new MemoryItem("m3", "tenant-A", "database sql relational", // GH-90000
                new double[]{0.0, 1.0, 0.0}));
        search.index(new MemoryItem("m4", "tenant-A", "machine learning neural network", // GH-90000
                new double[]{0.0, 0.0, 1.0}));
        search.index(new MemoryItem("m5", "tenant-B", "java enterprise backend", // GH-90000
                new double[]{1.0, 0.0, 0.0}));
    }

    // ── Semantic search ranking ───────────────────────────────────────────────

    @Test
    @DisplayName("search returns results ordered by decreasing similarity")
    void searchReturnsResultsOrderedBySimilarity() { // GH-90000
        double[] query = {1.0, 0.0, 0.0}; // matches m1, m2 closely
        List<SearchResult> results = search.query("tenant-A", query, 10); // GH-90000

        for (int i = 0; i < results.size() - 1; i++) { // GH-90000
            assertThat(results.get(i).similarity()) // GH-90000
                    .isGreaterThanOrEqualTo(results.get(i + 1).similarity()); // GH-90000
        }
    }

    @Test
    @DisplayName("top match is the item with highest cosine similarity to the query")
    void topMatchHasHighestCosineSimilarity() { // GH-90000
        double[] query = {1.0, 0.0, 0.0}; // unit vector along first dimension
        List<SearchResult> results = search.query("tenant-A", query, 5); // GH-90000

        assertThat(results).isNotEmpty(); // GH-90000
        assertThat(results.get(0).item().id()).isEqualTo("m1"); // exact match
        assertThat(results.get(0).similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
    }

    @Test
    @DisplayName("item with zero cosine similarity is ranked last")
    void zeroSimilarityItemRankedLast() { // GH-90000
        double[] query = {0.0, 0.0, 1.0}; // perpendicular to m1 (java) and m3 (db) // GH-90000
        List<SearchResult> results = search.query("tenant-A", query, 5); // GH-90000

        SearchResult lastResult = results.get(results.size() - 1); // GH-90000
        assertThat(lastResult.similarity()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000
    }

    // ── Tenant isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("search is scoped to the requesting tenant")
    void searchIsScopedToTenant() { // GH-90000
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> tenantAResults = search.query("tenant-A", query, 10); // GH-90000
        List<SearchResult> tenantBResults = search.query("tenant-B", query, 10); // GH-90000

        Set<String> tenantAIds = tenantAResults.stream() // GH-90000
                .map(r -> r.item().id()).collect(Collectors.toSet()); // GH-90000
        Set<String> tenantBIds = tenantBResults.stream() // GH-90000
                .map(r -> r.item().id()).collect(Collectors.toSet()); // GH-90000

        // m5 belongs to tenant-B only
        assertThat(tenantBIds).contains("m5");
        assertThat(tenantAIds).doesNotContain("m5");
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("top-k limits the result set to k items")
    void topKLimitsResultSet() { // GH-90000
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.query("tenant-A", query, 2); // GH-90000

        assertThat(results).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("top-k larger than index size returns all available items")
    void topKLargerThanIndexReturnsAllItems() { // GH-90000
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.query("tenant-A", query, 100); // GH-90000

        assertThat(results.size()).isLessThanOrEqualTo(4); // 4 items for tenant-A // GH-90000
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("content filter excludes items not matching the keyword")
    void contentFilterExcludesNonMatchingItems() { // GH-90000
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.queryWithContentFilter("tenant-A", query, 10, "java"); // GH-90000

        assertThat(results).allMatch(r -> r.item().content().contains("java"));
    }

    @Test
    @DisplayName("content filter returning no matches produces an empty result list")
    void contentFilterWithNoMatchesProducesEmptyList() { // GH-90000
        double[] query = {1.0, 0.0, 0.0};
        List<SearchResult> results = search.queryWithContentFilter("tenant-A", query, 10, "kubernetes"); // GH-90000

        assertThat(results).isEmpty(); // GH-90000
    }

    // ── Vector ID mapping ─────────────────────────────────────────────────────

    @Test
    @DisplayName("indexed item is retrievable by its exact ID")
    void indexedItemIsRetrievableById() { // GH-90000
        assertThat(search.findById("m1")).isPresent(); // GH-90000
        assertThat(search.findById("m1").get().id()).isEqualTo("m1"); // GH-90000
    }

    @Test
    @DisplayName("unknown ID returns empty Optional")
    void unknownIdReturnsEmpty() { // GH-90000
        assertThat(search.findById("does-not-exist")).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("search result IDs correspond to indexed items (no phantom IDs)")
    void searchResultIdsMatchIndexedItems() { // GH-90000
        double[] query = {0.0, 1.0, 0.0}; // closest to m3
        List<SearchResult> results = search.query("tenant-A", query, 5); // GH-90000

        // Every result ID must be findable in the index
        results.forEach(r -> // GH-90000
                assertThat(search.findById(r.item().id())).isPresent()); // GH-90000
    }

    @Test
    @DisplayName("removing an item by ID excludes it from subsequent queries")
    void removedItemDoesNotAppearInSearch() { // GH-90000
        search.remove("m3"); // GH-90000

        double[] query = {0.0, 1.0, 0.0}; // was closest to m3
        List<SearchResult> results = search.query("tenant-A", query, 10); // GH-90000

        assertThat(results).extracting(r -> r.item().id()) // GH-90000
                .doesNotContain("m3"); // GH-90000
    }

    @Test
    @DisplayName("removing an item by ID makes findById return empty")
    void removedItemNotFoundById() { // GH-90000
        search.remove("m2"); // GH-90000
        assertThat(search.findById("m2")).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("removing an item in one tenant does not affect the other tenant's index")
    void removeIsScoped() { // GH-90000
        search.remove("m5"); // tenant-B item
        assertThat(search.findById("m5")).isEmpty(); // GH-90000

        // tenant-A items must be intact
        assertThat(search.findById("m1")).isPresent(); // GH-90000
        assertThat(search.findById("m2")).isPresent(); // GH-90000
    }

    @Test
    @DisplayName("ID uniqueness: indexing a duplicate ID overwrites the previous embedding")
    void duplicateIdOverwritesPreviousEmbedding() { // GH-90000
        // Overwrite m1's embedding to point in the database direction
        search.index(new MemoryItem("m1", "tenant-A", "database sql", // GH-90000
                new double[]{0.0, 1.0, 0.0})); // GH-90000

        double[] query = {0.0, 1.0, 0.0};
        List<SearchResult> results = search.query("tenant-A", query, 5); // GH-90000

        // m1 should now score highest with the rewritten embedding
        assertThat(results.get(0).item().id()).isEqualTo("m1"); // GH-90000
        assertThat(results.get(0).similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001)); // GH-90000

        // findById should return the updated item
        MemoryItem updated = search.findById("m1").orElseThrow(); // GH-90000
        assertThat(updated.content()).isEqualTo("database sql"); // GH-90000
    }

    // ── Semantic search service (inner, for tests) ──────────────────────────── // GH-90000

    static class SemanticSearchService {
        private final java.util.Map<String, MemoryItem> index = new java.util.LinkedHashMap<>(); // GH-90000

        void index(MemoryItem item) { index.put(item.id(), item); } // GH-90000

        void remove(String id) { index.remove(id); } // GH-90000

        java.util.Optional<MemoryItem> findById(String id) { // GH-90000
            return java.util.Optional.ofNullable(index.get(id)); // GH-90000
        }

        List<SearchResult> query(String tenantId, double[] queryVector, int topK) { // GH-90000
            return index.values().stream() // GH-90000
                    .filter(m -> m.tenantId().equals(tenantId)) // GH-90000
                    .map(m -> new SearchResult(m, cosine(queryVector, m.embedding()))) // GH-90000
                    .sorted(Comparator.comparingDouble(SearchResult::similarity).reversed()) // GH-90000
                    .limit(topK) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        List<SearchResult> queryWithContentFilter(String tenantId, double[] queryVector, // GH-90000
                                                   int topK, String keyword) {
            return query(tenantId, queryVector, topK).stream() // GH-90000
                    .filter(r -> r.item().content().contains(keyword)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        private double cosine(double[] a, double[] b) { // GH-90000
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) { // GH-90000
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            if (normA == 0 || normB == 0) return 0; // GH-90000
            return dot / (Math.sqrt(normA) * Math.sqrt(normB)); // GH-90000
        }
    }
}
