package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for moment classification into spheres.
 *
 * @doc.type record
 * @doc.purpose Carries moment content and context for AI sphere classification
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ClassificationRequest(
        @JsonProperty("content") String content,
        @JsonProperty("transcript") String transcript,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("emotions") List<String> emotions,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("userIntent") String userIntent,
        @JsonProperty("availableSpheres") List<SphereInfo> availableSpheres,
        @JsonProperty("userId") String userId
) {
}
