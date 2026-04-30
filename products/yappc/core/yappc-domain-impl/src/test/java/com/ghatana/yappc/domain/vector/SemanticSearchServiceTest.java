package com.ghatana.yappc.domain.vector;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SemanticSearchService.
 *
 * @doc.type test
 * @doc.purpose Test semantic search operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SemanticSearchService Tests")
class SemanticSearchServiceTest extends EventloopTestBase {

    private VectorStore vectorStore;
    private EmbeddingService embeddingService;
    private SemanticSearchService service;

    @BeforeEach
    void setUp() { // GH-90000
        vectorStore = mock(VectorStore.class); // GH-90000
        embeddingService = mock(EmbeddingService.class); // GH-90000
        service = new SemanticSearchService(vectorStore, embeddingService); // GH-90000
    }

    @Nested
    @DisplayName("Semantic Search")
    class SemanticSearchTests {

        @Test
        @DisplayName("should search with embedding generation")
        void shouldSearchWithEmbeddingGeneration() { // GH-90000
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding); // GH-90000
            when(embeddingService.createEmbedding(anyString())) // GH-90000
                    .thenReturn(Promise.of(embeddingResult)); // GH-90000

            VectorSearchResult searchResult = new VectorSearchResult( // GH-90000
                    "doc-1", "Document content", embedding, 0.95, 0, Map.of("source", "test.md") // GH-90000
            );
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(List.of(searchResult))); // GH-90000

            SemanticSearchService.SemanticSearchRequest request =
                    new SemanticSearchService.SemanticSearchRequest( // GH-90000
                            "test query", 10, 0.7, null, null
                    );

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise( // GH-90000
                    () -> service.search(request) // GH-90000
            );

            // THEN
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.hits()).hasSize(1); // GH-90000
            assertThat(result.hits().get(0).id()).isEqualTo("doc-1");
            assertThat(result.hits().get(0).score()).isEqualTo(0.95); // GH-90000
            verify(embeddingService).createEmbedding("test query");
        }

        @Test
        @DisplayName("should return empty results on search failure")
        void shouldReturnEmptyOnFailure() { // GH-90000
            // GIVEN
            when(embeddingService.createEmbedding(anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Embedding error")));

            SemanticSearchService.SemanticSearchRequest request =
                    SemanticSearchService.SemanticSearchRequest.of("failing query");

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise( // GH-90000
                    () -> service.search(request) // GH-90000
            );

            // THEN
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.error()).isNotNull(); // GH-90000
            assertThat(result.hits()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Hybrid Search")
    class HybridSearchTests {

        @Test
        @DisplayName("should combine semantic and keyword search")
        void shouldCombineKeywordAndSemanticSearch() { // GH-90000
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding); // GH-90000
            when(embeddingService.createEmbedding(anyString())) // GH-90000
                    .thenReturn(Promise.of(embeddingResult)); // GH-90000

            VectorSearchResult semanticResult = new VectorSearchResult( // GH-90000
                    "doc-1", "Semantic match content with keyword", embedding, 0.8, 0, Map.of() // GH-90000
            );
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap())) // GH-90000
                    .thenReturn(Promise.of(List.of(semanticResult))); // GH-90000

            SemanticSearchService.HybridSearchRequest request =
                    new SemanticSearchService.HybridSearchRequest( // GH-90000
                            "test query", 10, 0.7, null,
                            List.of("keyword"), 0.5, null
                    );

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise( // GH-90000
                    () -> service.hybridSearch(request) // GH-90000
            );

            // THEN
            assertThat(result.hits()).isNotEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Indexing Operations")
    class IndexingTests {

        @Test
        @DisplayName("should index document with embeddings")
        void shouldIndexDocumentWithEmbeddings() { // GH-90000
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding); // GH-90000
            when(embeddingService.createEmbedding(anyString())) // GH-90000
                    .thenReturn(Promise.of(embeddingResult)); // GH-90000
            when(vectorStore.store(anyString(), anyString(), any(float[].class), anyMap())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            SemanticSearchService.IndexRequest request =
                    new SemanticSearchService.IndexRequest( // GH-90000
                            "doc-1", "Test document content", Map.of("source", "test.md"), null // GH-90000
                    );

            // WHEN
            SemanticSearchService.IndexResult result = runPromise( // GH-90000
                    () -> service.index(request) // GH-90000
            );

            // THEN
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.id()).isEqualTo("doc-1");
            assertThat(result.vectorDimension()).isEqualTo(3); // GH-90000
            verify(embeddingService).createEmbedding("Test document content");
            verify(vectorStore).store(eq("doc-1"), eq("Test document content"), any(float[].class), anyMap());
        }

        @Test
        @DisplayName("should batch index multiple documents")
        void shouldBatchIndexMultipleDocuments() { // GH-90000
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding); // GH-90000
            when(embeddingService.createEmbeddings(anyList())) // GH-90000
                    .thenReturn(Promise.of(List.of(embeddingResult, embeddingResult, embeddingResult))); // GH-90000
            when(vectorStore.store(anyString(), anyString(), any(float[].class), anyMap())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            List<SemanticSearchService.IndexRequest> requests = List.of( // GH-90000
                    new SemanticSearchService.IndexRequest("doc-1", "Content 1", Map.of(), null), // GH-90000
                    new SemanticSearchService.IndexRequest("doc-2", "Content 2", Map.of(), null), // GH-90000
                    new SemanticSearchService.IndexRequest("doc-3", "Content 3", Map.of(), null) // GH-90000
            );

            // WHEN
            List<SemanticSearchService.IndexResult> results = runPromise( // GH-90000
                    () -> service.batchIndex(requests) // GH-90000
            );

            // THEN
            assertThat(results).hasSize(3); // GH-90000
            assertThat(results).allMatch(SemanticSearchService.IndexResult::success); // GH-90000
        }

        @Test
        @DisplayName("should delete document from index")
        void shouldDeleteDocumentFromIndex() { // GH-90000
            // GIVEN
            when(vectorStore.delete(anyString())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            // WHEN
            boolean result = runPromise( // GH-90000
                    () -> service.delete("doc-1", null)
            );

            // THEN
            assertThat(result).isTrue(); // GH-90000
            verify(vectorStore).delete("doc-1");
        }
    }

    @Nested
    @DisplayName("Find Similar")
    class FindSimilarTests {

        @Test
        @DisplayName("should find similar documents excluding self")
        void shouldFindSimilarDocuments() { // GH-90000
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            List<VectorSearchResult> similarDocs = List.of( // GH-90000
                    new VectorSearchResult("similar-1", "Similar content", embedding, 0.9, 0, Map.of()), // GH-90000
                    new VectorSearchResult("ref-doc", "Reference content", embedding, 1.0, 0, Map.of()) // GH-90000
            );
            when(vectorStore.searchById(anyString(), anyInt(), anyDouble())) // GH-90000
                    .thenReturn(Promise.of(similarDocs)); // GH-90000

            // WHEN
            List<SemanticSearchService.SearchHit> result = runPromise( // GH-90000
                    () -> service.findSimilar("ref-doc", 10, 0.7, null) // GH-90000
            );

            // THEN
            assertThat(result).hasSize(1); // Excludes the reference document // GH-90000
            assertThat(result.get(0).id()).isEqualTo("similar-1");
        }
    }

    // ==================== F-Y019 / C-Y8 — Tenant isolation ====================

    @Nested
    @DisplayName("Tenant Isolation (F-Y019 / C-Y8)")
    class TenantIsolationTests {

        @Test
        @DisplayName("search() injects tenantId into VectorStore filter map")
        void searchInjectsTenantIdIntoFilters() {
            // GIVEN
            float[] vec = {0.1f, 0.2f, 0.3f};
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(EmbeddingResult.of(vec)));
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap()))
                    .thenReturn(Promise.of(List.of()));

            SemanticSearchService.SemanticSearchRequest request =
                    new SemanticSearchService.SemanticSearchRequest(
                            "test query", 5, 0.7, null, "tenant-abc");

            // WHEN
            runPromise(() -> service.search(request));

            // THEN — VectorStore must have received tenantId in the filter map
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> filterCaptor =
                    (ArgumentCaptor<Map<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
            verify(vectorStore).search(any(float[].class), anyInt(), anyDouble(), filterCaptor.capture());
            assertThat(filterCaptor.getValue()).containsEntry("tenantId", "tenant-abc");
        }

        @Test
        @DisplayName("search() with extra filters merges tenantId alongside caller filters")
        void searchMergesTenantIdWithCallerFilters() {
            // GIVEN
            float[] vec = {0.1f};
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(EmbeddingResult.of(vec)));
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap()))
                    .thenReturn(Promise.of(List.of()));

            SemanticSearchService.SemanticSearchRequest request =
                    new SemanticSearchService.SemanticSearchRequest(
                            "q", 5, 0.7, Map.of("source", "wiki"), "tenant-xyz");

            // WHEN
            runPromise(() -> service.search(request));

            // THEN — both caller filter and tenantId filter must be present
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> captor =
                    (ArgumentCaptor<Map<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
            verify(vectorStore).search(any(float[].class), anyInt(), anyDouble(), captor.capture());
            assertThat(captor.getValue())
                    .containsEntry("tenantId", "tenant-xyz")
                    .containsEntry("source", "wiki");
        }

        @Test
        @DisplayName("hybridSearch() injects tenantId into VectorStore filter map")
        void hybridSearchInjectsTenantIdIntoFilters() {
            // GIVEN
            float[] vec = {0.1f};
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(EmbeddingResult.of(vec)));
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap()))
                    .thenReturn(Promise.of(List.of()));

            SemanticSearchService.HybridSearchRequest request =
                    new SemanticSearchService.HybridSearchRequest(
                            "hybrid query", 5, 0.7, null, List.of("keyword"), 0.5, "tenant-hybrid");

            // WHEN
            runPromise(() -> service.hybridSearch(request));

            // THEN
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> captor =
                    (ArgumentCaptor<Map<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
            verify(vectorStore).search(any(float[].class), anyInt(), anyDouble(), captor.capture());
            assertThat(captor.getValue()).containsEntry("tenantId", "tenant-hybrid");
        }

        @Test
        @DisplayName("index() stores tenantId in document metadata")
        void indexStoresTenantIdInMetadata() {
            // GIVEN
            float[] vec = {0.1f};
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(EmbeddingResult.of(vec)));
            when(vectorStore.store(anyString(), anyString(), any(float[].class), anyMap()))
                    .thenReturn(Promise.of(null));

            SemanticSearchService.IndexRequest request =
                    new SemanticSearchService.IndexRequest(
                            "doc-t1", "some content", Map.of("tag", "v1"), "tenant-store");

            // WHEN
            runPromise(() -> service.index(request));

            // THEN — metadata passed to VectorStore must contain tenantId
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> metaCaptor =
                    (ArgumentCaptor<Map<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
            verify(vectorStore).store(eq("doc-t1"), anyString(), any(float[].class), metaCaptor.capture());
            assertThat(metaCaptor.getValue())
                    .containsEntry("tenantId", "tenant-store")
                    .containsEntry("tag", "v1");
        }

        @Test
        @DisplayName("findSimilar() post-filters results by tenantId when provided")
        void findSimilarPostFiltersByTenantId() {
            // GIVEN — two docs: one from the right tenant, one from a different tenant
            float[] vec = {0.1f};
            List<VectorSearchResult> allResults = List.of(
                    new VectorSearchResult("match", "content", vec, 0.9, 0,
                            Map.of("tenantId", "tenant-a")),
                    new VectorSearchResult("other", "content", vec, 0.85, 0,
                            Map.of("tenantId", "tenant-b"))
            );
            when(vectorStore.searchById(anyString(), anyInt(), anyDouble()))
                    .thenReturn(Promise.of(allResults));

            // WHEN — search as tenant-a
            List<SemanticSearchService.SearchHit> hits =
                    runPromise(() -> service.findSimilar("source-doc", 10, 0.7, "tenant-a"));

            // THEN — only tenant-a result is returned
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).id()).isEqualTo("match");
        }

        @Test
        @DisplayName("search() with null tenantId does not inject tenantId filter")
        void searchWithNullTenantDoesNotInjectFilter() {
            // GIVEN
            float[] vec = {0.1f};
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(EmbeddingResult.of(vec)));
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap()))
                    .thenReturn(Promise.of(List.of()));

            SemanticSearchService.SemanticSearchRequest request =
                    new SemanticSearchService.SemanticSearchRequest("q", 5, 0.7, null, null);

            // WHEN
            runPromise(() -> service.search(request));

            // THEN — tenantId key must NOT appear in filter map
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> captor =
                    (ArgumentCaptor<Map<String, String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
            verify(vectorStore).search(any(float[].class), anyInt(), anyDouble(), captor.capture());
            assertThat(captor.getValue()).doesNotContainKey("tenantId");
        }
    }
}
