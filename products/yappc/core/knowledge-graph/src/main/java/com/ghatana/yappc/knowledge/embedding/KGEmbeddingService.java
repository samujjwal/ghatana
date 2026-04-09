package com.ghatana.yappc.knowledge.embedding;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.yappc.knowledge.model.YAPPCGraphNode;
import io.activej.promise.Promise;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @doc.type class
 * @doc.purpose Generates and stores semantic embeddings for knowledge graph nodes
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KGEmbeddingService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public KGEmbeddingService(EmbeddingService embeddingService, VectorStore vectorStore) {
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
    }

    public Promise<Void> indexNode(YAPPCGraphNode node) {
        Objects.requireNonNull(node, "node must not be null");

        String content = buildContent(node);
        Map<String, String> metadata = buildMetadata(node);

        return embeddingService.createEmbedding(content)
                .then(embedding -> vectorStore.store(node.id(), content, embedding.getVector(), metadata));
    }

    public Promise<Void> deleteNode(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return vectorStore.delete(nodeId);
    }

    private String buildContent(YAPPCGraphNode node) {
        String tags = node.tags().stream().sorted().collect(Collectors.joining(", "));
        String properties = node.properties().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + entry.getValue())
                .collect(Collectors.joining(", "));

        return String.join("\n",
                node.type().name() + ": " + node.name(),
                node.description() == null ? "" : node.description(),
                tags.isBlank() ? "" : "Tags: " + tags,
                properties.isBlank() ? "" : "Properties: " + properties).trim();
    }

    private Map<String, String> buildMetadata(YAPPCGraphNode node) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", node.metadata().tenantId());
        if (node.metadata().projectId() != null) {
            metadata.put("projectId", node.metadata().projectId());
        }
        if (node.metadata().workspaceId() != null) {
            metadata.put("workspaceId", node.metadata().workspaceId());
        }
        metadata.put("nodeType", node.type().name());
        return metadata;
    }
}
