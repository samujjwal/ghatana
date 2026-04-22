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
@DisplayName("SemanticSearchService Tests [GH-90000]")
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
    @DisplayName("Semantic Search [GH-90000]")
    class SemanticSearchTests {

        @Test
        @DisplayName("should search with embedding generation [GH-90000]")
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
                            "test query", 10, 0.7, null
                    );

            // WHEN
            SemanticSearchService.SemanticSearchResult result = runPromise( // GH-90000
                    () -> service.search(request) // GH-90000
            );

            // THEN
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.hits()).hasSize(1); // GH-90000
            assertThat(result.hits().get(0).id()).isEqualTo("doc-1 [GH-90000]");
            assertThat(result.hits().get(0).score()).isEqualTo(0.95); // GH-90000
            verify(embeddingService).createEmbedding("test query [GH-90000]");
        }

        @Test
        @DisplayName("should return empty results on search failure [GH-90000]")
        void shouldReturnEmptyOnFailure() { // GH-90000
            // GIVEN
            when(embeddingService.createEmbedding(anyString())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("Embedding error [GH-90000]")));

            SemanticSearchService.SemanticSearchRequest request =
                    SemanticSearchService.SemanticSearchRequest.of("failing query [GH-90000]");

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
    @DisplayName("Hybrid Search [GH-90000]")
    class HybridSearchTests {

        @Test
        @DisplayName("should combine semantic and keyword search [GH-90000]")
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
                            List.of("keyword [GH-90000]"), 0.5
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
    @DisplayName("Indexing Operations [GH-90000]")
    class IndexingTests {

        @Test
        @DisplayName("should index document with embeddings [GH-90000]")
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
                            "doc-1", "Test document content", Map.of("source", "test.md") // GH-90000
                    );

            // WHEN
            SemanticSearchService.IndexResult result = runPromise( // GH-90000
                    () -> service.index(request) // GH-90000
            );

            // THEN
            assertThat(result.success()).isTrue(); // GH-90000
            assertThat(result.id()).isEqualTo("doc-1 [GH-90000]");
            assertThat(result.vectorDimension()).isEqualTo(3); // GH-90000
            verify(embeddingService).createEmbedding("Test document content [GH-90000]");
            verify(vectorStore).store(eq("doc-1 [GH-90000]"), eq("Test document content [GH-90000]"), any(float[].class), anyMap());
        }

        @Test
        @DisplayName("should batch index multiple documents [GH-90000]")
        void shouldBatchIndexMultipleDocuments() { // GH-90000
            // GIVEN
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
            EmbeddingResult embeddingResult = EmbeddingResult.of(embedding); // GH-90000
            when(embeddingService.createEmbeddings(anyList())) // GH-90000
                    .thenReturn(Promise.of(List.of(embeddingResult, embeddingResult, embeddingResult))); // GH-90000
            when(vectorStore.store(anyString(), anyString(), any(float[].class), anyMap())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            List<SemanticSearchService.IndexRequest> requests = List.of( // GH-90000
                    new SemanticSearchService.IndexRequest("doc-1", "Content 1", Map.of()), // GH-90000
                    new SemanticSearchService.IndexRequest("doc-2", "Content 2", Map.of()), // GH-90000
                    new SemanticSearchService.IndexRequest("doc-3", "Content 3", Map.of()) // GH-90000
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
        @DisplayName("should delete document from index [GH-90000]")
        void shouldDeleteDocumentFromIndex() { // GH-90000
            // GIVEN
            when(vectorStore.delete(anyString())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            // WHEN
            boolean result = runPromise( // GH-90000
                    () -> service.delete("doc-1 [GH-90000]")
            );

            // THEN
            assertThat(result).isTrue(); // GH-90000
            verify(vectorStore).delete("doc-1 [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Find Similar [GH-90000]")
    class FindSimilarTests {

        @Test
        @DisplayName("should find similar documents excluding self [GH-90000]")
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
                    () -> service.findSimilar("ref-doc", 10, 0.7) // GH-90000
            );

            // THEN
            assertThat(result).hasSize(1); // Excludes the reference document // GH-90000
            assertThat(result.get(0).id()).isEqualTo("similar-1 [GH-90000]");
        }
    }
}
