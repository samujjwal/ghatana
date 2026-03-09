package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A time-aligned segment of a transcription.
 *
 * @doc.type record
 * @doc.purpose Represents a timed segment within a full transcript
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TranscriptionSegment(
        @JsonProperty("start") double start,
        @JsonProperty("end") double end,
        @JsonProperty("text") String text,
        @JsonProperty("confidence") double confidence
) {
}
