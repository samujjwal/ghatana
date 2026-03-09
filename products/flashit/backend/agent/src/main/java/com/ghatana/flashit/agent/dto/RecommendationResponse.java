package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO with generated recommendations.
 *
 * @doc.type record
 * @doc.purpose Returns ranked recommendations with metadata
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RecommendationResponse(
        @JsonProperty("recommendations") List<RecommendationItem> recommendations,
        @JsonProperty("totalGenerated") int totalGenerated,
        @JsonProperty("strategies") List<String> strategies,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
