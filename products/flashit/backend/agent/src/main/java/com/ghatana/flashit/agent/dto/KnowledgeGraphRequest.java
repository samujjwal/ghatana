package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for knowledge graph operations (extract, query, expand).
 *
 * <p>The specific operation is determined by the endpoint URL
 * (e.g., /extract, /query, /expand).
 *
 * @doc.type record
 * @doc.purpose Carries moment data for knowledge graph construction and querying
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record KnowledgeGraphRequest(
        @JsonProperty("userId") String userId,
        @JsonProperty("moments") List<MomentData> moments,
        @JsonProperty("queryNode") String queryNode,
        @JsonProperty("depth") int depth,
        @JsonProperty("limit") int limit
) {
}
