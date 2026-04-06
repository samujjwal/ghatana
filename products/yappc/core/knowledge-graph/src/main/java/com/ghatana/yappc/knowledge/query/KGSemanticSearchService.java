package com.ghatana.yappc.knowledge.query;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import com.ghatana.yappc.knowledge.persistence.KGNodeRepository;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Runs semantic search over embedded knowledge graph nodes
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KGSemanticSearchService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KGNodeRepository nodeRepository;

    public KGSemanticSearchService(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            KGNodeRepository nodeRepository) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.nodeRepository = Objects.requireNonNull(nodeRepository, "nodeRepository must not be null");
    }

    public Promise<List<SemanticNodeMatch>> findSimilarNodes(
            String query,
            String tenantId,
            int limit,
            double minSimilarity) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Map<String, String> filter = Map.of("tenantId", tenantId);

        return embeddingService.createEmbedding(query)
                .then(embedding -> vectorStore.search(embedding.getVector(), limit, minSimilarity, filter))
                .then(results -> mapMatches(results, tenantId));
    }

    private Promise<List<SemanticNodeMatch>> mapMatches(List<VectorSearchResult> results, String tenantId) {
        List<String> nodeIds = results.stream().map(VectorSearchResult::getId).toList();

        return nodeRepository.findNodesByIds(nodeIds, tenantId)
                .map(nodes -> {
                    Map<String, YAPPCGraphNode> nodesById = nodes.stream()
                            .collect(Collectors.toMap(YAPPCGraphNode::id, node -> node, (left, right) -> left, LinkedHashMap::new));

                    return results.stream()
                            .map(result -> {
                                YAPPCGraphNode node = nodesById.get(result.getId());
                                if (node == null) {
                                    return null;
                                }
                                return new SemanticNodeMatch(node, result.getSimilarity(), result.getMetadata());
                            })
                            .filter(Objects::nonNull)
                            .toList();
                });
    }

    public record SemanticNodeMatch(
            YAPPCGraphNode node,
            double similarity,
            Map<String, String> metadata) {
    }
}