package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sentiment analysis result.
 *
 * @doc.type record
 * @doc.purpose Returns sentiment label and polarity scores
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SentimentResult(
        @JsonProperty("label") String label,
        @JsonProperty("score") double score,
        @JsonProperty("positive") double positive,
        @JsonProperty("negative") double negative,
        @JsonProperty("neutral") double neutral
) {
}
