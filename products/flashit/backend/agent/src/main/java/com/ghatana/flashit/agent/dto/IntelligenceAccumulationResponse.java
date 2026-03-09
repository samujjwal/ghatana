package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for intelligence accumulation results.
 *
 * @doc.type record
 * @doc.purpose Returns computed knowledge profile with topics, entities, and patterns
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntelligenceAccumulationResponse(
        @JsonProperty("userId") String userId,
        @JsonProperty("topTopics") List<TopicWeight> topTopics,
        @JsonProperty("topEntities") List<EntityWeight> topEntities,
        @JsonProperty("emotionProfile") Map<String, Double> emotionProfile,
        @JsonProperty("activityPattern") Map<String, Object> activityPattern,
        @JsonProperty("newInsights") List<String> newInsights,
        @JsonProperty("profileVersion") int profileVersion,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
