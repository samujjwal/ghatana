package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for moment classification results.
 *
 * @doc.type record
 * @doc.purpose Returns classified sphere with confidence and alternatives
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ClassificationResponse(
        @JsonProperty("sphereId") String sphereId,
        @JsonProperty("sphereName") String sphereName,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("alternatives") List<SphereSuggestion> alternatives,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
