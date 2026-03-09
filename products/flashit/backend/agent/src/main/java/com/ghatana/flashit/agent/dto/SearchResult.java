package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single search result with similarity score.
 *
 * @doc.type record
 * @doc.purpose Represents a moment matched by semantic search
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SearchResult(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("content") String content,
        @JsonProperty("sphereId") String sphereId,
        @JsonProperty("similarity") double similarity
) {
}
