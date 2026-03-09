package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A suggested sphere classification with confidence.
 *
 * @doc.type record
 * @doc.purpose Represents an alternative sphere classification suggestion
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SphereSuggestion(
        @JsonProperty("sphereId") String sphereId,
        @JsonProperty("sphereName") String sphereName,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reasoning") String reasoning
) {
}
