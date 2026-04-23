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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        service = new KGSemanticSearchService(embeddingService, vectorStore, nodeRepository); // GH-90000
    }

    @Test
    @DisplayName("findSimilarNodes returns similarity-ranked graph nodes")
    void findSimilarNodesReturnsMatches() { // GH-90000
        YAPPCGraphNode node = node(); // GH-90000
        float[] embedding = new float[] {0.1f, 0.2f, 0.3f};
        VectorSearchResult vectorMatch = new VectorSearchResult( // GH-90000
                "node-1",
                "SERVICE: BillingService",
                embedding,
                0.93,
                1,
                Map.of("tenantId", "tenant-1")); // GH-90000

        when(embeddingService.createEmbedding("billing service")).thenReturn(Promise.of(new EmbeddingResult("query", embedding, "test-model")));
        when(vectorStore.search(embedding, 5, 0.75, Map.of("tenantId", "tenant-1"))) // GH-90000
                .thenReturn(Promise.of(List.of(vectorMatch))); // GH-90000
        when(nodeRepository.findNodesByIds(List.of("node-1"), "tenant-1"))
                .thenReturn(Promise.of(List.of(node))); // GH-90000

        List<KGSemanticSearchService.SemanticNodeMatch> matches = runPromise( // GH-90000
                () -> service.findSimilarNodes("billing service", "tenant-1", 5, 0.75)); // GH-90000

        assertThat(matches).hasSize(1); // GH-90000
        assertThat(matches.get(0).node()).isEqualTo(node); // GH-90000
        assertThat(matches.get(0).similarity()).isEqualTo(0.93); // GH-90000

        verify(vectorStore).search(embedding, 5, 0.75, Map.of("tenantId", "tenant-1")); // GH-90000
    }

    private static YAPPCGraphNode node() { // GH-90000
        return YAPPCGraphNode.builder() // GH-90000
                .id("node-1")
                .type(YAPPCGraphNode.YAPPCNodeType.SERVICE) // GH-90000
                .name("BillingService")
                .description("Handles billing operations")
                .properties(Map.of("language", "java")) // GH-90000
                .tags(Set.of("backend", "critical")) // GH-90000
                .metadata(new YAPPCGraphMetadata( // GH-90000
                        "tenant-1",
                        "proj-1",
                        "ws-1",
                        "tester",
                        Instant.parse("2026-04-06T00:00:00Z"),
                        Instant.parse("2026-04-06T01:00:00Z"),
                        "1.0",
                        Map.of("domain", "payments"))) // GH-90000
                .build(); // GH-90000
    }
}
