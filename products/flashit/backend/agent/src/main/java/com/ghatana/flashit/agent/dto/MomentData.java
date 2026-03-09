package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Moment data carrier for reflection and analysis.
 *
 * @doc.type record
 * @doc.purpose Carries moment content and metadata for AI processing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MomentData(
        @JsonProperty("id") String id,
        @JsonProperty("content") String content,
        @JsonProperty("transcript") String transcript,
        @JsonProperty("capturedAt") String capturedAt,
        @JsonProperty("emotions") List<String> emotions,
        @JsonProperty("tags") List<String> tags
) {
}
