package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mood detection result.
 *
 * @doc.type record
 * @doc.purpose Returns detected mood with confidence and secondary moods
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MoodResult(
        @JsonProperty("primaryMood") String primaryMood,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("secondaryMoods") List<String> secondaryMoods,
        @JsonProperty("intensity") double intensity
) {
}
