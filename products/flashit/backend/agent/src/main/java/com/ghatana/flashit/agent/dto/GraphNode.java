package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A node in the knowledge graph (topic or entity).
 *
 * @doc.type record
 * @doc.purpose Represents a single node in the user's knowledge graph
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GraphNode(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("nodeType") String nodeType,
        @JsonProperty("entityType") String entityType,
        @JsonProperty("weight") double weight,
        @JsonProperty("momentCount") int momentCount,
        @JsonProperty("relatedNodeIds") List<String> relatedNodeIds
) {
}
