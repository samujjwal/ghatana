package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for embedding generation results.
 *
 * @doc.type record
 * @doc.purpose Returns embedding vector with metadata
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EmbeddingResponse(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("embedding") double[] embedding,
        @JsonProperty("dimensions") int dimensions,
        @JsonProperty("tokenCount") int tokenCount,
        @JsonProperty("model") String model,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("stored") boolean stored
) {
}
