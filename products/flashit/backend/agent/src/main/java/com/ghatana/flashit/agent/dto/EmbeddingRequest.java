package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for text embedding generation.
 *
 * @doc.type record
 * @doc.purpose Carries text content for embedding vector generation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EmbeddingRequest(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("text") String text,
        @JsonProperty("userId") String userId,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("store") boolean store
) {
}
