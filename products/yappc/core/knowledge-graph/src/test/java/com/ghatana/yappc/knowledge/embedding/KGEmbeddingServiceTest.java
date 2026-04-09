package com.ghatana.yappc.knowledge.embedding;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies knowledge graph node embeddings are generated and stored through the shared vector stack
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KGEmbeddingService Tests")
class KGEmbeddingServiceTest extends EventloopTestBase {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    private KGEmbeddingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new KGEmbeddingService(embeddingService, vectorStore);
    }

    @Test
    @DisplayName("indexNode stores a vector with tenant-scoped metadata")
    void indexNodeStoresVector() {
        YAPPCGraphNode node = node();
        float[] embedding = new float[] {0.1f, 0.2f, 0.3f};

        when(embeddingService.createEmbedding(any())).thenReturn(Promise.of(new EmbeddingResult("text", embedding, "test-model")));
        when(vectorStore.store(eq(node.id()), any(), eq(embedding), eq(Map.of(
                "tenantId", "tenant-1",
                "projectId", "proj-1",
                "workspaceId", "ws-1",
                "nodeType", "SERVICE")))).thenReturn(Promise.of((Void) null));

        runPromise(() -> service.indexNode(node));

        verify(embeddingService).createEmbedding(any());
        verify(vectorStore).store(eq(node.id()), any(), eq(embedding), eq(Map.of(
                "tenantId", "tenant-1",
                "projectId", "proj-1",
                "workspaceId", "ws-1",
                "nodeType", "SERVICE")));
    }

    @Test
    @DisplayName("deleteNode removes vectors by node id")
    void deleteNodeRemovesVector() {
        when(vectorStore.delete("node-1")).thenReturn(Promise.of((Void) null));

        runPromise(() -> service.deleteNode("node-1"));

        verify(vectorStore).delete("node-1");
    }

    private static YAPPCGraphNode node() {
        return YAPPCGraphNode.builder()
                .id("node-1")
                .type(YAPPCGraphNode.YAPPCNodeType.SERVICE)
                .name("BillingService")
                .description("Handles billing operations")
                .properties(Map.of("language", "java"))
                .tags(Set.of("backend", "critical"))
                .metadata(new YAPPCGraphMetadata(
                        "tenant-1",
                        "proj-1",
                        "ws-1",
                        "tester",
                        Instant.parse("2026-04-06T00:00:00Z"),
                        Instant.parse("2026-04-06T01:00:00Z"),
                        "1.0",
                        Map.of("domain", "payments")))
                .build();
    }
}
