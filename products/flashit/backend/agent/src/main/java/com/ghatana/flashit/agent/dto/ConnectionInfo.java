package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Connection between moments discovered during reflection.
 *
 * @doc.type record
 * @doc.purpose Represents a discovered relationship between moments
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ConnectionInfo(
        @JsonProperty("momentId") String momentId,
        @JsonProperty("relationship") String relationship,
        @JsonProperty("confidence") double confidence
) {
}
