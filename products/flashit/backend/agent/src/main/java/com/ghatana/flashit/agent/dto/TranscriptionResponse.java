package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for transcription results.
 *
 * @doc.type record
 * @doc.purpose Returns transcript text with segments and confidence
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TranscriptionResponse(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("jobId") String jobId,
        @JsonProperty("status") String status,
        @JsonProperty("transcript") String transcript,
        @JsonProperty("language") String language,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("segments") List<TranscriptionSegment> segments,
        @JsonProperty("processingTimeMs") long processingTimeMs,
        @JsonProperty("model") String model
) {
}
