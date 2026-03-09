package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An edge in the knowledge graph connecting two nodes.
 *
 * @doc.type record
 * @doc.purpose Represents a relationship between two knowledge graph nodes
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GraphEdgeInfo(
        @JsonProperty("sourceId") String sourceId,
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("targetId") String targetId,
        @JsonProperty("targetType") String targetType,
        @JsonProperty("edgeType") String edgeType,
        @JsonProperty("weight") double weight,
        @JsonProperty("occurrences") int occurrences
) {
}
