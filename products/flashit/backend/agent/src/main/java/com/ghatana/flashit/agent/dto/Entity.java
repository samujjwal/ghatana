package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Named entity extracted from text.
 *
 * @doc.type record
 * @doc.purpose Represents a named entity (person, place, org, etc.)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record Entity(
        @JsonProperty("text") String text,
        @JsonProperty("type") String type,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("startOffset") int startOffset,
        @JsonProperty("endOffset") int endOffset
) {
}
