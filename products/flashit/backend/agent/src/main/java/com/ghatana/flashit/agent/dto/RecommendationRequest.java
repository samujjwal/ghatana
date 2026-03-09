package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for generating personalized recommendations.
 *
 * @doc.type record
 * @doc.purpose Carries user context for AI-powered recommendation generation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RecommendationRequest(
        @JsonProperty("userId") String userId,
        @JsonProperty("recentMoments") List<MomentData> recentMoments,
        @JsonProperty("sphereIds") List<String> sphereIds,
        @JsonProperty("strategies") List<String> strategies,
        @JsonProperty("limit") int limit,
        @JsonProperty("excludeIds") List<String> excludeIds
) {
}
