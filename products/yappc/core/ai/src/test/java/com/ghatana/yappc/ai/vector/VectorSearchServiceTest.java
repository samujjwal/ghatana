package com.ghatana.yappc.ai.vector;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorStoreClient;
import com.ghatana.ai.vectorstore.VectorStoreClient.VectorStoreResult;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Tests for VectorSearchService.
 */
@DisplayName("VectorSearchService Tests")
/**
 * @doc.type class
 * @doc.purpose Handles vector search service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class VectorSearchServiceTest extends EventloopTestBase {

    @Mock
    private VectorStoreClient vectorStore;

    @Mock
    private EmbeddingService embeddingService;

    private VectorSearchService vectorSearchService;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        vectorSearchService = new VectorSearchService(vectorStore, embeddingService); // GH-90000
    }

    @Test
    @DisplayName("Should search for similar documents")
    void shouldSearchDocuments() { // GH-90000
        // Given
        String query = "security vulnerabilities";
        int topK = 5;
        Map<String, Object> filters = Map.of("severity", "HIGH"); // GH-90000

        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        List<VectorStoreResult> storeResults = List.of(); // GH-90000

        when(embeddingService.embed(query)) // GH-90000
            .thenReturn(Promise.of(embedding)); // GH-90000
        when(vectorStore.search(embedding, topK, filters)) // GH-90000
            .thenReturn(Promise.of(storeResults)); // GH-90000

        // When
        Promise<List<SearchResult>> result = vectorSearchService.search(query, topK, filters); // GH-90000

        // Then
        verify(embeddingService).embed(query); // GH-90000
        verify(vectorStore).search(embedding, topK, filters); // GH-90000
    }

    @Test
    @DisplayName("Should add document to vector store")
    void shouldAddDocument() { // GH-90000
        // Given
        String id = "doc-123";
        String content = "Security vulnerability report";
        Map<String, Object> metadata = Map.of("type", "report", "severity", "HIGH"); // GH-90000

        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};

        when(embeddingService.embed(content)) // GH-90000
            .thenReturn(Promise.of(embedding)); // GH-90000
        when(vectorStore.upsert(id, embedding, metadata)) // GH-90000
            .thenReturn(Promise.of((Void) null)); // GH-90000

        // When
        Promise<String> result = vectorSearchService.addDocument(id, content, metadata); // GH-90000

        // Then
        verify(embeddingService).embed(content); // GH-90000
        verify(vectorStore).upsert(id, embedding, metadata); // GH-90000
    }

    @Test
    @DisplayName("Should delete document from vector store")
    void shouldDeleteDocument() { // GH-90000
        // Given
        String id = "doc-123";

        when(vectorStore.delete(id)) // GH-90000
            .thenReturn(Promise.of(true)); // GH-90000

        // When
        Promise<Boolean> result = vectorSearchService.deleteDocument(id); // GH-90000

        // Then
        verify(vectorStore).delete(id); // GH-90000
    }

    @Test
    @DisplayName("Should update document in vector store")
    void shouldUpdateDocument() { // GH-90000
        // Given
        String id = "doc-123";
        String content = "Updated security report";
        Map<String, Object> metadata = Map.of("type", "report", "severity", "CRITICAL"); // GH-90000

        float[] embedding = new float[]{0.2f, 0.3f, 0.4f};

        when(embeddingService.embed(content)) // GH-90000
            .thenReturn(Promise.of(embedding)); // GH-90000
        when(vectorStore.upsert(id, embedding, metadata)) // GH-90000
            .thenReturn(Promise.of((Void) null)); // GH-90000

        // When
        Promise<String> result = vectorSearchService.updateDocument(id, content, metadata); // GH-90000

        // Then
        verify(embeddingService).embed(content); // GH-90000
        verify(vectorStore).upsert(id, embedding, metadata); // GH-90000
    }
}
