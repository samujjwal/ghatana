package com.ghatana.yappc.knowledge.query;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.knowledge.model.YAPPCGraphMetadata;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies semantic search over embedded knowledge graph nodes resolves repository-backed domain nodes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("KGSemanticSearchService Tests")
class KGSemanticSearchServiceTest extends EventloopTestBase {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private KGNodeRepository nodeRepository;

    private KGSemanticSearchService service;

    @BeforeEach
    void setUp() { 
        MockitoAnnotations.openMocks(this); 
        service = new KGSemanticSearchService(embeddingService, vectorStore, nodeRepository); 
    }

    @Test
    @DisplayName("findSimilarNodes returns similarity-ranked graph nodes")
    void findSimilarNodesReturnsMatches() { 
        YAPPCGraphNode node = node(); 
        float[] embedding = new float[] {0.1f, 0.2f, 0.3f};
        VectorSearchResult vectorMatch = new VectorSearchResult( 
                "node-1",
                "SERVICE: BillingService",
                embedding,
                0.93,
                1,
                Map.of("tenantId", "tenant-1")); 

        when(embeddingService.createEmbedding("billing service")).thenReturn(Promise.of(new EmbeddingResult("query", embedding, "test-model")));
        when(vectorStore.search(embedding, 5, 0.75, Map.of("tenantId", "tenant-1"))) 
                .thenReturn(Promise.of(List.of(vectorMatch))); 
        when(nodeRepository.findNodesByIds(List.of("node-1"), "tenant-1"))
                .thenReturn(Promise.of(List.of(node))); 

        List<KGSemanticSearchService.SemanticNodeMatch> matches = runPromise( 
                () -> service.findSimilarNodes("billing service", "tenant-1", 5, 0.75)); 

        assertThat(matches).hasSize(1); 
        assertThat(matches.get(0).node()).isEqualTo(node); 
        assertThat(matches.get(0).similarity()).isEqualTo(0.93); 

        verify(vectorStore).search(embedding, 5, 0.75, Map.of("tenantId", "tenant-1")); 
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
