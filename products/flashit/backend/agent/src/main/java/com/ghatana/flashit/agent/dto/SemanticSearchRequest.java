package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for semantic similarity search.
 *
 * @doc.type record
 * @doc.purpose Carries search query for vector-based moment retrieval
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SemanticSearchRequest(
        @JsonProperty("query") String query,
        @JsonProperty("userId") String userId,
        @JsonProperty("sphereIds") List<String> sphereIds,
        @JsonProperty("limit") int limit,
        @JsonProperty("similarityThreshold") double similarityThreshold
) {
}
