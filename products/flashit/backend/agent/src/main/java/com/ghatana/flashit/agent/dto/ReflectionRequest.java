package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request DTO for AI reflection (insights, patterns, connections).
 *
 * <p>The specific reflection type is determined by the endpoint URL
 * (e.g., /insights, /patterns, /connections).
 *
 * @doc.type record
 * @doc.purpose Carries moment context for AI-powered reflection generation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReflectionRequest(
        @JsonProperty("moments") List<MomentData> moments,
        @JsonProperty("userId") String userId,
        @JsonProperty("sphereId") String sphereId,
        @JsonProperty("timeRange") String timeRange
) {
}
