package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Pattern detected across moments during reflection.
 *
 * @doc.type record
 * @doc.purpose Carries a detected behavioral or thematic pattern
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PatternInfo(
        @JsonProperty("pattern") String pattern,
        @JsonProperty("frequency") int frequency,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("examples") List<String> examples
) {
}
