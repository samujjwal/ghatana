package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for audio/video transcription.
 *
 * @doc.type record
 * @doc.purpose Carries audio reference for Whisper transcription
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TranscriptionRequest(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("audioUrl") String audioUrl,
        @JsonProperty("audioData") String audioData,
        @JsonProperty("language") String language,
        @JsonProperty("userId") String userId
) {
}
