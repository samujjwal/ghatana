package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for semantic search results.
 *
 * @doc.type record
 * @doc.purpose Returns ranked search results with similarity scores
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SemanticSearchResponse(
        @JsonProperty("results") List<SearchResult> results,
        @JsonProperty("totalResults") int totalResults,
        @JsonProperty("query") String query,
        @JsonProperty("processingTimeMs") long processingTimeMs
) {
}
