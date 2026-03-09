package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for NLP analysis results.
 *
 * @doc.type record
 * @doc.purpose Returns extracted entities, sentiment, and mood analysis
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record NLPResponse(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("entities") List<Entity> entities,
        @JsonProperty("sentiment") SentimentResult sentiment,
        @JsonProperty("mood") MoodResult mood,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
