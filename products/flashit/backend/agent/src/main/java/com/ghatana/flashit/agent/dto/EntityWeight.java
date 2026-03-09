package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An entity with its computed weight/frequency.
 *
 * @doc.type record
 * @doc.purpose Represents a ranked entity in the user's knowledge profile
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EntityWeight(
        @JsonProperty("entity") String entity,
        @JsonProperty("entityType") String entityType,
        @JsonProperty("weight") double weight,
        @JsonProperty("mentionCount") int mentionCount
) {
}
