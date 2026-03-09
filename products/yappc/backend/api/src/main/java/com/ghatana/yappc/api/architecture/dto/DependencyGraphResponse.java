/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.architecture.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for dependency graph.
 *
 * @doc.type record
 * @doc.purpose Dependency graph response
 * @doc.layer api
 * @doc.pattern DTO
 */
public record DependencyGraphResponse(
    @JsonProperty("rootEntityId") String rootEntityId,
    @JsonProperty("nodes") List<GraphNode> nodes,
    @JsonProperty("edges") List<GraphEdge> edges,
    @JsonProperty("clusters") List<GraphCluster> clusters,
    @JsonProperty("statistics") GraphStatistics statistics) {
  /** Graph node. */
  public record GraphNode(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("type") String type,
      @JsonProperty("status") String status,
      @JsonProperty("metadata") Map<String, Object> metadata) {}

  /** Graph edge. */
  public record GraphEdge(
      @JsonProperty("id") String id,
      @JsonProperty("source") String source,
      @JsonProperty("target") String target,
      @JsonProperty("type") String type,
      @JsonProperty("weight") double weight,
      @JsonProperty("metadata") Map<String, Object> metadata) {}

  /** Graph cluster. */
  public record GraphCluster(
      @JsonProperty("id") String id,
      @JsonProperty("name") String name,
      @JsonProperty("nodeIds") List<String> nodeIds,
      @JsonProperty("type") String type) {}

  /** Graph statistics. */
  public record GraphStatistics(
      @JsonProperty("totalNodes") int totalNodes,
      @JsonProperty("totalEdges") int totalEdges,
      @JsonProperty("maxDepth") int maxDepth,
      @JsonProperty("averageConnections") double averageConnections,
      @JsonProperty("mostConnectedNode") String mostConnectedNode,
      @JsonProperty("orphanNodes") int orphanNodes) {}
}
