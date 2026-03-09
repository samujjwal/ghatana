package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for knowledge graph operations.
 *
 * @doc.type record
 * @doc.purpose Returns extracted knowledge graph with nodes and edges
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record KnowledgeGraphResponse(
        @JsonProperty("nodes") List<GraphNode> nodes,
        @JsonProperty("edges") List<GraphEdgeInfo> edges,
        @JsonProperty("totalNodes") int totalNodes,
        @JsonProperty("totalEdges") int totalEdges,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
