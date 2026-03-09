package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for reflection results (insights, patterns, connections).
 *
 * @doc.type record
 * @doc.purpose Returns AI-generated reflection analysis of moments
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReflectionResponse(
        @JsonProperty("summary") String summary,
        @JsonProperty("insights") List<String> insights,
        @JsonProperty("patterns") List<PatternInfo> patterns,
        @JsonProperty("connections") List<ConnectionInfo> connections,
        @JsonProperty("themes") List<String> themes,
        @JsonProperty("actionItems") List<String> actionItems,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
