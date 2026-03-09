package com.ghatana.products.yappc.domain.vector;

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
    void setUp() {
        vectorStore = mock(VectorStore.class);
        embeddingService = mock(EmbeddingService.class);
        service = new SemanticSearchService(vectorStore, embeddingService);
    }

    @Nested
    @DisplayName("Semantic Search")
    class SemanticSearchTests {

        @Test
        @DisplayName("should search with embedding generation")
        void shouldSearchWithEmbeddingGeneration() {
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding);
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(embeddingResult));

            VectorSearchResult searchResult = new VectorSearchResult(
                    "doc-1", "Document content", embedding, 0.95, 0, Map.of("source", "test.md")
            );
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap()))
                    .thenReturn(Promise.of(List.of(searchResult)));

            SemanticSearchService.SemanticSearchRequest request =
                    new SemanticSearchService.SemanticSearchRequest(
                            "test query", 10, 0.7, null
                    );

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise(
                    () -> service.search(request)
            );

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.hits()).hasSize(1);
            assertThat(result.hits().get(0).id()).isEqualTo("doc-1");
            assertThat(result.hits().get(0).score()).isEqualTo(0.95);
            verify(embeddingService).createEmbedding("test query");
        }

        @Test
        @DisplayName("should return empty results on search failure")
        void shouldReturnEmptyOnFailure() {
            // GIVEN
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.ofException(new RuntimeException("Embedding error")));

            SemanticSearchService.SemanticSearchRequest request =
                    SemanticSearchService.SemanticSearchRequest.of("failing query");

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise(
                    () -> service.search(request)
            );

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isNotNull();
            assertThat(result.hits()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Hybrid Search")
    class HybridSearchTests {

        @Test
        @DisplayName("should combine semantic and keyword search")
        void shouldCombineKeywordAndSemanticSearch() {
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding);
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(embeddingResult));

            VectorSearchResult semanticResult = new VectorSearchResult(
                    "doc-1", "Semantic match content with keyword", embedding, 0.8, 0, Map.of()
            );
            when(vectorStore.search(any(float[].class), anyInt(), anyDouble(), anyMap()))
                    .thenReturn(Promise.of(List.of(semanticResult)));

            SemanticSearchService.HybridSearchRequest request =
                    new SemanticSearchService.HybridSearchRequest(
                            "test query", 10, 0.7, null,
                            List.of("keyword"), 0.5
                    );

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise(
                    () -> service.hybridSearch(request)
            );

            // THEN
            assertThat(result.hits()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Indexing Operations")
    class IndexingTests {

        @Test
        @DisplayName("should index document with embeddings")
        void shouldIndexDocumentWithEmbeddings() {
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding);
            when(embeddingService.createEmbedding(anyString()))
                    .thenReturn(Promise.of(embeddingResult));
            when(vectorStore.store(anyString(), anyString(), any(float[].class), anyMap()))
                    .thenReturn(Promise.of(null));

            SemanticSearchService.IndexRequest request =
                    new SemanticSearchService.IndexRequest(
                            "doc-1", "Test document content", Map.of("source", "test.md")
                    );

            // WHEN
            SemanticSearchService.IndexResult result = runPromise(
                    () -> service.index(request)
            );

            // THEN
            assertThat(result.success()).isTrue();
            assertThat(result.id()).isEqualTo("doc-1");
            assertThat(result.vectorDimension()).isEqualTo(3);
            verify(embeddingService).createEmbedding("Test document content");
            verify(vectorStore).store(eq("doc-1"), eq("Test document content"), any(float[].class), anyMap());
        }

        @Test
        @DisplayName("should batch index multiple documents")
        void shouldBatchIndexMultipleDocuments() {
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding);
            when(embeddingService.createEmbeddings(anyList()))
                    .thenReturn(Promise.of(List.of(embeddingResult, embeddingResult, embeddingResult)));
            when(vectorStore.store(anyString(), anyString(), any(float[].class), anyMap()))
                    .thenReturn(Promise.of(null));

            List<SemanticSearchService.IndexRequest> requests = List.of(
                    new SemanticSearchService.IndexRequest("doc-1", "Content 1", Map.of()),
                    new SemanticSearchService.IndexRequest("doc-2", "Content 2", Map.of()),
                    new SemanticSearchService.IndexRequest("doc-3", "Content 3", Map.of())
            );

            // WHEN
            List<SemanticSearchService.IndexResult> results = runPromise(
                    () -> service.batchIndex(requests)
            );

            // THEN
            assertThat(results).hasSize(3);
            assertThat(results).allMatch(SemanticSearchService.IndexResult::success);
        }

        @Test
        @DisplayName("should delete document from index")
        void shouldDeleteDocumentFromIndex() {
            // GIVEN
            when(vectorStore.delete(anyString()))
                    .thenReturn(Promise.of(null));

            // WHEN
            boolean result = runPromise(
                    () -> service.delete("doc-1")
            );

            // THEN
            assertThat(result).isTrue();
            verify(vectorStore).delete("doc-1");
        }
    }

    @Nested
    @DisplayName("Find Similar")
    class FindSimilarTests {

        @Test
        @DisplayName("should find similar documents excluding self")
        void shouldFindSimilarDocuments() {
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            List<VectorSearchResult> similarDocs = List.of(
                    new VectorSearchResult("similar-1", "Similar content", embedding, 0.9, 0, Map.of()),
                    new VectorSearchResult("ref-doc", "Reference content", embedding, 1.0, 0, Map.of())
            );
            when(vectorStore.searchById(anyString(), anyInt(), anyDouble()))
                    .thenReturn(Promise.of(similarDocs));

            // WHEN
            List<SemanticSearchService.SearchHit> result = runPromise(
                    () -> service.findSimilar("ref-doc", 10, 0.7)
            );

            // THEN
            assertThat(result).hasSize(1); // Excludes the reference document
            assertThat(result.get(0).id()).isEqualTo("similar-1");
        }
    }
}
