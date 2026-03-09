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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    void setUp() {
        MockitoAnnotations.openMocks(this);
        vectorSearchService = new VectorSearchService(vectorStore, embeddingService);
    }
    
    @Test
    @DisplayName("Should search for similar documents")
    void shouldSearchDocuments() {
        // Given
        String query = "security vulnerabilities";
        int topK = 5;
        Map<String, Object> filters = Map.of("severity", "HIGH");
        
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        List<VectorStoreResult> storeResults = List.of();
        
        when(embeddingService.embed(query))
            .thenReturn(Promise.of(embedding));
        when(vectorStore.search(embedding, topK, filters))
            .thenReturn(Promise.of(storeResults));
        
        // When
        Promise<List<SearchResult>> result = vectorSearchService.search(query, topK, filters);
        
        // Then
        verify(embeddingService).embed(query);
        verify(vectorStore).search(embedding, topK, filters);
    }
    
    @Test
    @DisplayName("Should add document to vector store")
    void shouldAddDocument() {
        // Given
        String id = "doc-123";
        String content = "Security vulnerability report";
        Map<String, Object> metadata = Map.of("type", "report", "severity", "HIGH");
        
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        
        when(embeddingService.embed(content))
            .thenReturn(Promise.of(embedding));
        when(vectorStore.upsert(id, embedding, metadata))
            .thenReturn(Promise.of((Void) null));
        
        // When
        Promise<String> result = vectorSearchService.addDocument(id, content, metadata);
        
        // Then
        verify(embeddingService).embed(content);
        verify(vectorStore).upsert(id, embedding, metadata);
    }
    
    @Test
    @DisplayName("Should delete document from vector store")
    void shouldDeleteDocument() {
        // Given
        String id = "doc-123";
        
        when(vectorStore.delete(id))
            .thenReturn(Promise.of(true));
        
        // When
        Promise<Boolean> result = vectorSearchService.deleteDocument(id);
        
        // Then
        verify(vectorStore).delete(id);
    }
    
    @Test
    @DisplayName("Should update document in vector store")
    void shouldUpdateDocument() {
        // Given
        String id = "doc-123";
        String content = "Updated security report";
        Map<String, Object> metadata = Map.of("type", "report", "severity", "CRITICAL");
        
        float[] embedding = new float[]{0.2f, 0.3f, 0.4f};
        
        when(embeddingService.embed(content))
            .thenReturn(Promise.of(embedding));
        when(vectorStore.upsert(id, embedding, metadata))
            .thenReturn(Promise.of((Void) null));
        
        // When
        Promise<String> result = vectorSearchService.updateDocument(id, content, metadata);
        
        // Then
        verify(embeddingService).embed(content);
        verify(vectorStore).upsert(id, embedding, metadata);
    }
}
